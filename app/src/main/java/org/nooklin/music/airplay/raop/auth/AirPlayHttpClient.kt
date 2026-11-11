package org.nooklin.music.airplay.raop.auth

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal HTTP/1.1 client over a single PERSISTENT raw TCP socket.
 *
 * Why not HttpURLConnection / OkHttp: HAP pairing state (M1 -> M2 -> M3 -> M4) is
 * bound to one TCP connection on the receiver side. A pooled HTTP client may open a
 * fresh connection per request, so later steps arrive on a socket the receiver has
 * no session for -- and its minimal embedded server simply never replies, producing
 * a timeout rather than an error. Standard clients also inject/rewrite headers and
 * may switch to chunked transfer encoding, which that server doesn't handle.
 *
 * Framing and response parsing mirror pyatv's support/http.py
 * (_format_message / _parse_http_message) byte for byte: CRLF line endings,
 * Content-Length only when a body is present, blank line before body, and the
 * response body read to exactly Content-Length bytes.
 *
 * Close this (it is Closeable) only after the whole pairing handshake completes.
 */
class AirPlayHttpClient(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 10000
) : AutoCloseable {

    companion object {
        private const val TAG = "AirSyncHttp"
    }

    class HttpFailure(val statusCode: Int, message: String) : Exception(message)

    // NOTE: do NOT use Socket().apply { ... } here. Inside `apply`, `this` is the
    // Socket, and Socket has its own `port` property (0 until connected) which
    // silently SHADOWS this class's `port` constructor parameter - producing a
    // connect to port 0 and an ECONNREFUSED/timeout that looks like a protocol
    // problem. Building the socket explicitly keeps the parameters unambiguous.
    private val socket: Socket = Socket().also { s ->
        s.tcpNoDelay = true // pairing messages are small; don't let Nagle delay them
        s.soTimeout = readTimeoutMs
        org.nooklin.music.airplay.AirPlayNetwork.bind(s)
        val t0 = System.currentTimeMillis()
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
    }

    /**
     * The local interface address of THIS connection, as seen by the OS.
     *
     * Use this for the RTSP session URI -- never InetAddress.getLocalHost(), which on
     * Android commonly returns 127.0.0.1. A loopback address in the session URI makes
     * the receiver drop the connection immediately without any error response.
     */
    val localIp: String
        get() = socket.localAddress?.hostAddress ?: "0.0.0.0"

    private val output: OutputStream = socket.getOutputStream()
    private val rawInput: InputStream = BufferedInputStream(socket.getInputStream())

    /**
     * Non-null once pairing has enabled encryption. From that point EVERY byte in both
     * directions is HAP-framed and ChaCha20-encrypted -- see [HapSession].
     */
    private var session: HapSession? = null

    /** Plaintext bytes already decrypted but not yet consumed by the response parser. */
    private var plainBuffer = ByteArray(0)
    private var plainPos = 0

    /**
     * Switches the connection to encrypted mode. Must be called immediately after a
     * successful pairing handshake and before any further request -- the receiver
     * expects encryption from that moment on.
     */
    fun enableEncryption(outputKey: ByteArray, inputKey: ByteArray) {
        session = HapSession(outputKey, inputKey)
    }

    /** Reads one byte of plaintext, decrypting another frame from the wire if needed. */
    private fun readPlainByte(): Int {
        val s = session ?: return rawInput.read()
        while (plainPos >= plainBuffer.size) {
            val prefix = readRawExactly(2) ?: return -1
            val frameLen = (prefix[0].toInt() and 0xFF) or ((prefix[1].toInt() and 0xFF) shl 8)
            val body = readRawExactly(s.frameBodySize(frameLen)) ?: return -1
            plainBuffer = s.decryptFrame(prefix, body)
            plainPos = 0
        }
        return plainBuffer[plainPos++].toInt() and 0xFF
    }

    private fun readRawExactly(length: Int): ByteArray? {
        val out = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = rawInput.read(out, read, length - read)
            if (n == -1) return null
            read += n
        }
        return out
    }

    private fun writeRaw(bytes: ByteArray) {
        val s = session
        output.write(if (s != null) s.encrypt(bytes) else bytes)
        output.flush()
    }

    data class Response(val statusCode: Int, val headers: Map<String, String>, val body: ByteArray)

    fun post(path: String, body: ByteArray, headers: Map<String, String>): Response =
        exchange("POST", path, body, headers, "HTTP/1.1")

    /**
     * Sends a request and reads one response over the persistent socket.
     * `protocol` is "HTTP/1.1" for pairing and "RTSP/1.0" for stream setup -- both
     * share the same message framing, which is why one client serves both.
     */
    fun exchange(
        method: String,
        path: String,
        body: ByteArray,
        headers: Map<String, String>,
        protocol: String = "RTSP/1.0",
        includeHost: Boolean = true
    ): Response {
        val header = StringBuilder()
        header.append(method).append(" ").append(path).append(" ").append(protocol).append("\r\n")
        // The reference implementation (pyatv _format_message) sends NO Host header.
        // Pairing works with one, so it stays for HTTP; RTSP omits it, since AirTunes'
        // RTSP parser is stricter and an unexpected header is a plausible reason for it
        // to drop the connection without replying.
        if (includeHost) {
            header.append("Host: ").append(host).append(":").append(port).append("\r\n")
        }
        for ((k, v) in headers) {
            header.append(k).append(": ").append(v).append("\r\n")
        }
        // Content-Length is written explicitly here; because we own the socket, this
        // is exactly what goes on the wire -- no chunked-encoding fallback possible.
        header.append("Content-Length: ").append(body.size).append("\r\n")
        header.append("\r\n")

        // Header and body must be encrypted as a single logical message, so they are
        // concatenated before framing rather than written separately.
        val headerBytes = header.toString().toByteArray(Charsets.UTF_8)
        writeRaw(if (body.isEmpty()) headerBytes else headerBytes + body)

        val t0 = System.currentTimeMillis()
        val resp = readResponse()
        return resp
    }

    private fun readResponse(): Response {
        val headerBytes = readUntilHeaderEnd()
        val headerText = String(headerBytes, Charsets.UTF_8)
        val lines = headerText.split("\r\n").filter { it.isNotEmpty() }

        require(lines.isNotEmpty()) { "Empty HTTP response from receiver" }

        // Status line: "HTTP/1.1 200 OK"
        val statusParts = lines[0].split(" ", limit = 3)
        val statusCode = statusParts.getOrNull(1)?.toIntOrNull()
            ?: throw HttpFailure(-1, "Malformed status line: ${lines[0]}")

        val headerMap = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val idx = line.indexOf(": ")
            if (idx > 0) {
                headerMap[line.substring(0, idx).lowercase()] = line.substring(idx + 2)
            }
        }

        val contentLength = headerMap["content-length"]?.toIntOrNull() ?: 0
        val bodyBytes = readExactly(contentLength)

        return Response(statusCode, headerMap, bodyBytes)
    }

    /** Reads bytes until the CRLFCRLF header terminator, returning the header block only. */
    private fun readUntilHeaderEnd(): ByteArray {
        val buffer = ByteArrayOutputStream()
        var matched = 0 // how many bytes of "\r\n\r\n" matched so far
        val terminator = byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A)

        while (true) {
            val b = readPlainByte()
            if (b == -1) {
                Log.w(TAG, "Receiver closed connection after ${buffer.size()} header bytes")
                throw HttpFailure(-1, "Connection closed by receiver before response headers completed")
            }
            buffer.write(b)
            matched = if (b.toByte() == terminator[matched]) matched + 1 else if (b.toByte() == terminator[0]) 1 else 0
            if (matched == 4) break
        }
        val all = buffer.toByteArray()
        return all.copyOf(all.size - 4) // strip the terminator itself
    }

    private fun readExactly(length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val out = ByteArray(length)
        for (i in 0 until length) {
            val b = readPlainByte()
            if (b == -1) throw HttpFailure(-1, "Connection closed mid-body (got $i of $length bytes)")
            out[i] = b.toByte()
        }
        return out
    }

    override fun close() {
        try {
            socket.close()
        } catch (_: Exception) {
            // best effort
        }
    }
}
