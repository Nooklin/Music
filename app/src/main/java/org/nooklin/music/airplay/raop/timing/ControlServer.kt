package org.nooklin.music.airplay.raop.timing

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

/**
 * UDP control channel. We advertise its port as `controlPort` in the audio stream
 * SETUP; the receiver uses it for retransmit requests and sync packets.
 *
 * For now this only binds a real port and logs what arrives - advertising a dead port
 * was one of the things that differed from a known-working sender. Handling retransmit
 * requests properly comes with the audio streaming work.
 */
class ControlServer : AutoCloseable {

    companion object {
        private const val TAG = "AirSyncControl"
    }

    /**
     * The bound socket. Sync packets must be sent FROM this port, because that is the
     * controlPort we advertised to the receiver - replies and retransmit requests come
     * back here, so the association has to match.
     */
    var socket: DatagramSocket? = null
        private set
    @Volatile private var running = false
        private set

    fun start(): Int {
        val s = DatagramSocket(0)
        org.nooklin.music.airplay.AirPlayNetwork.bind(s)
        socket = s
        running = true
        val port = s.localPort

        thread(name = "airsync-control", isDaemon = true) {
            val buf = ByteArray(2048)
            while (running) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    s.receive(packet)
                } catch (e: Exception) {
                }
            }
        }

        return port
    }

    override fun close() {
        running = false
        socket?.close()
    }
}
