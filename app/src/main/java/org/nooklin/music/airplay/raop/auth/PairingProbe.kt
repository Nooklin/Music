package org.nooklin.music.airplay.raop.auth

import android.content.Context
import org.nooklin.music.airplay.util.DeviceIdentity

/**
 * Confirms a receiver will actually accept us BEFORE anything user-facing happens -
 * specifically before the screen-capture permission prompt and before the card is
 * marked selected. Previously that pairing handshake only ran once StreamingService
 * started connecting, which is AFTER the user had already granted MediaProjection -
 * so a rejected pairing meant they'd been asked for a permission for nothing.
 *
 * This runs the same handshake ReceiverSession.connect() runs, on its own throwaway
 * connection, then closes it either way. HAP session state is bound to one TCP
 * socket, so the real connection used for streaming is opened fresh afterwards
 * rather than reusing this one - the extra round trip is a few tens of ms on a LAN,
 * which is a fair trade for never asking for permission the receiver was always
 * going to refuse.
 */
object PairingProbe {

    enum class Outcome {
        /** Either verified against stored credentials or transient pairing succeeded. */
        READY,
        /** Receiver refused transient pairing and needs the on-screen PIN flow. */
        NEEDS_PIN
    }

    class ProbeException(message: String) : Exception(message)

    /**
     * @param forcePinPairing Skips the transient-pairing attempt entirely when true
     *   and no credential is stored yet, going straight to NEEDS_PIN instead. Some
     *   receivers - a Mac's AirPlay Receiver set to "Anyone on the Same Network" is
     *   the known case - accept transient pairing outright with no prompt at all,
     *   the same way an AirPort Express does. That's fine for a receiver that has no
     *   real long-term identity to speak of, but for one the user wants an actual
     *   stored, revocable credential for, transient pairing succeeding silently is
     *   exactly the problem: nothing ever gets saved, because HAP's transient
     *   pairing is deliberately credential-less by design. Forcing PIN/password
     *   pairing is the only way to end up with something to store.
     */
    fun check(
        context: Context,
        host: String,
        port: Int,
        deviceKey: String,
        forcePinPairing: Boolean = false
    ): Outcome {
        val store = CredentialStore(context)
        val stored = store.load(deviceKey)

        AirPlayHttpClient(host, port).use { client ->
            if (stored != null) {
                try {
                    HapPairVerify(client, stored).verify()
                    return Outcome.READY
                } catch (e: Exception) {
                    // Same reasoning as ReceiverSession.connect(): a rejected pair-verify
                    // means the stored keys are no longer valid, so clear them rather
                    // than leave the app stuck retrying a pairing that can never work.
                    store.clear(deviceKey)
                    throw ProbeException("Pair-verify failed: ${e.message}")
                }
            }

            if (forcePinPairing) return Outcome.NEEDS_PIN

            return try {
                TransientPairVerify(host, port, DeviceIdentity.name(context)).pairOn(client)
                Outcome.READY
            } catch (e: TransientPairVerify.PairingRejectedException) {
                val msg = e.message.orEmpty()
                val needsPin = msg.contains("password-based pairing", ignoreCase = true) ||
                    msg.contains("PIN shown on screen", ignoreCase = true) ||
                    msg.contains("requires full HAP pair-setup", ignoreCase = true)
                if (needsPin) Outcome.NEEDS_PIN else throw ProbeException(msg)
            }
        }
    }
}
