package org.nooklin.music.airplay.raop.auth

import android.util.Log
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.math.BigInteger
import java.security.SecureRandom
import java.util.UUID

/**
 * HAP pair-setup with an on-screen PIN - the flow an Apple TV requires.
 *
 * The AirPort Expresses accept TRANSIENT pairing (a fixed PIN of 3939, no user
 * interaction, see [TransientPairVerify]). An Apple TV refuses that with HTTP 470 and
 * instead displays a 4-digit code, which the user must type in. That is this flow.
 *
 * Sequence (ported from pyatv's AirPlayHapPairSetupProcedure + SRPAuthHandler):
 *   M1  POST /pair-pin-start        -> Apple TV displays the PIN
 *       POST /pair-setup  {Method=0, SeqNo=1}
 *   M2  <- {Salt, PublicKey}
 *   M3  POST /pair-setup  {SeqNo=3, PublicKey, Proof}     SRP with the typed PIN
 *   M4  <- (accepted)
 *   M5  POST /pair-setup  {SeqNo=5, EncryptedData}        our identity, signed
 *   M6  <- {EncryptedData}                                the device's identity
 *
 * The result is a long-term credential pair. Unlike transient pairing, this is meant
 * to be STORED and reused: subsequent connections run pair-verify with these keys
 * instead of showing the PIN again.
 *
 * NOTE ON CRYPTO: Ed25519 comes from BouncyCastle. Android's own providers cannot
 * generate an exportable Ed25519 key - see the dependency comment in build.gradle.kts.
 */
class HapPinPairing(private val http: AirPlayHttpClient, private val senderName: String = "Android") {

    companion object {
        private const val TAG = "AirSyncPin"

        // HKDF labels, from pyatv hap_srp.py step3/step4.
        private const val SIGN_SALT = "Pair-Setup-Controller-Sign-Salt"
        private const val SIGN_INFO = "Pair-Setup-Controller-Sign-Info"
        private const val ENCRYPT_SALT = "Pair-Setup-Encrypt-Salt"
        private const val ENCRYPT_INFO = "Pair-Setup-Encrypt-Info"

        private const val NONCE_M5 = "PS-Msg05"
        private const val NONCE_M6 = "PS-Msg06"
    }

    // Instance, not companion: the name varies per phone. X-Apple-Client-Name is
    // what a receiver shows on its own pairing confirmation UI in place of a blank
    // sender field (see DeviceIdentity for where this value comes from).
    private val headers = mapOf(
        "User-Agent" to "AirPlay/320.20",
        "Connection" to "keep-alive",
        // 3 = regular HAP pairing (4 is the transient variant).
        "X-Apple-HKP" to "3",
        "X-Apple-Client-Name" to senderName,
        "Content-Type" to "application/octet-stream"
    )

    class PinPairingException(message: String) : Exception(message)

    /** Long-term credentials, worth persisting so the PIN is only needed once. */
    data class Credentials(
        val devicePublicKey: ByteArray,   // the Apple TV's Ed25519 public key (ltpk)
        val clientPrivateKey: ByteArray,  // our Ed25519 seed (ltsk)
        val clientPublicKey: ByteArray,
        val deviceIdentifier: String,
        val clientIdentifier: String
    )

    // Our long-term identity for this pairing.
    private val clientIdentifier: String = UUID.randomUUID().toString().uppercase()
    private val signingSeed = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val signingKey = Ed25519PrivateKeyParameters(signingSeed, 0)
    private val verifyingKey: Ed25519PublicKeyParameters = signingKey.generatePublicKey()

    private var salt: ByteArray? = null
    private var devicePublicKey: ByteArray? = null

    /**
     * Starts pairing. This is what makes the Apple TV display its code - after this
     * returns, ask the user for the PIN and call [finish].
     */
    fun start() {
        http.post("/pair-pin-start", ByteArray(0), headers)

        val m1 = Tlv8.write(
            listOf(
                Tlv8.Tag.METHOD to byteArrayOf(0x00),
                Tlv8.Tag.SEQ_NO to byteArrayOf(0x01)
            )
        )
        val resp = http.post("/pair-setup", m1, headers)
        if (resp.statusCode !in 200..299) {
            throw PinPairingException("pair-setup M1 returned HTTP ${resp.statusCode}")
        }

        val m2 = Tlv8.read(resp.body)
        m2[Tlv8.Tag.ERROR]?.let {
            throw PinPairingException(
                "Device rejected pairing (HAP error ${it.firstOrNull()?.toInt() ?: -1})"
            )
        }
        salt = m2[Tlv8.Tag.SALT] ?: throw PinPairingException("M2 missing Salt")
        devicePublicKey = m2[Tlv8.Tag.PUBLIC_KEY]
            ?: throw PinPairingException("M2 missing PublicKey")
    }

