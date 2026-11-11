package org.nooklin.music.playback.mix

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteOrder
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Decodes a whole audio file into a native buffer at the engine rate (44.1 kHz, 16-bit stereo; other rates are
 * converted by the native resampler, mono is doubled, more than two channels keep the first two).
 */
object PcmDecoder {
    class Result(val handle: Long, val frames: Long)

    /** Songs longer than this are not decoded into memory (about 42 MB per 4 minutes). */
    const val MAX_SECONDS = 15 * 60

    private const val TAG = "NooklinMix"
    private const val MIME_ALAC = "audio/alac"

    suspend fun decode(path: String): Result? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var handle = 0L
        try {
            extractor.setDataSource(path)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return@withContext null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs > MAX_SECONDS * 1_000_000L) return@withContext null
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            // Many phones have no ALAC codec: Apple Lossless is decoded by the native decoder (the same samples as Apple's)
            if (mime == MIME_ALAC) return@withContext decodeAlac(extractor, format, durationUs)
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var floatPcm = false
            var tmpShort = ShortArray(0)
            var tmpFloat = FloatArray(0)
            var chunk = ShortArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            fun openBuffer() {
                if (handle == 0L) {
                    val expected = if (durationUs > 0L) durationUs * 44_100L / 1_000_000L else 0L
                    handle = NativeMix.newBuffer(rate, expected)
                }
            }

            while (!outputDone) {
                ensureActive()
                if (!inputDone) {
                    val index = c.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = c.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            c.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = c.dequeueOutputBuffer(info, 10_000)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = c.outputFormat
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    if (handle != 0L && rate != 0) {
                        // The rate cannot change in the middle of a buffer; a decoder that does so is not supported
                        Log.w(TAG, "decoder output format changed after the start")
                    }
                } else if (out >= 0) {
                    if (info.size > 0) {
                        openBuffer()
                        val buffer = c.getOutputBuffer(out)!!
                        buffer.order(ByteOrder.nativeOrder())
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val frames: Int
                        if (floatPcm) {
                            val fb = buffer.asFloatBuffer()
                            val n = fb.remaining()
                            if (tmpFloat.size < n) tmpFloat = FloatArray(n)
                            fb.get(tmpFloat, 0, n)
                            frames = n / channels
                            if (chunk.size < frames * 2) chunk = ShortArray(frames * 2)
                            for (i in 0 until frames) {
                                val l = tmpFloat[i * channels]
                                val r = if (channels > 1) tmpFloat[i * channels + 1] else l
                                chunk[2 * i] = (l.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                chunk[2 * i + 1] = (r.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                            }
                        } else {
                            val sb = buffer.asShortBuffer()
                            val n = sb.remaining()
                            if (tmpShort.size < n) tmpShort = ShortArray(n)
                            sb.get(tmpShort, 0, n)
                            frames = n / channels
                            if (chunk.size < frames * 2) chunk = ShortArray(frames * 2)
                            for (i in 0 until frames) {
                                val l = tmpShort[i * channels]
                                chunk[2 * i] = l
                                chunk[2 * i + 1] = if (channels > 1) tmpShort[i * channels + 1] else l
                            }
                        }
                        if (frames > 0) NativeMix.append(handle, chunk, frames)
                    }
                    c.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            if (handle == 0L) return@withContext null
            NativeMix.finish(handle)
            val frames = NativeMix.frames(handle)
            if (frames <= 0L) {
                NativeMix.freeBuffer(handle)
                return@withContext null
            }
            Result(handle, frames)
        } catch (e: CancellationException) {
            if (handle != 0L) NativeMix.freeBuffer(handle)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "decoding failed: ${e.javaClass.simpleName}")
            if (handle != 0L) NativeMix.freeBuffer(handle)
            null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            codec?.release()
            extractor.release()
        }
    }

    /** Apple Lossless: the packets of the track go straight through the native decoder. */
    private suspend fun decodeAlac(extractor: MediaExtractor, format: MediaFormat, durationUs: Long): Result? {
        val csd = format.getByteBuffer("csd-0")
        if (csd == null) {
            Log.w(TAG, "alac: the track has no codec data")
            return null
        }
        val cookie = ByteArray(csd.remaining())
        csd.duplicate().get(cookie)
        val alac = NativeMix.alacOpen(cookie)
        if (alac == 0L) {
            Log.w(TAG, "alac: stream not supported (${cookie.size} bytes of codec data)")
            return null
        }
        var handle = 0L
        try {
            val info = NativeMix.alacInfo(alac)
            val rate = info[0]
            val expected = if (durationUs > 0L) durationUs * 44_100L / 1_000_000L else 0L
            handle = NativeMix.newBuffer(rate, expected)
            val packet = java.nio.ByteBuffer.allocate(256 * 1024)
            val out = ShortArray(info[3] * 2)
            var damaged = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                packet.clear()
                val size = extractor.readSampleData(packet, 0)
                if (size < 0) break
                val frames = NativeMix.alacDecode(alac, packet.array(), size, out)
                if (frames > 0) {
                    NativeMix.append(handle, out, frames)
                } else if (frames < 0 && ++damaged > 20) {
                    Log.w(TAG, "alac: too many damaged packets")
                    NativeMix.freeBuffer(handle)
                    return null
                }
                extractor.advance()
            }
            NativeMix.finish(handle)
            val frames = NativeMix.frames(handle)
            if (frames <= 0L) {
                NativeMix.freeBuffer(handle)
                return null
            }
            return Result(handle, frames)
        } catch (e: CancellationException) {
            if (handle != 0L) NativeMix.freeBuffer(handle)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "alac decoding failed: ${e.javaClass.simpleName}")
            if (handle != 0L) NativeMix.freeBuffer(handle)
            return null
        } finally {
            NativeMix.alacClose(alac)
        }
    }
}
