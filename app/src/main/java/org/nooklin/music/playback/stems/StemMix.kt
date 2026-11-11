package org.nooklin.music.playback.stems

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/** Stereo audio at 44.1 kHz that starts at [startMs] in its song. */
class Pcm(val left: FloatArray, val right: FloatArray, val startMs: Long)

/** The change from song A to song B as the beat plan has it (times in the songs), and the volume of each song. */
class StemClipSpec(
    val startA: Long,
    val fadeAMs: Long,
    val ratio: Double,
    val startB: Long,
    val factorA: Float,
    val factorB: Float,
    /** Where in the overlap (0 to 1) the bass and the drums change over */
    val bassSwap: Float,
    /** The length of the overlap in beats (0 when not known); the overlap starts on a bar line */
    val beats: Int = 0
)

/**
 * Builds the overlap of two songs from their stems, so that melodies do not run through each other. The result is one
 * stretch of audio at the tempo of song B (it plays at the normal speed, nothing is stretched while it plays): first
 * song A as it is for [PLAIN_MS], then [BEND_MS] in which the tempo of song A glides to that of song B (it is read
 * faster or slower, like a pitch fader; the pitch follows), then the overlap, then a short tail of song B alone.
 * The order, in parts of the overlap (p from 0 to 1):
 *  - the old song loses its melody (the rest) first, over about [VOCAL_RAMP_MS], at the quietest moment of its vocals;
 *  - the vocals of the old song stop with a short fade at the end of a phrase ([VocalGate]: the pause in its vocals
 *    nearest to the start of the overlap, else a bar line);
 *  - the melody of the new song comes in during the fade of the old melody; its vocals stay silent until the vocals of
 *    the old song are gone and the next phrase of the new song begins, so two singers are never heard together;
 *  - at the bar line [StemClipSpec.bassSwap] the bass changes over and the drums of the old song go and those of the
 *    new song come in.
 */
object StemMix {
    /** Where in the clip the old song moves over to the second player (the clip is song A as it is up to PLAIN_MS) */
    const val HANDOVER_AT_MS = 1_000L
    const val PLAIN_MS = 4_000L
    const val BEND_MS = 4_000L
    // Song B alone after the overlap: the next song takes over from the clip in here, as soon as it is in step
    const val TAIL_MS = 3_000L
    private const val A_TAIL_MS = 600L
    const val MARGIN_MS = 1_000L
    const val VOCAL_RAMP_MS = 2_500.0
    private const val SR = StemSeparator.SAMPLE_RATE
    private const val BASS_RAMP = 0.0625
    private const val DRUMS_IN = 0.1
    private const val DRUMS_OUT = 0.15

    /** Milliseconds in the clip (and in song B) at which the overlap begins. */
    const val OVERLAP_AT_MS = PLAIN_MS + BEND_MS

    /** Milliseconds of song A from the start of the clip to the start of the overlap. */
    private fun leadAMs(ratio: Double): Double = PLAIN_MS + BEND_MS * (1.0 + (ratio - 1.0) * 0.5)

    /** Where in song A the clip begins (milliseconds in song A); negative when song A is too short for it. */
    fun baseMs(spec: StemClipSpec): Long = spec.startA - Math.round(leadAMs(spec.ratio))

    /** How long the overlap takes in the clip: song A is read at [StemClipSpec.ratio], song B at its own speed. */
    fun fadeWallMs(spec: StemClipSpec): Long = Math.round(spec.fadeAMs / spec.ratio)

    /** The part of song A to read (from, to; milliseconds in song A) */
    fun rangeA(spec: StemClipSpec): Pair<Long, Long> =
        Pair(max(0L, baseMs(spec) - 50L), spec.startA + spec.fadeAMs + A_TAIL_MS + MARGIN_MS)

    /** The part of song B to read (from, to; milliseconds in song B) */
    fun rangeB(spec: StemClipSpec): Pair<Long, Long> {
        val fadeB = fadeWallMs(spec)
        return Pair(max(0L, spec.startB - MARGIN_MS), spec.startB + fadeB + TAIL_MS + MARGIN_MS)
    }

    /**
     * How far (in seconds of song A) the music of A still is from the start of the overlap, at [c] seconds in the clip
     * (c up to the start of the overlap). The tempo of A glides with a smooth step from 1 to [ratio] during the bend.
     */
    private fun distanceToOverlap(c: Double, ratio: Double): Double {
        val c1 = PLAIN_MS / 1000.0
        val bend = BEND_MS / 1000.0
        val c2 = c1 + bend
        // The integral of the smooth step 3u^2 - 2u^3 is u^3 - u^4 / 2 and is 0.5 at u = 1
        val whole = bend * (1.0 + (ratio - 1.0) * 0.5)
        return if (c <= c1) {
            whole + (c1 - c)
        } else {
            val u = ((c - c1) / bend).coerceIn(0.0, 1.0)
            (c2 - c) + (ratio - 1.0) * bend * (0.5 - (u * u * u - u * u * u * u / 2.0))
        }
    }

