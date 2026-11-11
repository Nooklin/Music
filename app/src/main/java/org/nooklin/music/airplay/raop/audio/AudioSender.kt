package org.nooklin.music.airplay.raop.audio

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sends encrypted RTP audio to the receiver's dataPort.
 *
 * Packet layout, ported from OwnTone's packet_encrypt (src/outputs/airplay.c):
 *   [12-byte RTP header, NOT encrypted]
 *   [ChaCha20-Poly1305 ciphertext of the ALAC payload]
 *   [16-byte auth tag]
 *   [8-byte nonce tail]
 * AAD is header bytes 4..11 (timestamp + SSRC). The nonce is 4 zero bytes followed by
 * the sequence number - deliberately, so a retransmitted packet is byte-identical to
 * the original.
 *
 * RTP header:
 *   byte 0: 0x80 (version 2)
 *   byte 1: payload type (0x60 = 96), with the marker bit set on the first packet
 *   bytes 2-3: sequence number, big-endian
 *   bytes 4-7: RTP timestamp, big-endian
 *   bytes 8-11: SSRC, big-endian
 */
class AudioSender(
    private val host: String,
    private val dataPort: Int,
    audioKey: ByteArray,
    /** The receiver's control port, from the audio stream SETUP response. Sync
     *  packets go here; without them the receiver buffers audio but never plays it. */
    private val receiverControlPort: Int = 0,
    /** Our own bound control socket, so sync packets come FROM the port we advertised. */
    private val controlSocket: DatagramSocket? = null,
    /** 0.0.158: the RTP SSRC; the same number as the streamConnectionID of the stream SETUP (it was always 0 before). */
    private val ssrc: Int = 0
) : AutoCloseable {

    companion object {
        private const val TAG = "AirSyncAudio"
        const val SAMPLE_RATE = 44100
        const val FRAMES_PER_PACKET = 352
        private const val PAYLOAD_TYPE = 0x60
    }

    private val key = SecretKeySpec(audioKey, "ChaCha20")
    private val socket = DatagramSocket().also { org.nooklin.music.airplay.AirPlayNetwork.bind(it) }
    private val address: InetAddress = InetAddress.getByName(host)

    // NOTE: sequence and timestamp are supplied by the CALLER, not owned here.
    // With multiple receivers every one of them must be fed the SAME RTP timeline -
    // identical sequence numbers and timestamps - or they cannot play in sync. The
    // timeline therefore belongs to whoever drives the capture loop, and each sender
    // only handles this receiver's encryption and socket.
    private val startRtpTime = 0L
    var packetsSent = 0
        private set
    var syncPacketsSent = 0
        private set

    /**
     * Sends a sync packet on the control channel, telling the receiver how RTP
     * timestamps map to wall-clock time. Must be sent once immediately (type 0x90)
     * and then about once per second (0x80) while streaming.
     *
     * `currentRtpPos` is deliberately BEHIND what we have transmitted: it represents
     * what should be playing NOW, while we have already sent ahead by the latency the
     * receiver was told about in latencyMin (11025 samples).
     */
    /**
     * Sends a pre-built sync packet. Built by the caller so that EVERY receiver in a
     * group is given a byte-identical timing anchor - the same RTP position mapped to
     * the same wall-clock instant. Building it per-receiver would introduce a few
     * milliseconds of skew between speakers for no reason.
     */
    fun sendSyncPacket(data: ByteArray) {
        val socket = controlSocket ?: return
        if (receiverControlPort == 0) return
        socket.send(DatagramPacket(data, data.size, address, receiverControlPort))
        syncPacketsSent += 1
    }

    /** Builds, encrypts and sends one packet of already-encoded ALAC audio. */
    fun sendFrame(
        alacPayload: ByteArray,
        sequence: Int,
        rtpTimestamp: Long,
        isFirst: Boolean = false
    ) {
        val header = ByteArray(12)
        header[0] = 0x80.toByte()
        header[1] = (if (isFirst) PAYLOAD_TYPE or 0x80 else PAYLOAD_TYPE).toByte()
        header[2] = ((sequence shr 8) and 0xFF).toByte()
        header[3] = (sequence and 0xFF).toByte()
        header[4] = ((rtpTimestamp shr 24) and 0xFF).toByte()
        header[5] = ((rtpTimestamp shr 16) and 0xFF).toByte()
        header[6] = ((rtpTimestamp shr 8) and 0xFF).toByte()
        header[7] = (rtpTimestamp and 0xFF).toByte()
        header[8] = ((ssrc shr 24) and 0xFF).toByte()
        header[9] = ((ssrc shr 16) and 0xFF).toByte()
        header[10] = ((ssrc shr 8) and 0xFF).toByte()
        header[11] = (ssrc and 0xFF).toByte()

        // Nonce: 4 zero bytes + the sequence number (little-endian, as OwnTone memcpy's
        // the uint16 seqnum into nonce+4 on a little-endian host).
        val nonce = ByteArray(12)
        nonce[4] = (sequence and 0xFF).toByte()
        nonce[5] = ((sequence shr 8) and 0xFF).toByte()

        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(nonce))
        cipher.updateAAD(header, 4, 8) // timestamp + SSRC
        val sealed = cipher.doFinal(alacPayload) // ciphertext || 16-byte tag

        val packet = ByteArray(12 + sealed.size + 8)
        System.arraycopy(header, 0, packet, 0, 12)
        System.arraycopy(sealed, 0, packet, 12, sealed.size)
        System.arraycopy(nonce, 4, packet, 12 + sealed.size, 8)

        socket.send(DatagramPacket(packet, packet.size, address, dataPort))
        packetsSent += 1
    }

    override fun close() = socket.close()
}
