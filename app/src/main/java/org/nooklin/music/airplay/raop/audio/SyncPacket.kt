package org.nooklin.music.airplay.raop.audio

/**
 * RTCP-style sync packets sent on the CONTROL channel.
 *
 * These give the receiver the anchor it needs to decide WHEN to play the audio it is
 * buffering. Without them a receiver will happily accept the stream and open its audio
 * output (audible as a click when the stream starts or stops) but render nothing,
 * because it has no mapping from RTP timestamps to wall-clock time.
 *
 * Layout ported from OwnTone's sync_packet_ntp_make (src/outputs/rtp_common.c),
 * 20 bytes for the NTP (non-PTP) case:
 *   [0]     type: 0x90 for the FIRST packet after a device connects, 0x80 thereafter
 *   [1]     0xD4
 *   [2..3]  0x00 0x07  (matches what iTunes/Apple Music send)
 *   [4..7]  current RTP position, big-endian
 *   [8..11]  NTP seconds, big-endian
 *   [12..15] NTP fraction, big-endian
 *   [16..19] the RTP timestamp of the NEXT audio packet to be sent, big-endian
 *
 * 0.0.157: bytes 16..19 used to hold the RTP time the stream started (always 0). The AirPlay spec says "RTP timestamp for the
 * next audio packet" and OwnTone writes the running sample position there (rtp_common.c, sync_packet_ntp_make). A receiver
 * works out its latency as [16..19] minus [4..7] (here 11025), so with a 0 there it saw a nonsense latency. An AirPort
 * Express did not care, a Denon AVR-X2400H played nothing.
 */
object SyncPacket {

    const val LENGTH = 20
    const val TYPE_FIRST = 0x90
    const val TYPE_ONGOING = 0x80

    // Seconds between the NTP epoch (1900) and the Unix epoch (1970).
    private const val NTP_UNIX_EPOCH_DIFF = 2_208_988_800L

    /**
     * @param currentRtpPos the RTP timestamp the receiver should currently be playing
     *        (i.e. now-time position, behind what we have sent by the latency)
     * @param nextRtpPos the RTP timestamp of the next audio packet to be sent (current position plus the latency)
     * @param isFirst true for the initial sync packet after connecting
     */
    fun build(currentRtpPos: Long, nextRtpPos: Long, isFirst: Boolean): ByteArray {
        val data = ByteArray(LENGTH)
        data[0] = (if (isFirst) TYPE_FIRST else TYPE_ONGOING).toByte()
        data[1] = 0xD4.toByte()
        data[2] = 0x00
        data[3] = 0x07

        writeBE32(data, 4, currentRtpPos)

        val millis = System.currentTimeMillis()
        val ntpSeconds = millis / 1000 + NTP_UNIX_EPOCH_DIFF
        val ntpFraction = ((millis % 1000) * (1L shl 32)) / 1000
        writeBE32(data, 8, ntpSeconds)
        writeBE32(data, 12, ntpFraction)

        writeBE32(data, 16, nextRtpPos)
        return data
    }

    private fun writeBE32(target: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 4) {
            target[offset + i] = ((value shr (8 * (3 - i))) and 0xFF).toByte()
        }
    }
}