    private fun ramp(p: Double, from: Double, to: Double): Double = ((p - from) / (to - from)).coerceIn(0.0, 1.0)

    private fun down(p: Double, from: Double, to: Double): Float = cos(ramp(p, from, to) * PI / 2.0).toFloat()

    private fun up(p: Double, from: Double, to: Double): Float = sin(ramp(p, from, to) * PI / 2.0).toFloat()

    /** The part of the overlap that the vocals of the old song take to go out. */
    private fun rampOf(fadeWallSec: Double): Double = (VOCAL_RAMP_MS / 1000.0 / fadeWallSec).coerceIn(0.125, 0.3)

    private fun gainA(
        stem: Int, p: Double, out: Double, r: Double, swap: Double, vocalOut: Double, vocalLen: Double
    ): Float = when (stem) {
        Stem.VOCALS -> down(p, vocalOut, vocalOut + vocalLen)
        Stem.OTHER -> down(p, out, out + r)
        Stem.BASS -> down(p, swap, swap + BASS_RAMP)
        else -> down(p, swap, swap + DRUMS_OUT)
    }

    private fun gainB(
        stem: Int, p: Double, out: Double, r: Double, swap: Double, vocalIn: Double, vocalLen: Double
    ): Float = when (stem) {
        Stem.OTHER -> up(p, out + 0.25 * r, out + 1.25 * r)
        Stem.VOCALS -> up(p, vocalIn, vocalIn + vocalLen)
        Stem.BASS -> up(p, swap, swap + BASS_RAMP)
        else -> up(p, swap, swap + DRUMS_IN)
    }

    /**
     * Where the melody (the rest) of the old song stops: of a few places in the first quarter of the overlap, the
     * earliest where its vocals are quiet for the next stretch (under 30% of their mean), else the quietest one.
     * The vocals themselves stop at a phrase end, see [VocalGate].
     */
    private fun chooseOut(
        separator: StemSeparator, sep: StemSeparator.Separated, marginSec: Double, fadeSec: Double, r: Double
    ): Double {
        val energy = separator.energyPerFrame(sep, Stem.VOCALS)
        fun mean(from: Double, to: Double): Double {
            var sum = 0.0
            var n = 0
            for (t in energy.indices) {
                val p = (separator.frameTime(t) - marginSec) / fadeSec
                if (p >= from && p < to) {
                    sum += energy[t]
                    n++
                }
            }
            return if (n == 0) 0.0 else sum / n
        }
        val base = mean(0.0, 0.5)
        if (base <= 1e-9) return 0.125
        var quietest = 0.125
        var quietestMean = Double.MAX_VALUE
        for (f in doubleArrayOf(0.0625, 0.125, 0.1875, 0.25)) {
            val m = mean(f, f + r)
            if (m < 0.3 * base) return f
            if (m < quietestMean) {
                quietestMean = m
                quietest = f
            }
        }
        return quietest
    }

    private fun slice(a: FloatArray, from: Int): FloatArray = a.copyOfRange(min(from, a.size), a.size)

    private fun sample(a: FloatArray, pos: Double): Float {
        val i0 = floor(pos).toInt()
        if (i0 < 0 || i0 + 1 >= a.size) return 0f
        val frac = (pos - i0).toFloat()
        return a[i0] * (1f - frac) + a[i0 + 1] * frac
    }

