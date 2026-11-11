package org.nooklin.music.playback.stems

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.sqrt

/** The four stems of the separation model, in the order of [Separated.estimates]. */
object Stem {
    const val VOCALS = 0
    const val DRUMS = 1
    const val BASS = 2
    const val OTHER = 3
    const val COUNT = 4
    val NAMES = arrayOf("vocals", "drums", "bass", "other")
}

/**
 * Splits music into vocals, drums, bass and the rest with Spleeter (4 stems), run with ONNX Runtime on this phone.
 * The model takes the magnitudes of a spectrogram and returns an estimate per stem; the split itself is a ratio mask
 * applied to the original spectrum (so the four stems always add up to the original), as described with the model:
 * a periodic Hann window of 4096 samples, a hop of 1024, 44.1 kHz stereo, 1024 of the 2049 bins go to the model and
 * the mask above that takes the mean of the mask below it. Nothing leaves the phone.
 */
class StemSeparator(
    private val modelBytes: (String) -> ByteArray,
    private val threads: Int = 2,
    /** 0.0.129: keep the four model sessions after [separate] so that the next call does not load them again; the owner must call [close]. */
    private val keepSessions: Boolean = false
) {
    companion object {
        const val SAMPLE_RATE = 44100
        const val N_FFT = 4096
        const val HOP = 1024
        const val PAD = N_FFT - HOP
        const val FRAMES_PER_SPLIT = 512
        const val MODEL_BINS = 1024
        const val BINS = N_FFT / 2 + 1
        private const val EPS = 1e-10f

        /**
         * 0.0.130: the spectrogram, the energy and the render run in native code ([NativeStemDsp]) on [DSP_THREADS] threads. Set to false to
         * use the Kotlin code below again (build again; there is no switch at run time).
         */
        const val NATIVE_DSP = true
        private const val DSP_THREADS = 3
    }

    /** 0.0.127: where the time goes, added up over every call of [separate] on this object (milliseconds). */
    var fftMs = 0L
    var loadMs = 0L
    var runMs = 0L

    // 0.0.129: the same, split per stem and per step, to see where the time goes (log of 0.0.128: 63 to 136 s per build)
    var renderMs = 0L
    var energyMs = 0L   // only counted for the native code
    var splitsRun = 0
    var audioSec = 0.0
    private val loadByStemMs = LongArray(Stem.COUNT)
    private val runByStemMs = LongArray(Stem.COUNT)
    private var sessionsCreated = 0

    /** 0.0.129: one piece of the log line of [org.nooklin.music.playback.mix.MixStems]; the per-stem lists are vocals / drums / bass / other. */
    fun timingReport(): String =
        "audio ${"%.1f".format(audioSec)} s in $splitsRun splits, $sessionsCreated model loads ${loadByStemMs.joinToString("/")} ms, " +
            "model run per stem ${runByStemMs.joinToString("/")} ms, render $renderMs ms, energy $energyMs ms, dsp ${if (NATIVE_DSP) "native" else "kotlin"}"

    private val sessions = arrayOfNulls<OrtSession>(Stem.COUNT)
    private val sessionOptions = arrayOfNulls<OrtSession.SessionOptions>(Stem.COUNT)

    private fun sessionFor(env: OrtEnvironment, stem: Int): OrtSession {
        sessions[stem]?.let { return it }
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(threads)
        val tLoad = System.nanoTime()
        val session = try {
            env.createSession(modelBytes(Stem.NAMES[stem] + ".fp16.onnx"), options)
        } catch (e: Throwable) {
            options.close()
            throw e
        }
        val ms = (System.nanoTime() - tLoad) / 1_000_000
        loadMs += ms
        loadByStemMs[stem] += ms
        sessionsCreated++
        sessions[stem] = session
        sessionOptions[stem] = options
        return session
    }

    private fun releaseSession(stem: Int) {
        sessions[stem]?.close()
        sessionOptions[stem]?.close()
        sessions[stem] = null
        sessionOptions[stem] = null
    }

    /** 0.0.129: closes the sessions that [keepSessions] kept. Safe to call more than once. */
    fun close() {
        for (stem in 0 until Stem.COUNT) releaseSession(stem)
    }

    private val fft = Fft(N_FFT)
    private val window = DoubleArray(N_FFT) { 0.5 - 0.5 * cos(2.0 * PI * it / N_FFT) }

    /** One separated stretch of music: keeps the spectrum and the four estimates, to be mixed with [render]. */
    class Separated(
        val samples: Int,
        val frames: Int,
        // Per channel, per frame, per bin: real and imaginary part
        val spectrum: Array<FloatArray>,
        // Per stem: [channel][frame][bin] flattened, with a frame stride of [stride]
        val estimates: Array<FloatArray>,
        val stride: Int
    )

    /** Separates [left] and [right] (44.1 kHz). [progress] is called with 0 to 1. With [only] set, just that stem is estimated (a quarter of the work, but then [energyPerFrame] and [render] cannot be used; see [shareOf]). */
    fun separate(left: FloatArray, right: FloatArray, progress: (Float) -> Unit = {}, only: Int = -1): Separated {
        val samples = left.size
        audioSec += samples.toDouble() / SAMPLE_RATE
        val n = ceil((PAD + samples).toDouble() / HOP).toInt()
        val frames = n
        val splits = ceil(frames.toDouble() / FRAMES_PER_SPLIT).toInt()
        val stride = splits * FRAMES_PER_SPLIT
        val spectrum = arrayOf(FloatArray(frames * BINS * 2), FloatArray(frames * BINS * 2))
        val magnitudes = FloatArray(2 * stride * MODEL_BINS)

        val tFft = System.nanoTime()
        if (NATIVE_DSP) {
            NativeStemDsp.stft(left, right, frames, stride, spectrum[0], spectrum[1], magnitudes, DSP_THREADS)
        } else {
            // Forward transform; both channels in one complex transform
            val re = DoubleArray(N_FFT)
            val im = DoubleArray(N_FFT)
            for (t in 0 until frames) {
                val from = t * HOP - PAD
                for (i in 0 until N_FFT) {
                    val s = from + i
                    val l = if (s in 0 until samples) left[s].toDouble() else 0.0
                    val r = if (s in 0 until samples) right[s].toDouble() else 0.0
                    re[i] = l * window[i]
                    im[i] = r * window[i]
                }
                fft.transform(re, im, false)
                for (k in 0 until BINS) {
                    val k2 = (N_FFT - k) % N_FFT
                    val lr = (re[k] + re[k2]) / 2.0
                    val li = (im[k] - im[k2]) / 2.0
                    val rr = (im[k] + im[k2]) / 2.0
                    val ri = -(re[k] - re[k2]) / 2.0
                    val at = (t * BINS + k) * 2
                    spectrum[0][at] = lr.toFloat()
                    spectrum[0][at + 1] = li.toFloat()
                    spectrum[1][at] = rr.toFloat()
                    spectrum[1][at + 1] = ri.toFloat()
                    if (k < MODEL_BINS) {
                        magnitudes[(0 * stride + t) * MODEL_BINS + k] = sqrt(lr * lr + li * li).toFloat()
                        magnitudes[(1 * stride + t) * MODEL_BINS + k] = sqrt(rr * rr + ri * ri).toFloat()
                    }
                }
            }
        }

        fftMs += (System.nanoTime() - tFft) / 1_000_000
        // The model, one stem after the other (only one is in memory at a time), one split at a time
        val env = OrtEnvironment.getEnvironment()
        val estimates = Array(Stem.COUNT) { FloatArray(if (only < 0 || it == only) 2 * stride * MODEL_BINS else 0) }
        val input = ByteBuffer.allocateDirect(2 * FRAMES_PER_SPLIT * MODEL_BINS * 4).order(ByteOrder.nativeOrder())
        val inputFloats = input.asFloatBuffer()
        for (stem in 0 until Stem.COUNT) {
            if (only >= 0 && stem != only) continue
            val session = sessionFor(env, stem)
            val tRun = System.nanoTime()
            try {
                for (split in 0 until splits) {
                    inputFloats.clear()
                    for (c in 0 until 2) {
                        inputFloats.put(magnitudes, (c * stride + split * FRAMES_PER_SPLIT) * MODEL_BINS, FRAMES_PER_SPLIT * MODEL_BINS)
                    }
                    inputFloats.rewind()
                    val tensor = OnnxTensor.createTensor(
                        env, inputFloats, longArrayOf(2, 1, FRAMES_PER_SPLIT.toLong(), MODEL_BINS.toLong())
                    )
                    tensor.use {
                        session.run(mapOf("x" to tensor)).use { result ->
                            val out = (result.get(0) as OnnxTensor).floatBuffer
                            for (c in 0 until 2) {
                                out.position(c * FRAMES_PER_SPLIT * MODEL_BINS)
                                out.get(estimates[stem], (c * stride + split * FRAMES_PER_SPLIT) * MODEL_BINS, FRAMES_PER_SPLIT * MODEL_BINS)
                            }
                        }
                    }
                    splitsRun++
                    progress((stem * splits + split + 1).toFloat() / (Stem.COUNT * splits))
                }
            } finally {
                val ran = (System.nanoTime() - tRun) / 1_000_000
                runMs += ran
                runByStemMs[stem] += ran
                if (!keepSessions) releaseSession(stem)
            }
        }
        return Separated(samples, frames, spectrum, estimates, stride)
    }

    /**
     * For a separation made with [Separated] `only` = [stem]: per frame, the share of the sound that the estimate of that
     * stem makes up (its squared estimate over the squared magnitude of the music, over the bins of the model, 0 to 1).
     */
    fun shareOf(sep: Separated, stem: Int): FloatArray {
        val out = FloatArray(sep.frames)
        for (t in 0 until sep.frames) {
            var est = 0.0
            var all = 0.0
            for (c in 0 until 2) {
                for (k in 0 until MODEL_BINS) {
                    val e = finite(sep.estimates[stem][(c * sep.stride + t) * MODEL_BINS + k])
                    val at = (t * BINS + k) * 2
                    val re = sep.spectrum[c][at]
                    val im = sep.spectrum[c][at + 1]
                    est += e * e
                    all += re * re + im * im
                }
            }
            out[t] = if (all <= 1e-9) 0f else minOf(1.0, est / all).toFloat()
        }
        return out
    }

    /** The energy of one stem per frame (the sum over the bins of the masked magnitude, squared). */
    fun energyPerFrame(sep: Separated, stem: Int): FloatArray {
        if (NATIVE_DSP) {
            val tEnergy = System.nanoTime()
            val energy = FloatArray(sep.frames)
            NativeStemDsp.energy(sep.spectrum[0], sep.spectrum[1], sep.estimates, sep.frames, sep.stride, stem, energy, DSP_THREADS)
            energyMs += (System.nanoTime() - tEnergy) / 1_000_000
            return energy
        }
        val out = FloatArray(sep.frames)
        val mask = FloatArray(Stem.COUNT)
        for (t in 0 until sep.frames) {
            var sum = 0.0
            for (c in 0 until 2) {
                for (k in 0 until MODEL_BINS) {
                    maskAt(sep, c, t, k, mask)
                    val at = (t * BINS + k) * 2
                    val re = sep.spectrum[c][at]
                    val im = sep.spectrum[c][at + 1]
                    sum += mask[stem] * mask[stem] * (re * re + im * im)
                }
            }
            out[t] = sum.toFloat()
        }
        return out
    }

    /** The centre time (seconds from the start of the stretch) of frame [t]. */
    fun frameTime(t: Int): Double = (t * HOP - PAD + N_FFT / 2).toDouble() / SAMPLE_RATE

    private fun finite(x: Float): Float = if (x.isNaN() || x.isInfinite()) 0f else x

    private fun maskAt(sep: Separated, c: Int, t: Int, k: Int, out: FloatArray) {
        // The model can answer with NaN on a stretch of digital silence: that counts as 0 (the masks then stay 1 / 4)
        var total = EPS
        for (s in 0 until Stem.COUNT) {
            val e = finite(sep.estimates[s][(c * sep.stride + t) * MODEL_BINS + k])
            total += e * e
        }
        for (s in 0 until Stem.COUNT) {
            val e = finite(sep.estimates[s][(c * sep.stride + t) * MODEL_BINS + k])
            out[s] = (e * e + EPS / Stem.COUNT) / total
        }
    }

    /**
     * The stretch with every stem at its own volume: [gain] gives the volume (0 to 1) of a stem at a time in seconds.
     * With all gains 1 this is the original. Returns left and right, [Separated.samples] long.
     */
    fun render(sep: Separated, gain: (stem: Int, seconds: Double) -> Float): Array<FloatArray> {
        val tRender = System.nanoTime()
        if (NATIVE_DSP) {
            val gains = FloatArray(sep.frames * Stem.COUNT)
            for (t in 0 until sep.frames) {
                val seconds = frameTime(t)
                for (s in 0 until Stem.COUNT) gains[t * Stem.COUNT + s] = gain(s, seconds)
            }
            val left = FloatArray(sep.samples)
            val right = FloatArray(sep.samples)
            NativeStemDsp.render(sep.spectrum[0], sep.spectrum[1], sep.estimates, sep.frames, sep.stride, gains, left, right, DSP_THREADS)
            renderMs += (System.nanoTime() - tRender) / 1_000_000
            return arrayOf(left, right)
        }
        val out = arrayOf(DoubleArray((sep.frames - 1) * HOP + N_FFT), DoubleArray((sep.frames - 1) * HOP + N_FFT))
        val weights = DoubleArray(out[0].size)
        val re = DoubleArray(N_FFT)
        val im = DoubleArray(N_FFT)
        val mask = FloatArray(Stem.COUNT)
        val tail = Array(2) { FloatArray(Stem.COUNT) }
        val g = FloatArray(Stem.COUNT)
        val mixed = Array(2) { FloatArray(BINS) }
        for (t in 0 until sep.frames) {
            val seconds = frameTime(t)
            for (s in 0 until Stem.COUNT) g[s] = gain(s, seconds)
            for (c in 0 until 2) {
                java.util.Arrays.fill(tail[c], 0f)
                for (k in 0 until MODEL_BINS) {
                    maskAt(sep, c, t, k, mask)
                    var m = 0f
                    for (s in 0 until Stem.COUNT) {
                        m += g[s] * mask[s]
                        tail[c][s] += mask[s]
                    }
                    mixed[c][k] = m
                }
                var m = 0f
                for (s in 0 until Stem.COUNT) m += g[s] * tail[c][s] / MODEL_BINS
                for (k in MODEL_BINS until BINS) mixed[c][k] = m
            }
            // Back: both channels in one complex transform (left plus i times right)
            for (k in 0 until BINS) {
                val at = (t * BINS + k) * 2
                val lr = sep.spectrum[0][at] * mixed[0][k].toDouble()
                val li = sep.spectrum[0][at + 1] * mixed[0][k].toDouble()
                val rr = sep.spectrum[1][at] * mixed[1][k].toDouble()
                val ri = sep.spectrum[1][at + 1] * mixed[1][k].toDouble()
                // C = L + i R
                re[k] = lr - ri
                im[k] = li + rr
                if (k in 1 until N_FFT / 2) {
                    val k2 = N_FFT - k
                    // L(N-k) = conj(L(k)), R(N-k) = conj(R(k))
                    re[k2] = lr + ri
                    im[k2] = -li + rr
                }
            }
            fft.transform(re, im, true)
            val from = t * HOP
            for (i in 0 until N_FFT) {
                out[0][from + i] += re[i] * window[i]
                out[1][from + i] += im[i] * window[i]
                weights[from + i] += window[i] * window[i]
            }
        }
        val result = Array(2) { c ->
            FloatArray(sep.samples) { i ->
                val w = weights[PAD + i]
                if (w > 1e-8) (out[c][PAD + i] / w).toFloat() else 0f
            }
        }
        renderMs += (System.nanoTime() - tRender) / 1_000_000
        return result
    }
}
