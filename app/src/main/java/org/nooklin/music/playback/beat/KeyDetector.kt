package org.nooklin.music.playback.beat

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.nooklin.music.playback.stems.Fft

/**
 * Finds the key of a song from the notes in it: every third block of 16384 samples is turned into a spectrum, the
 * strength of each of the 12 notes is added up (the octave does not matter), and the result is compared with the
 * typical shape of a major and of a minor key (Krumhansl and Kessler). Only 12 numbers are kept, never the audio.
 * A key is a number: 0 to 11 is major with that note as the first note (0 is C), 12 to 23 is minor (12 is C minor).
 */
class KeyDetector(private val sampleRate: Int) {
    private val fft = Fft(SIZE)
    private val window = DoubleArray(SIZE) { 0.5 - 0.5 * cos(2.0 * PI * it / SIZE) }
    private val re = DoubleArray(SIZE)
    private val im = DoubleArray(SIZE)
    private val block = DoubleArray(SIZE)
    private var filled = 0
    private var blockNumber = 0
    private val chroma = DoubleArray(12)
    private var used = 0

    // The note of each bin in the range 100 Hz to 2 kHz, or -1
    private val noteOfBin = IntArray(SIZE / 2) { k ->
        val hz = k.toDouble() * sampleRate / SIZE
        if (hz < 100.0 || hz > 2000.0) -1 else Math.floorMod((69.0 + 12.0 * ln(hz / 440.0) / ln(2.0)).roundToInt(), 12)
    }

    fun add(x: Float) {
        if (blockNumber % 3 == 0) block[filled] = x.toDouble()
        if (++filled == SIZE) {
            if (blockNumber % 3 == 0) analyse()
            blockNumber++
            filled = 0
        }
    }

    private fun analyse() {
        var energy = 0.0
        for (i in 0 until SIZE) {
            re[i] = block[i] * window[i]
            im[i] = 0.0
            energy += block[i] * block[i]
        }
        // Silence says nothing about the key
        if (energy / SIZE < 1e-8) return
        fft.transform(re, im, false)
        val frame = DoubleArray(12)
        var sum = 0.0
        for (k in 1 until SIZE / 2) {
            val note = noteOfBin[k]
            if (note < 0) continue
            val magnitude = sqrt(sqrt(re[k] * re[k] + im[k] * im[k]))
            frame[note] += magnitude
            sum += magnitude
        }
        if (sum <= 0.0) return
        // Every block counts the same, loud or not
        for (i in 0 until 12) chroma[i] += frame[i] / sum
        used++
    }

    /** What the last call of [key] found, as a line for the log: the best keys and how well they fit. */
    var report: String = ""
        private set

    /**
     * The correlation of the notes of this song with each of the 24 keys (0 to 11 major, 12 to 23 minor). With a third
     * profile ([other]) a key that fits that one counts for the minor key of the same note, as the profile set says.
     */
    private fun fits(x: DoubleArray, xn: Double, major: DoubleArray, minor: DoubleArray, other: DoubleArray?): DoubleArray {
        val out = DoubleArray(24) { -2.0 }
        val profiles = if (other == null) listOf(major, minor) else listOf(major, minor, other)
        for ((index, profile) in profiles.withIndex()) {
            val pm = profile.average()
            val pn = sqrt(profile.sumOf { (it - pm) * (it - pm) })
            for (tonic in 0 until 12) {
                var dot = 0.0
                for (i in 0 until 12) dot += x[(tonic + i) % 12] * (profile[i] - pm)
                val r = dot / (xn * pn)
                val at = (if (index == 0) 0 else 12) + tonic
                if (r > out[at]) out[at] = r
            }
        }
        return out
    }

    /** The best key and the next best of the 24 fits: (key, fit, second key, second fit). */
    private fun top(fit: DoubleArray): DoubleArray {
        var best = 0
        for (i in 1 until 24) if (fit[i] > fit[best]) best = i
        var second = if (best == 0) 1 else 0
        for (i in 0 until 24) if (i != best && fit[i] > fit[second]) second = i
        return doubleArrayOf(best.toDouble(), fit[best], second.toDouble(), fit[second])
    }

    /** The key, or -1 when there is not enough music or no key stands out. [report] says what was found. */
    fun key(): Int {
        if (used < MIN_BLOCKS) {
            report = "only $used blocks of $MIN_BLOCKS"
            return -1
        }
        val mean = chroma.average()
        val x = DoubleArray(12) { chroma[it] - mean }
        val xn = sqrt(x.sumOf { it * it })
        if (xn <= 1e-12) {
            report = "no notes"
            return -1
        }
        // The profiles of electronic dance music decide; those of Krumhansl are only reported, to compare
        val edm = top(fits(x, xn, MAJOR, MINOR, OTHER))
        val krumhansl = top(fits(x, xn, KRUMHANSL_MAJOR, KRUMHANSL_MINOR, null))
        val best = edm[0].toInt()
        val fit = edm[1]
        val margin = edm[1] - edm[3]
        report = "edm ${name(best)} fit ${"%.2f".format(fit)} margin ${"%.2f".format(margin)} next ${name(edm[2].toInt())}, " +
            "krumhansl ${name(krumhansl[0].toInt())} fit ${"%.2f".format(krumhansl[1])} margin ${"%.2f".format(krumhansl[1] - krumhansl[3])}, " +
            "$used blocks"
        // A key must fit well and clearly better than the next one
        return if (fit >= MIN_FIT && margin >= MIN_MARGIN) best else -1
    }

    companion object {
        private const val SIZE = 16384
        private const val MIN_BLOCKS = 12
        private const val MIN_FIT = 0.35
        private const val MIN_MARGIN = 0.05
        // Profiles from a study of electronic dance music, as the Essentia key algorithm has them ("edma"): major, minor,
        // and one for tracks that fit neither (reported as the minor key of that note)
        private val MAJOR = doubleArrayOf(1.00, 0.29, 0.50, 0.40, 0.60, 0.56, 0.32, 0.80, 0.31, 0.45, 0.42, 0.39)
        private val MINOR = doubleArrayOf(1.00, 0.31, 0.44, 0.58, 0.33, 0.49, 0.29, 0.78, 0.43, 0.29, 0.53, 0.32)
        private val OTHER = doubleArrayOf(1.00, 0.26, 0.35, 0.29, 0.44, 0.36, 0.21, 0.78, 0.26, 0.25, 0.32, 0.26)

        // The profiles of Krumhansl and Kessler (what was used before), kept to compare in the log
        private val KRUMHANSL_MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
        private val KRUMHANSL_MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

        /**
         * How far two keys are from each other on the circle of fifths (0 to 6), where a major key and its relative
         * minor count as one place. 0 is the same key or the relative key, 1 is a fifth away, 6 is the farthest.
         */
        fun distance(a: Int, b: Int): Int {
            if (a < 0 || b < 0) return 0
            val da = place(a)
            val db = place(b)
            val d = abs(da - db)
            return minOf(d, 12 - d)
        }

        private fun place(key: Int): Int {
            val tonic = key % 12
            // The relative major of a minor key lies three notes higher
            val major = if (key >= 12) (tonic + 3) % 12 else tonic
            return (major * 7) % 12
        }

        /** The key moved up by [semitones] (may be negative); an unknown key stays unknown. */
        fun shifted(key: Int, semitones: Int): Int =
            if (key < 0) -1 else (key / 12) * 12 + Math.floorMod(key % 12 + semitones, 12)

        fun name(key: Int): String {
            if (key < 0) return "unknown"
            val notes = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            return notes[key % 12] + if (key >= 12) " minor" else " major"
        }
    }
}
