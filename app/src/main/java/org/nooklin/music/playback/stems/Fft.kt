package org.nooklin.music.playback.stems

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A complex FFT of a fixed size (a power of two), done in place. [inverse] divides by the size. */
class Fft(val size: Int) {
    private val cosTable = DoubleArray(size / 2)
    private val sinTable = DoubleArray(size / 2)
    private val reversed = IntArray(size)

    init {
        require(size >= 2 && size and (size - 1) == 0)
        for (i in 0 until size / 2) {
            cosTable[i] = cos(2.0 * PI * i / size)
            sinTable[i] = sin(2.0 * PI * i / size)
        }
        var bits = 0
        while ((1 shl bits) < size) bits++
        for (i in 0 until size) {
            var r = 0
            var x = i
            for (b in 0 until bits) {
                r = (r shl 1) or (x and 1)
                x = x shr 1
            }
            reversed[i] = r
        }
    }

    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        val sign = if (inverse) 1.0 else -1.0
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sign * sinTable[k]
                    val l = j + half
                    val xr = re[l] * wr - im[l] * wi
                    val xi = re[l] * wi + im[l] * wr
                    re[l] = re[j] - xr
                    im[l] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += step
                }
                start += half * 2
            }
            half *= 2
        }
        if (inverse) {
            val scale = 1.0 / size
            for (i in 0 until size) {
                re[i] *= scale
                im[i] *= scale
            }
        }
    }
}