    /** The clip (left, right) at 44.1 kHz, or null when the audio does not cover the overlap. */
    fun mix(
        separator: StemSeparator,
        a: Pcm,
        b: Pcm,
        spec: StemClipSpec,
        progress: (String) -> Unit = {}
    ): Array<FloatArray>? {
        val ratio = spec.ratio
        val fadeASec = spec.fadeAMs / 1000.0
        val fadeWallSec = fadeASec / ratio
        // The overlap begins at this sample of the clip, and at this sample of the audio of song A
        val overlapAt = (OVERLAP_AT_MS * SR / 1000.0).roundToInt()
        val overlapSamples = (fadeWallSec * SR).roundToInt()
        val tailSamples = (TAIL_MS * SR / 1000.0).roundToInt()
        val total = overlapAt + overlapSamples + tailSamples
        val leadIn = ((spec.startA - a.startMs) * SR / 1000.0).roundToInt()
        val aNeeded = leadIn + (fadeASec * SR).roundToInt() + 2
        val leadMax = leadAMs(ratio) * SR / 1000.0
        if (leadIn < leadMax - 2 || a.left.size < aNeeded) {
            progress("audio of A too short: ${a.left.size} < $aNeeded, lead-in $leadIn < ${leadMax.toInt()}")
            return null
        }
        val marginSamples = (MARGIN_MS * SR / 1000.0).roundToInt()
        val sepStartA = max(0, leadIn - marginSamples)
        val marginASec = (leadIn - sepStartA).toDouble() / SR

        val r = rampOf(fadeWallSec)
        val swap = spec.bassSwap.toDouble()
        val sepA = separator.separate(slice(a.left, sepStartA), slice(a.right, sepStartA))
        val out = chooseOut(separator, sepA, marginASec, fadeASec, r)
        // The vocals of A: a short fade at the end of a phrase (the melody keeps the slower fade chosen above)
        val vocalOut = VocalGate.chooseOut(
            separator.energyPerFrame(sepA, Stem.VOCALS), { t -> separator.frameTime(t) - marginASec }, fadeASec, spec.beats
        )
        val vocalOutP = vocalOut.sec / fadeASec
        val vocalOutLenP = VocalGate.A_FADE_SEC / fadeASec
        progress(
            "A separated, its melody goes out at ${"%.4f".format(out)} over ${"%.3f".format(r)}, " +
                "its vocals at ${"%.4f".format(vocalOutP)} (${vocalOut.how}) over ${"%.3f".format(vocalOutLenP)}"
        )
        val renderA = separator.render(sepA) { stem, sec ->
            gainA(stem, (sec - marginASec) / fadeASec, out, r, swap, vocalOutP, vocalOutLenP)
        }

        val marginBSec = (spec.startB - b.startMs) / 1000.0
        if (marginBSec < 0.0) return null
        val sepB = separator.separate(b.left.copyOf(), b.right.copyOf())
        // The vocals of B come in at the first phrase start after the vocals of A are gone
        val vocalIn = VocalGate.chooseIn(
            separator.energyPerFrame(sepB, Stem.VOCALS), { t -> separator.frameTime(t) - marginBSec }, fadeWallSec,
            vocalOutP + vocalOutLenP, spec.beats
        )
        val vocalInP = vocalIn.sec / fadeWallSec
        val vocalInLenP = vocalIn.fade / fadeWallSec
        val renderB = separator.render(sepB) { stem, sec ->
            gainB(stem, (sec - marginBSec) / fadeWallSec, out, r, swap, vocalInP, vocalInLenP)
        }
        progress("B separated, its vocals come in at ${"%.4f".format(vocalInP)} (${vocalIn.how})")

        val left = FloatArray(total)
        val right = FloatArray(total)
        val fa = spec.factorA
        val fb = spec.factorB
        val bStart = marginBSec * SR
        for (j in 0 until total) {
            var l = 0f
            var rr = 0f
            if (j < overlapAt) {
                // Song A as it is, read at a speed that glides from 1 to the ratio
                val pos = leadIn - distanceToOverlap(j.toDouble() / SR, ratio) * SR
                l = sample(a.left, pos) * fa
                rr = sample(a.right, pos) * fa
            } else {
                // Song A with its stems, at the ratio
                val pos = leadIn + (j - overlapAt) * ratio - sepStartA
                l = sample(renderA[0], pos) * fa
                rr = sample(renderA[1], pos) * fa
                // Song B at its own speed
                val ib = (bStart + (j - overlapAt)).roundToInt()
                if (ib >= 0 && ib < renderB[0].size) {
                    l += renderB[0][ib] * fb
                    rr += renderB[1][ib] * fb
                }
            }
            left[j] = softLimit(l)
            right[j] = softLimit(rr)
        }
        return arrayOf(left, right)
    }

    /** Leaves everything under 0.9 alone and rounds off what is above it, so that the sum of two songs does not clip. */
    private fun softLimit(x: Float): Float {
        val m = abs(x)
        if (m <= 0.9f) return x
        val v = 0.9f + 0.1f * tanh((m - 0.9f) / 0.1f)
        return if (x < 0f) -v else v
    }

    /** A 16 bit stereo WAV file of the clip. */
    fun wavBytes(left: FloatArray, right: FloatArray): ByteArray {
        val n = left.size
        val data = n * 4
        val b = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(SR).putInt(SR * 4).putShort(4).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (i in 0 until n) {
            b.putShort((left[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
            b.putShort((right[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
        }
        return b.array()
    }
}
