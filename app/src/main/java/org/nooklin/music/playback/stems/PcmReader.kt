package org.nooklin.music.playback.stems

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Reads a stretch of a song as stereo audio at 44.1 kHz with the platform decoder. A song that is not downloaded is
 * read from the server, only the bytes of that stretch. The audio exists only in memory.
 */
object PcmReader {
    private const val TARGET_RATE = StemSeparator.SAMPLE_RATE

    /** The audio from [fromMs] to [toMs] in the song, or null when it could not be read. */
    fun read(source: (MediaExtractor) -> Unit, fromMs: Long, toMs: Long, limitMs: Long): Pcm? {
        val extractor = MediaExtractor()
        return try {
            source(extractor)
            decode(extractor, fromMs, toMs, limitMs)
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private class Growing {
        var left = FloatArray(1 shl 18)
        var right = FloatArray(1 shl 18)
        var count = 0

        fun add(l: Float, r: Float) {
            if (count == left.size) {
                left = left.copyOf(count * 2)
                right = right.copyOf(count * 2)
            }
            left[count] = l
            right[count] = r
            count++
        }
    }

    private fun decode(extractor: MediaExtractor, fromMs: Long, toMs: Long, limitMs: Long): Pcm? {
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                extractor.selectTrack(i)
                format = f
                break
            }
        }
        if (format == null) return null
        if (fromMs > 0) extractor.seekTo(fromMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            val got = Growing()
            var firstUs = -1L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val deadline = SystemClock.elapsedRealtime() + limitMs
            while (!outputDone && SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(5_000L)
                    if (i >= 0) {
                        val buffer = codec.getInputBuffer(i)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5_000L)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    if (info.size > 0) {
                        if (firstUs < 0) firstUs = info.presentationTimeUs
                        val buffer = codec.getOutputBuffer(o)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder())
                        val frames = info.size / (channels * if (floatPcm) 4 else 2)
                        if (floatPcm) {
                            val view = buffer.asFloatBuffer()
                            for (f in 0 until frames) {
                                var l = 0f
                                var r = 0f
                                for (c in 0 until channels) {
                                    val v = view.get()
                                    if (c == 0) l = v
                                    if (c == 1) r = v
                                }
                                got.add(l, if (channels >= 2) r else l)
                            }
                        } else {
                            val view = buffer.asShortBuffer()
                            for (f in 0 until frames) {
                                var l = 0f
                                var r = 0f
                                for (c in 0 until channels) {
                                    val v = view.get() / 32768f
                                    if (c == 0) l = v
                                    if (c == 1) r = v
                                }
                                got.add(l, if (channels >= 2) r else l)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    if (firstUs >= 0 && firstUs / 1000.0 + got.count * 1000.0 / sampleRate >= toMs) outputDone = true
                }
            }
            if (firstUs < 0 || got.count == 0) return null

            // The stretch asked for, in the samples that were read (the read begins at or before [fromMs])
            val wanted = ((toMs - fromMs) * sampleRate / 1000.0).roundToInt()
            val drop = ((fromMs * 1000.0 - firstUs) * sampleRate / 1_000_000.0).roundToInt()
            val l = FloatArray(wanted)
            val r = FloatArray(wanted)
            for (i in 0 until wanted) {
                val s = i + drop
                if (s in 0 until got.count) {
                    l[i] = got.left[s]
                    r[i] = got.right[s]
                }
            }
            return if (sampleRate == TARGET_RATE) Pcm(l, r, fromMs) else resample(l, r, sampleRate, fromMs)
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
        }
    }

    /** Linear interpolation to 44.1 kHz; good enough for the small step from 48 kHz and for the stems it feeds. */
    private fun resample(l: FloatArray, r: FloatArray, rate: Int, fromMs: Long): Pcm {
        val n = (l.size.toLong() * TARGET_RATE / rate).toInt()
        val outL = FloatArray(n)
        val outR = FloatArray(n)
        val step = rate.toDouble() / TARGET_RATE
        for (i in 0 until n) {
            val pos = i * step
            val i0 = floor(pos).toInt()
            val frac = (pos - i0).toFloat()
            val i1 = minOf(i0 + 1, l.size - 1)
            outL[i] = l[i0] * (1f - frac) + l[i1] * frac
            outR[i] = r[i0] * (1f - frac) + r[i1] * frac
        }
        return Pcm(outL, outR, fromMs)
    }
}
