package org.nooklin.music.airplay.raop.auth

import android.util.Log
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/**
 * HAP pair-VERIFY - how a device with stored credentials reconnects without a PIN.
 *
 * [HapPinPairing] establishes long-term keys once, using the code on screen. Every
 * subsequent connection runs THIS instead: an X25519 exchange, with both sides proving
 * possession of their Ed25519 long-term keys.
 *
 * Without this, the streaming path fell back to TRANSIENT pairing, whose
 * /pair-pin-start makes the Apple TV display a brand new code - which is exactly the
 * "it asks for a code again and never plays" symptom.
 *
 * Flow (pyatv AirPlayHapPairVerifyProcedure + SRPAuthHandler.verify1/verify2):
 *   M1  POST /pair-verify {SeqNo=1, PublicKey=ourEphemeralX25519}
 *   M2  <- {PublicKey=deviceEphemeral, EncryptedData}
 *       derive shared secret, decrypt, verify the device's signature against its
 *       stored long-term public key
 *   M3  POST /pair-verify {SeqNo=3, EncryptedData=ourIdentifier+signature}
 *   M4  <- accepted
 * Then the control channel is encrypted with keys derived from the shared secret.
 */
class HapPairVerify(
    private val http: AirPlayHttpClient,
    private val credentials: HapPinPairing.Credentials
) {

    companion object {
        private const val TAG = "AirSyncVerify"

        private const val VERIFY_SALT = "Pair-Verify-Encrypt-Salt"
        private const val VERIFY_INFO = "Pair-Verify-Encrypt-Info"
        private const val NONCE_M2 = "PV-Msg02"
        private const val NONCE_M3 = "PV-Msg03"

        private const val CONTROL_SALT = "Control-Salt"
        private const val CONTROL_WRITE_INFO = "Control-Write-Encryption-Key"
        private const val CONTROL_READ_INFO = "Control-Read-Encryption-Key"

        private val HEADERS = mapOf(
            "User-Agent" to "AirPlay/320.20",
            "Connection" to "keep-alive",
            // 3 = regular HAP (4 is transient).
            "X-Apple-HKP" to "3",
            "Content-Type" to "application/octet-stream"
        )
    }

    class VerifyException(message: String) : Exception(message)

    /** The X25519 shared secret, kept so control-channel keys can be derived. */
    private var shared: ByteArray? = null

    /**
     * Runs verification and enables encryption on [http].
     *
     * @return the shared secret, for deriving further keys (event channel, audio)
     */
    fun verify(): ByteArray {
        // Ephemeral X25519 key pair for this session only.
        val ephemeralPrivate = X25519PrivateKeyParameters(SecureRandom())
        val ephemeralPublic = ephemeralPrivate.generatePublicKey().encoded


        // ---- M1 ----
        val m1 = Tlv8.write(
            listOf(
                Tlv8.Tag.SEQ_NO to byteArrayOf(0x01),
                Tlv8.Tag.PUBLIC_KEY to ephemeralPublic
            )
        )
        val m2Resp = http.post("/pair-verify", m1, HEADERS)
        if (m2Resp.statusCode !in 200..299) {
            throw VerifyException("pair-verify M1 returned HTTP ${m2Resp.statusCode}")
        }

        val m2 = Tlv8.read(m2Resp.body)
        m2[Tlv8.Tag.ERROR]?.let {
            throw VerifyException(
                "Device rejected our credentials (HAP error ${it.firstOrNull()?.toInt() ?: -1})"
            )
        }
        val devicePublic = m2[Tlv8.Tag.PUBLIC_KEY]
            ?: throw VerifyException("M2 missing PublicKey")
        val encrypted = m2[Tlv8.Tag.ENCRYPTED_DATA]
            ?: throw VerifyException("M2 missing EncryptedData")

        // ---- shared secret ----
        val agreement = X25519Agreement()
        agreement.init(ephemeralPrivate)
        val secret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(devicePublic, 0), secret, 0)
        shared = secret

        val sessionKey = Hkdf.deriveKey(VERIFY_SALT, VERIFY_INFO, secret)

        // ---- verify the device's signature ----
        val decrypted = try {
            HapChaCha20Poly1305.decrypt(sessionKey, NONCE_M2, encrypted)
        } catch (e: Exception) {
            throw VerifyException(
                "Could not decrypt M2 - stored credentials are probably stale: ${e.message}"
            )
        }
        val inner = Tlv8.read(decrypted)
        val deviceId = inner[Tlv8.Tag.IDENTIFIER]
            ?: throw VerifyException("M2 inner TLV missing Identifier")
        val deviceSignature = inner[Tlv8.Tag.SIGNATURE]
            ?: throw VerifyException("M2 inner TLV missing Signature")

        val expectedId = credentials.deviceIdentifier.toByteArray(Charsets.UTF_8)
        if (!deviceId.contentEquals(expectedId)) {
            throw VerifyException(
                "Device identity changed - it was probably reset. Forget and re-pair."
            )
        }

        // The device signs: itsEphemeralPublic || itsIdentifier || ourEphemeralPublic
        val signedInfo = devicePublic + deviceId + ephemeralPublic
        val deviceLtpk = Ed25519PublicKeyParameters(credentials.devicePublicKey, 0)
        val verifier = Ed25519Signer().apply {
            init(false, deviceLtpk)
            update(signedInfo, 0, signedInfo.size)
        }
        if (!verifier.verifySignature(deviceSignature)) {
            throw VerifyException(
                "Device signature did not verify - stored credentials are invalid"
            )
        }

        // ---- M3: prove our own identity ----
        val clientId = credentials.clientIdentifier.toByteArray(Charsets.UTF_8)
        // We sign: ourEphemeralPublic || ourIdentifier || itsEphemeralPublic
        val ourInfo = ephemeralPublic + clientId + devicePublic
        val signer = Ed25519Signer().apply {
            init(true, Ed25519PrivateKeyParameters(credentials.clientPrivateKey, 0))
            update(ourInfo, 0, ourInfo.size)
        }
        val ourSignature = signer.generateSignature()

        val subTlv = Tlv8.write(
            listOf(
                Tlv8.Tag.IDENTIFIER to clientId,
                Tlv8.Tag.SIGNATURE to ourSignature
            )
        )
        val m3 = Tlv8.write(
            listOf(
                Tlv8.Tag.SEQ_NO to byteArrayOf(0x03),
                Tlv8.Tag.ENCRYPTED_DATA to
                    HapChaCha20Poly1305.encrypt(sessionKey, NONCE_M3, subTlv)
            )
        )
        val m4 = http.post("/pair-verify", m3, HEADERS)
        if (m4.statusCode !in 200..299) {
            throw VerifyException("pair-verify M3 returned HTTP ${m4.statusCode}")
        }
        Tlv8.read(m4.body)[Tlv8.Tag.ERROR]?.let {
            throw VerifyException(
                "Device rejected our proof (HAP error ${it.firstOrNull()?.toInt() ?: -1})"
            )
        }

        // ---- encrypt the control channel, exactly as transient pairing does ----
        val outKey = Hkdf.deriveKey(CONTROL_SALT, CONTROL_WRITE_INFO, secret)
        val inKey = Hkdf.deriveKey(CONTROL_SALT, CONTROL_READ_INFO, secret)
        http.enableEncryption(outKey, inKey)

        return secret
    }
}
