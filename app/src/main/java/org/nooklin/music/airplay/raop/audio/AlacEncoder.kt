package org.nooklin.music.airplay.raop.audio

/**
 * ALAC encoder producing UNCOMPRESSED frames.
 *
 * ALAC is lossless, and its bitstream has an "is not compressed" escape path storing
 * raw PCM verbatim. Frames produced this way decode BIT-IDENTICALLY to ones from the
 * full predictive encoder - the only cost is bandwidth (~1.4 Mbps for 44.1/16 stereo
 * vs ~800 kbps–1 Mbps compressed). Audio quality is unaffected.
 *
 * Bit layout verified against the ALAC DECODER in libraop (src/alac.c, decode_frame,
 * stereo case), which parses exactly what we emit. Field order:
 *   3 bits   channels           (1 = 2 channels)
 *   4 bits   unknown            (0)
 *   12 bits  unknown            (0)
 *   1 bit    hassize            (0 = default sample count)
 *   2 bits   uncompressed_bytes (0)
 *   1 bit    isnotcompressed    (1 = raw PCM follows)
 * = 23 bits.
 *
 * IMPORTANT: 23 bits, NOT 24 - the header is deliberately not byte-aligned, so the
 * samples that follow must be BIT-packed, not byte-copied. Getting this wrong yields
 * a frame that looks plausible and decodes to noise.
 *
 * Then, per stereo frame: 16 bits left, 16 bits right, big-endian bit order.
 * Total frame size = ceil((23 + frameCount * 32) / 8) bytes; 1411 for 352 frames.
 *
 * Verified by a Python model that encodes and then decodes with the decoder's exact
 * bit reads, confirming a lossless round trip including full-scale and negative values.
 */
object AlacEncoder {

    /**
     * Bytes an encoded frame occupies: 23-bit header + 32 bits per stereo frame
     * + a 3-bit END element, then padded to a byte boundary.
     */
    fun encodedSize(frameCount: Int): Int = (23 + frameCount * 32 + 3 + 7) / 8

    /**
     * Encodes interleaved 16-bit LITTLE-endian stereo PCM (as Android delivers it)
     * into one uncompressed ALAC frame.
     *
     * @param pcm interleaved L,R 16-bit little-endian samples
     * @param frameCount stereo sample pairs (352 for AirPlay)
     */
    fun encodeUncompressedStereo(
        pcm: ByteArray,
        frameCount: Int,
        /**
         * Linear gain applied to the samples, 0.0..1.0. Used only for the connect
         * fade-in; 1.0 (the default) is bit-exact passthrough.
         *
         * The fade is applied to the PCM rather than by ramping SET_PARAMETER because
         * the volume worker deliberately COALESCES rapid changes and would flatten a
         * ramp into a single jump - defeating the point.
         */
        gain: Float = 1.0f
    ): ByteArray {
        require(pcm.size >= frameCount * 4) {
            "Need ${frameCount * 4} bytes for $frameCount stereo frames, got ${pcm.size}"
        }

        val out = ByteArray(encodedSize(frameCount))
        var bitPos = 0

        fun writeBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                if ((value shr i) and 1 == 1) {
                    out[bitPos ushr 3] = (out[bitPos ushr 3].toInt() or (0x80 ushr (bitPos and 7))).toByte()
                }
                bitPos += 1
            }
        }

        // 23-bit header
        writeBits(1, 3)   // channels: 1 = stereo
        writeBits(0, 4)
        writeBits(0, 12)
        writeBits(0, 1)   // hassize
        writeBits(0, 2)   // uncompressed_bytes
        writeBits(1, 1)   // isnotcompressed = raw PCM

        // Samples, converting each little-endian pair to a 16-bit big-endian bit run
        var src = 0
        repeat(frameCount) {
            var left = ((pcm[src + 1].toInt() and 0xFF) shl 8) or (pcm[src].toInt() and 0xFF)
            var right = ((pcm[src + 3].toInt() and 0xFF) shl 8) or (pcm[src + 2].toInt() and 0xFF)
            if (gain < 1.0f) {
                // Sign-extend, scale, clamp, then mask back to 16 bits.
                val l = ((left.toShort().toInt() * gain).toInt()).coerceIn(-32768, 32767)
                val r = ((right.toShort().toInt() * gain).toInt()).coerceIn(-32768, 32767)
                left = l and 0xFFFF
                right = r and 0xFFFF
            }
            writeBits(left, 16)
            writeBits(right, 16)
            src += 4
        }

        // ALAC element terminator: 3 bits of value 7 (END), then byte-align.
        // The frame is a sequence of bitstream elements and must be closed properly;
        // omitting it leaves an unterminated element. (Documented in
        // akustikrausch/airplay2-sender-cpp, Apache-2.0.)
        writeBits(7, 3)

        return out
    }
}