    /**
     * Completes pairing with the PIN the user read off the screen.
     * @param pin the 4-digit code, as shown
     */
    fun finish(pin: String): Credentials {
        val s = salt ?: throw PinPairingException("start() must be called first")
        val devKey = devicePublicKey ?: throw PinPairingException("start() must be called first")

        // ---- M3: SRP proof, using the PIN as the password ----
        val srp = Srp6aClient(username = "Pair-Setup", password = pin)
        srp.process(s, BigInteger(1, devKey))

        val m3 = Tlv8.write(
            listOf(
                Tlv8.Tag.SEQ_NO to byteArrayOf(0x03),
                Tlv8.Tag.PUBLIC_KEY to toUnsignedBytes(srp.clientPublic),
                Tlv8.Tag.PROOF to srp.clientProof
            )
        )
        val m4 = http.post("/pair-setup", m3, headers)
        if (m4.statusCode !in 200..299) {
            throw PinPairingException("pair-setup M3 returned HTTP ${m4.statusCode}")
        }
        Tlv8.read(m4.body)[Tlv8.Tag.ERROR]?.let {
            val code = it.firstOrNull()?.toInt() ?: -1
            throw PinPairingException(
                if (code == 2) "Incorrect PIN - check the code on screen and try again"
                else "Device rejected the proof (HAP error $code)"
            )
        }

        // ---- M5: send our identity, signed, encrypted under the session key ----
        val sessionKey = Hkdf.deriveKey(ENCRYPT_SALT, ENCRYPT_INFO, srp.sessionKey)
        val deviceX = Hkdf.deriveKey(SIGN_SALT, SIGN_INFO, srp.sessionKey)

        val clientIdBytes = clientIdentifier.toByteArray(Charsets.UTF_8)
        val publicKeyBytes = verifyingKey.encoded

        // Signed payload is deviceX || pairingId || ourPublicKey, in that order.
        val toSign = deviceX + clientIdBytes + publicKeyBytes
        val signer = Ed25519Signer().apply {
            init(true, signingKey)
            update(toSign, 0, toSign.size)
        }
        val signature = signer.generateSignature()

        val subTlv = Tlv8.write(
            listOf(
                Tlv8.Tag.IDENTIFIER to clientIdBytes,
                Tlv8.Tag.PUBLIC_KEY to publicKeyBytes,
                Tlv8.Tag.SIGNATURE to signature
            )
        )
        val encrypted = HapChaCha20Poly1305.encrypt(sessionKey, NONCE_M5, subTlv)

        val m5 = Tlv8.write(
            listOf(
                Tlv8.Tag.SEQ_NO to byteArrayOf(0x05),
                Tlv8.Tag.ENCRYPTED_DATA to encrypted
            )
        )
        val m6 = http.post("/pair-setup", m5, headers)
        if (m6.statusCode !in 200..299) {
            throw PinPairingException("pair-setup M5 returned HTTP ${m6.statusCode}")
        }

        val m6Tlv = Tlv8.read(m6.body)
        m6Tlv[Tlv8.Tag.ERROR]?.let {
            throw PinPairingException("Device rejected our identity (HAP error ${it.firstOrNull()?.toInt() ?: -1})")
        }
        val encryptedResponse = m6Tlv[Tlv8.Tag.ENCRYPTED_DATA]
            ?: throw PinPairingException("M6 missing EncryptedData")

        // ---- M6: decrypt the device's identity ----
        val decrypted = try {
            HapChaCha20Poly1305.decrypt(sessionKey, NONCE_M6, encryptedResponse)
        } catch (e: Exception) {
            throw PinPairingException("Could not decrypt M6: ${e.message}")
        }
        val inner = Tlv8.read(decrypted)
        val deviceId = inner[Tlv8.Tag.IDENTIFIER]
            ?: throw PinPairingException("M6 missing device Identifier")
        val deviceLtpk = inner[Tlv8.Tag.PUBLIC_KEY]
            ?: throw PinPairingException("M6 missing device PublicKey")

        return Credentials(
            devicePublicKey = deviceLtpk,
            clientPrivateKey = signingSeed,
            clientPublicKey = publicKeyBytes,
            deviceIdentifier = String(deviceId, Charsets.UTF_8),
            clientIdentifier = clientIdentifier
        )
    }

    private fun toUnsignedBytes(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }
}
