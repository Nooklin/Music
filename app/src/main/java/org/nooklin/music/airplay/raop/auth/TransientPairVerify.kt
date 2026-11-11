package org.nooklin.music.airplay.raop.auth

import android.util.Log
import java.math.BigInteger

/**
 * "Transient" HAP pairing: establishes a session with an AirPlay 2 receiver
 * WITHOUT requiring the user to read a PIN off a screen and type it in.
 *
 * This works because HAP defines a well-known fixed PIN (3939) for transient
 * (non-persistent) pairing -- see pyatv's hap_transient.py, TRANSIENT_PIN -- used
 * by senders that don't need a long-term trust relationship with the receiver.
 * It only succeeds against receivers that ALLOW unauthenticated/transient
 * connections. A receiver with "Require Password" or restricted access enabled
 * will reject this and needs the full PIN-entry pairing flow, which is NOT
 * implemented yet -- that's a known gap, surfaced as a clear exception rather
 * than a silent failure.
 *
 * IMPORTANT: all requests in this handshake go over ONE persistent socket
 * ([AirPlayHttpClient]). The receiver binds pairing state to the connection;
 * sending M3 on a different socket than M1 leaves it with no matching session
 * and it will never reply.
 *
 * Flow (M1-M4), ported from pyatv's AirPlayHapTransientPairVerifyProcedure:
 *   1. POST /pair-pin-start
 *   2. POST /pair-setup  {Method=0, SeqNo=M1, Flags=TransientPairing}
 *   3. <- response TLV contains Salt + server public key (M2)
 *   4. SRP: process(salt, serverPublic), username "Pair-Setup", password "3939"
 *   5. POST /pair-setup  {SeqNo=M3, PublicKey=clientPublic, Proof=clientProof}
 *   6. <- M4 response
 */
class TransientPairVerify(
    private val host: String,
    private val port: Int,
    private val senderName: String = "Android"
) {

    companion object {
        private const val TAG = "AirSyncPair"
        private const val TRANSIENT_PIN = "3939"

        // X-Apple-HKP selects the pairing scheme: 4 = transient, 3 = regular HAP.
        // Verified against pyatv hap_transient.py (_AIRPLAY_HEADERS) vs hap.py.
        // Omitting it entirely -- as an earlier version of this file did -- can
        // leave the receiver unsure which flow is intended, so it may not respond.
        // Key-derivation labels for the control (RTSP) channel, from pyatv's
        // airplay/auth/__init__.py: CONTROL_SALT / CONTROL_OUTPUT_INFO / CONTROL_INPUT_INFO.
        // Labels for the audio-stream key. The reference derives the stream key from
        // the EVENTS parameters rather than the control ones (pyatv
        // AirPlayV2.setup_audio_stream) -- its own comment notes the choice is somewhat
        // arbitrary since the key only needs to be agreed, not secret-derived.
        const val EVENTS_SALT = "Events-Salt"
        const val EVENTS_WRITE_INFO = "Events-Write-Encryption-Key"
        const val EVENTS_READ_INFO = "Events-Read-Encryption-Key"

        private const val CONTROL_SALT = "Control-Salt"
        private const val CONTROL_WRITE_INFO = "Control-Write-Encryption-Key"
        private const val CONTROL_READ_INFO = "Control-Read-Encryption-Key"
    }

    // Instance, not companion, since the name varies per phone - X-Apple-Client-Name
    // is what identifies this phone on the receiver's own "<name> wants to AirPlay"
    // confirmation prompt, in place of a blank field.
    private val headers = mapOf(
        "User-Agent" to "AirPlay/320.20",
        "Connection" to "keep-alive",
        "X-Apple-HKP" to "4",
        "X-Apple-Client-Name" to senderName,
        "Content-Type" to "application/octet-stream"
    )

    class PairingRejectedException(message: String) : Exception(message)

    /**
     * Runs the pairing handshake over an EXISTING client, leaving it open.
     * The receiver binds session state to the connection, so stream setup must
     * continue on this same socket.
     */
    fun pairOn(http: AirPlayHttpClient): ByteArray {
        run {
            http.post("/pair-pin-start", ByteArray(0), headers)

            val srp = Srp6aClient(username = "Pair-Setup", password = TRANSIENT_PIN)

            val m1Body = Tlv8.write(
                listOf(
                    Tlv8.Tag.METHOD to byteArrayOf(0x00),
                    Tlv8.Tag.SEQ_NO to byteArrayOf(0x01),
                    Tlv8.Tag.FLAGS to byteArrayOf(Tlv8.Flags.TRANSIENT_PAIRING.toByte())
                )
            )
            val m2Response = http.post("/pair-setup", m1Body, headers)
            if (m2Response.statusCode !in 200..299) {
                val hint = when (m2Response.statusCode) {
                    470 -> "470 means the receiver refuses transient (PIN-less) pairing and " +
                        "requires full HAP pair-setup with a PIN shown on screen. On an Apple TV " +
                        "this is governed by Settings > AirPlay and HomeKit > Allow Access."
                    401, 403 -> "requires password-based pairing, which isn't implemented yet."
                    else -> "unexpected status."
                }
                throw PairingRejectedException(
                    "Receiver returned HTTP ${m2Response.statusCode} to pair-setup M1. $hint"
                )
            }

            val m2 = Tlv8.read(m2Response.body)

            m2[Tlv8.Tag.ERROR]?.let {
                throw PairingRejectedException(
                    "Receiver rejected transient pairing (HAP error code ${it.firstOrNull()?.toInt() ?: -1}). " +
                        "This receiver likely requires password/PIN-based pairing, which isn't implemented yet."
                )
            }

            val salt = m2[Tlv8.Tag.SALT]
                ?: throw PairingRejectedException("M2 response missing Salt -- unexpected reply from receiver")
            val serverPublicBytes = m2[Tlv8.Tag.PUBLIC_KEY]
                ?: throw PairingRejectedException("M2 response missing PublicKey -- unexpected reply from receiver")

            srp.process(salt, BigInteger(1, serverPublicBytes))

            val m3Body = Tlv8.write(
                listOf(
                    Tlv8.Tag.SEQ_NO to byteArrayOf(0x03),
                    Tlv8.Tag.PUBLIC_KEY to toUnsignedBytes(srp.clientPublic),
                    Tlv8.Tag.PROOF to srp.clientProof
                )
            )
            val m4Response = http.post("/pair-setup", m3Body, headers)
            if (m4Response.statusCode !in 200..299) {
                throw PairingRejectedException("Receiver returned HTTP ${m4Response.statusCode} to pair-setup M3")
            }
            Tlv8.read(m4Response.body)[Tlv8.Tag.ERROR]?.let {
                throw PairingRejectedException(
                    "Receiver rejected pairing proof (HAP error code ${it.firstOrNull()?.toInt() ?: -1})"
                )
            }


            // Transient pairing yields encryption keys, and the receiver switches the
            // connection to encrypted mode as soon as the handshake completes. Every
            // request after this point (RTSP SETUP, RECORD, ...) must be encrypted, or
            // the receiver reads garbage and silently drops the connection.
            val outKey = Hkdf.deriveKey(CONTROL_SALT, CONTROL_WRITE_INFO, srp.sessionKey)
            val inKey = Hkdf.deriveKey(CONTROL_SALT, CONTROL_READ_INFO, srp.sessionKey)
            http.enableEncryption(outKey, inKey)

            return srp.sessionKey
        }
    }

    private fun toUnsignedBytes(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }
}
