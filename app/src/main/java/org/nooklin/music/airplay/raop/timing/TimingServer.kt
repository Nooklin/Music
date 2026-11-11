package org.nooklin.music.airplay.raop.timing

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

/**
 * NTP-style timing server the RECEIVER queries.
 *
 * We advertise `timingProtocol = "NTP"` and a `timingPort` in the session SETUP, and
 * the receiver then sends timing requests TO US on that UDP port. Advertising a port
 * with nothing listening leaves the receiver unable to establish a clock reference.
 *
 * Wire format, ported from OwnTone's timing_svc_cb (src/outputs/airplay.c):
 *   Request  (32 bytes): header 0x80 0xD2, client transmit timestamp at offset 24
 *   Response (32 bytes): header 0x80 0xD3, req[2] echoed at res[2],
 *                        client timestamp copied to offset 8,
 *                        our receive timestamp at offset 16,
 *                        our transmit timestamp at offset 24
 * Timestamps are NTP format: 4 bytes seconds since 1900 + 4 bytes fraction, big-endian.
 *
 * This is also the foundation for multi-room sync later: a shared, comparable clock
 * across receivers is what keeps them aligned.
 */
class TimingServer : AutoCloseable {

    companion object {
        private const val TAG = "AirSyncTiming"
        // Seconds between the NTP epoch (1900-01-01) and the Unix epoch (1970-01-01).
        private const val NTP_UNIX_EPOCH_DIFF = 2_208_988_800L
    }

    private var socket: DatagramSocket? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile var requestCount: Int = 0
        private set

    /** Binds an ephemeral UDP port and starts serving. Returns the bound port. */
    fun start(): Int {
        val s = DatagramSocket(0) // let the OS pick a free port
        org.nooklin.music.airplay.AirPlayNetwork.bind(s)
        socket = s
        running = true
        val port = s.localPort

        worker = thread(name = "airsync-timing", isDaemon = true) {
            val buf = ByteArray(64)
            while (running) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    s.receive(packet)
                    val recvStamp = ntpNow()

                    if (packet.length != 32) {
                        Log.w(TAG, "Timing request with unexpected size ${packet.length}, ignoring")
                        continue
                    }
                    val req = packet.data
                    if ((req[0].toInt() and 0xFF) != 0x80 || (req[1].toInt() and 0xFF) != 0xD2) {
                        Log.w(TAG, "Bad timing header 0x%02x%02x, ignoring"
                            .format(req[0].toInt() and 0xFF, req[1].toInt() and 0xFF))
                        continue
                    }

                    val res = ByteArray(32)
                    res[0] = 0x80.toByte()
                    res[1] = 0xD3.toByte()
                    res[2] = req[2]
                    // Echo the client's transmit timestamp (req offset 24) into res offset 8
                    System.arraycopy(req, 24, res, 8, 8)
                    writeNtp(res, 16, recvStamp)          // our receive time
                    writeNtp(res, 24, ntpNow())           // our transmit time

                    s.send(DatagramPacket(res, res.size, packet.address, packet.port))
                    requestCount += 1
                    if (requestCount <= 3 || requestCount % 50 == 0) {
                    }
                } catch (e: Exception) {
                }
            }
        }

        return port
    }

    /** Current time as an NTP timestamp: seconds since 1900 + 32-bit fraction. */
    private fun ntpNow(): Pair<Long, Long> {
        val millis = System.currentTimeMillis()
        val seconds = millis / 1000 + NTP_UNIX_EPOCH_DIFF
        val fraction = ((millis % 1000) * (1L shl 32)) / 1000
        return seconds to fraction
    }

    private fun writeNtp(target: ByteArray, offset: Int, stamp: Pair<Long, Long>) {
        val (sec, frac) = stamp
        for (i in 0 until 4) target[offset + i] = ((sec shr (8 * (3 - i))) and 0xFF).toByte()
        for (i in 0 until 4) target[offset + 4 + i] = ((frac shr (8 * (3 - i))) and 0xFF).toByte()
    }

    override fun close() {
        running = false
        socket?.close()
    }
}
