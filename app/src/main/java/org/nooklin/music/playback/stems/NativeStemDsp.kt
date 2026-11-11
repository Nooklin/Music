package org.nooklin.music.playback.stems

import org.nooklin.music.playback.mix.NativeMix

/**
 * 0.0.130: the signal processing around the stem model in native code (app/src/main/cpp/mix/stem_dsp.cpp): the spectrogram, the energy
 * of a stem per frame and the stretch put together again. The same formulas as the Kotlin code in [StemSeparator] that
 * [StemSeparator.NATIVE_DSP] switches off. The library is the one [NativeMix] loads. There is no fallback: when it is not there,
 * the call fails with a message.
 */
object NativeStemDsp {
    @JvmStatic private external fun nativeStft(
        left: FloatArray, right: FloatArray, frames: Int, stride: Int, spec0: FloatArray, spec1: FloatArray, mags: FloatArray, threads: Int
    )

    @JvmStatic private external fun nativeEnergy(
        spec0: FloatArray, spec1: FloatArray, e0: FloatArray, e1: FloatArray, e2: FloatArray, e3: FloatArray,
        frames: Int, stride: Int, stem: Int, out: FloatArray, threads: Int
    )

    @JvmStatic private external fun nativeRender(
        spec0: FloatArray, spec1: FloatArray, e0: FloatArray, e1: FloatArray, e2: FloatArray, e3: FloatArray,
        frames: Int, stride: Int, gains: FloatArray, outL: FloatArray, outR: FloatArray, threads: Int
    )

    private fun ready() {
        check(NativeMix.available) { "The native library did not load; set StemSeparator.NATIVE_DSP to false to use the Kotlin code again" }
    }

    /** Fills [spec0] and [spec1] (frames * 2049 * 2 values each) and [mags] (the first 1024 bins of each frame, 2 * stride * 1024 values). */
    fun stft(left: FloatArray, right: FloatArray, frames: Int, stride: Int, spec0: FloatArray, spec1: FloatArray, mags: FloatArray, threads: Int) {
        ready()
        nativeStft(left, right, frames, stride, spec0, spec1, mags, threads)
    }

    /** The energy of [stem] per frame into [out]; [estimates] has the four stems. */
    fun energy(
        spec0: FloatArray, spec1: FloatArray, estimates: Array<FloatArray>, frames: Int, stride: Int, stem: Int, out: FloatArray, threads: Int
    ) {
        ready()
        nativeEnergy(spec0, spec1, estimates[0], estimates[1], estimates[2], estimates[3], frames, stride, stem, out, threads)
    }

    /** The music with [gains] (4 volumes per frame: frame t, stem s at t * 4 + s) into [outL] and [outR]. */
    fun render(
        spec0: FloatArray, spec1: FloatArray, estimates: Array<FloatArray>, frames: Int, stride: Int, gains: FloatArray,
        outL: FloatArray, outR: FloatArray, threads: Int
    ) {
        ready()
        nativeRender(spec0, spec1, estimates[0], estimates[1], estimates[2], estimates[3], frames, stride, gains, outL, outR, threads)
    }
}
