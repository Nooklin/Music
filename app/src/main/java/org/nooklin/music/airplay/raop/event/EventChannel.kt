package org.nooklin.music.airplay.raop.event

import android.util.Log
import org.nooklin.music.airplay.raop.auth.HapSession
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * The AirPlay 2 event channel.
 *
 * After the session SETUP the receiver returns an `eventPort` and expects the sender
 * to open a SECOND TCP connection to it. The reference connects this channel BEFORE
 * setting up the audio stream (AirPlayV2._setup_base runs before setup_audio_stream),
 * and an AirPort Express rejects the audio SETUP with RTSP 400 until it exists.
 *
 * The channel carries HAP-encrypted HTTP requests FROM the receiver; each is answered
 * with a bare 200. Encryption uses the same framing as the control connection but
 * different keys -- and note the derivation is CROSSED relative to the audio key:
 * output uses "Events-Read-Encryption-Key" and input uses "Events-Write-Encryption-Key"
 * (pyatv _setup_base passes EVENTS_READ_INFO as output_info and EVENTS_WRITE_INFO as
 * input_info, the opposite order to setup_audio_stream's shk derivation).
 */
class EventChannel(
    private val host: String,
    private val port: Int,
    outputKey: ByteArray,
    inputKey: ByteArray,
    /**
     * Invoked if the receiver closes this channel while we still believe we are
     * streaming. That is the earliest signal that ANOTHER SENDER HAS TAKEN OVER the
     * speaker - AirPlay receivers accept one session at a time, and the loser simply
     * has its connections dropped.
     */
    private val onDisconnected: (() -> Unit)? = null
) : AutoCloseable {

    companion object {
        private const val TAG = "AirSyncEvent"
        private const val CONNECT_TIMEOUT_MS = 5000
        // The receiver may open its listener slightly AFTER telling us the port, so
        // the reference retries a few times before giving up.
        private const val CONNECT_RETRIES = 5
        private const val RETRY_DELAY_MS = 1000L
    }

    private val session = HapSession(outputKey, inputKey)
    private var socket: Socket? = null
    private var reader: Thread? = null
    @Volatile private var running = false

    /** Connects, retrying briefly. Returns true if the channel is up. */
    fun connect(): Boolean {
        for (attempt in 1..CONNECT_RETRIES) {
            try {
                val s = Socket()
                s.tcpNoDelay = true
                org.nooklin.music.airplay.AirPlayNetwork.bind(s)
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket = s
                running = true
                startReader(s)
                return true
            } catch (e: Exception) {
                if (attempt < CONNECT_RETRIES) Thread.sleep(RETRY_DELAY_MS)
            }
        }
        Log.w(TAG, "Event channel could not connect")
        return false
    }

    private fun startReader(s: Socket) {
        reader = thread(name = "airsync-event-channel", isDaemon = true) {
            try {
                val input: InputStream = BufferedInputStream(s.getInputStream())
                val output: OutputStream = s.getOutputStream()
                val buffer = StringBuilder()

                while (running) {
                    val prefix = readExactly(input, 2) ?: break
                    val frameLen = (prefix[0].toInt() and 0xFF) or ((prefix[1].toInt() and 0xFF) shl 8)
                    val body = readExactly(input, session.frameBodySize(frameLen)) ?: break
                    val plain = session.decryptFrame(prefix, body)
                    buffer.append(String(plain, Charsets.UTF_8))

                    // Requests are separated by a blank line; answer each one.
                    while (true) {
                        val end = buffer.indexOf("\r\n\r\n")
                        if (end < 0) break
                        val request = buffer.substring(0, end)
                        buffer.delete(0, end + 4)
                        respond(output, request)
                    }
                }
            } catch (e: Exception) {
                if (running) Log.w(TAG, "Event channel dropped by receiver: ${e.javaClass.simpleName}")
            }
            // Reaching here while still running means the receiver hung up on us.
            if (running) {
                running = false
                Log.w(TAG, "Event channel closed remotely - session appears to be gone")
                onDisconnected?.invoke()
            }
        }
    }

    /** Answers a receiver request with 200, echoing CSeq/Server as the reference does. */
    private fun respond(output: OutputStream, request: String) {
        val firstLine = request.lineSequence().firstOrNull() ?: ""

        val cseq = headerValue(request, "CSeq")
        val server = headerValue(request, "Server")
        val protocol = if (firstLine.contains("RTSP")) "RTSP/1.0" else "HTTP/1.1"

        // THE RESPONSE MUST BE MINIMAL: status line, Server, CSeq. Nothing else.
        // Adding Content-Length or Audio-Latency corrupts the receiver's realtime
        // timeline - the session stays CONNECTED and renders SILENCE. (Documented in
        // akustikrausch/airplay2-sender-cpp, Apache-2.0, as the final "stable but
        // silent" bug there: timing, sync and ALAC were all correct and two extra
        // response headers were the entire fault.)
        val sb = StringBuilder()
        sb.append("$protocol 200 OK\r\n")
        if (server != null) sb.append("Server: $server\r\n")
        if (cseq != null) sb.append("CSeq: $cseq\r\n")
        sb.append("\r\n")

        output.write(session.encrypt(sb.toString().toByteArray(Charsets.UTF_8)))
        output.flush()
    }

    private fun headerValue(request: String, name: String): String? =
        request.lineSequence()
            .firstOrNull { it.startsWith("$name:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()

    private fun readExactly(input: InputStream, length: Int): ByteArray? {
        val out = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(out, read, length - read)
            if (n == -1) return null
            read += n
        }
        return out
    }

    override fun close() {
        running = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }
}
