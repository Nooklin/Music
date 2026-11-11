package org.nooklin.music.playback.beat

import android.util.Log
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln

/**
 * How one change from song A to song B goes. Times are in the songs. Song A is sped up or slowed down by [ratio] so
 * that its tempo equals that of song B, [fadeMs] of song A (in song time) are used for the overlap, which starts at
 * [startA] in A (a bar line) and at [startB] in B (a bar line, the place chosen to come in). [startA] is not always
 * near the end of A: the rest of A is skipped when A is left at the end of a loud section.
 */
class BeatPlan(
    val startA: Long,
    val fadeMs: Long,
    val ratio: Double,
    val startB: Long,
    /** True: a cut on the bar line (the tempos are too far apart to match): a short change without matching */
    val cut: Boolean = false,
    /** Where in the overlap (0 to 1, on a bar line) the melody changes over: when the melody of A has stopped */
    val swap: Float = 0.25f,
    /** How much the melodies of A and B run through each other in the overlap, 0 to 1; negative when not known */
    val clash: Double = -1.0,
    /** The length of the overlap in beats (8 or 16); 0 for a cut */
    val beats: Int = 0,
    /** Where in the overlap (0 to 1, on a bar line) the bass and the drums change over */
    val bassSwap: Float = 0.75f,
    /**
     * True (a cut only): the old song goes into an echo one beat before the bar line, at the bar line its own sound
     * stops and only the echo rings on, and the new song comes in at once. [fadeMs] is then how long the echo rings.
     */
    val echo: Boolean = false,
    /** The length of one beat of song A (ms): the delay of the echo */
    val beatMs: Double = 0.0
)

object BeatMixPlanner {
    private const val MAX_WALL_FADE_MS = 24_000.0
    private const val TAIL_MS = 400.0

    // A song may be left from this part of its length on
    private const val MIN_EXIT_FRACTION = 0.4
    private const val TAG = "NooklinMix"

    /**
     * The tempo of the two songs may differ by this much (the old song is sped up or slowed down to match). In the
     * listening test of Ishizaki e.a. (ISMIR 2009) listeners are bothered most at 0.90 and 1.10 and on average only
     * beyond 0.852 and 1.227; slowing down bothers more than speeding up, so the limit is lower on that side.
     */
    private const val MIN_RATIO = 0.88
    private const val MAX_RATIO = 1.15

    /** Two keys this far apart on the circle of fifths (0 to 6) clash: that pair is not mixed, it gets a cut. */
    private const val MAX_KEY_DISTANCE = 4

    /** The leaving places of A (best first), then the natural end of A, against the coming-in places of B. */
    fun plan(
        aEnd: BeatEdge?,
        aExits: List<BeatEdge>,
        entries: List<BeatEdge>,
        durationA: Long,
        durationB: Long,
        leadA: String = "",
        leadB: String = "",
        minStartB: Long = 0L,
        /** More than 0: the overlap should take at least this long (stems); a fast tempo then gets 32 beats */
        minWallMs: Long = 0L,
        /** The overlap may not begin before this place in song A (it is already too close, or behind us) */
        minStartA: Long = 0L,
        /** The key of A (where it is left) and of B, see KeyDetector; -1 when not known */
        keyA: Int = -1,
        keyB: Int = -1
    ): BeatPlan? {
        if (durationA <= 0L || durationB <= 0L) return null
        val ways = aExits.map { it to true } + listOfNotNull(aEnd?.let { it to false })
        // All pairs that fit are judged on how much the melodies run through each other; the order (exits first,
        // later in the song first, the entries in their order) only decides between pairs that are about equal
        var best: BeatPlan? = null
        var bestScore = Double.MAX_VALUE
        var order = 0
        var judged = 0
        var keyRejected = 0
        var chosenDistance = 0
        for ((a, isExit) in ways) {
            for (entry in entries) {
                // A coming-in place right after the start of the next song leaves no room to start that song early
                if (entry.anchorMs < minStartB) continue
                val plan = planFor(a, isExit, entry, durationA, durationB, leadA, leadB, minWallMs)
                order++
                if (plan == null || plan.startA < minStartA) continue
                // With stems the pitch of A follows its tempo, so A is in another key by the end of the glide
                val semitones = if (minWallMs > 0L) Math.round(12.0 * ln(plan.ratio) / ln(2.0)).toInt() else 0
                val distance = KeyDetector.distance(KeyDetector.shifted(keyA, semitones), keyB)
                judged++
                if (distance >= MAX_KEY_DISTANCE) {
                    keyRejected++
                    continue
                }
                val score = (if (plan.clash >= 0.0) plan.clash else 0.5) + 0.1 * distance + 0.01 * order
                if (score < bestScore) {
                    bestScore = score
                    chosenDistance = distance
                    best = plan
                }
            }
        }
        val chosen = best
        if (chosen != null) {
        }
        return chosen
    }

    /** The melody level (0 to 1) at [ms] in a song, or -1 when the curve does not reach there. */
    private fun leadAt(lead: String, ms: Double): Double {
        val i = (ms / 1000.0).toInt()
        if (ms < 0.0 || i >= lead.length) return -1.0
        return (lead[i] - '0') / 9.0
    }

    /** The mean of (melody of A x melody of B) over the overlap, in steps of one second; -1 when not known. */
    private fun clashOf(leadA: String, leadB: String, startA: Double, startB: Double, ratio: Double, wallMs: Double): Double {
        if (leadA.isEmpty() || leadB.isEmpty()) return -1.0
        var sum = 0.0
        var n = 0
        var w = 0.0
        while (w < wallMs) {
            val a = leadAt(leadA, startA + w * ratio)
            val b = leadAt(leadB, startB + w)
            if (a >= 0.0 && b >= 0.0) {
                sum += a * b
                n++
            }
            w += 1000.0
        }
        return if (n == 0) -1.0 else sum / n
    }

    /**
     * The bar line (as a part of the overlap) where the melody changes over: the first one, between 15% and 37.5% (50% of a short overlap) of the
     * overlap, from which the melody of A is quiet (level 3 or lower) in the bar that follows. Else about a quarter.
     */
    private fun swapOf(leadA: String, startA: Double, ratio: Double, beats: Int, periodA: Double): Float {
        val bars = beats / 4
        val fallback = maxOf(1, Math.round(0.25 * bars).toInt())
        if (leadA.isEmpty() || bars < 2) return fallback.toFloat() * 4f / beats
        val first = maxOf(1, Math.ceil(0.15 * bars).toInt())
        // The melody changes over before the bass does (at 0.5 of a long overlap, at 0.75 of a short one)
        val last = maxOf(first, Math.floor((if (beats >= 16) 0.375 else 0.5) * bars).toInt())
        for (k in first..last) {
            val from = startA + k * 4.0 * periodA
            val a1 = leadAt(leadA, from + 0.5 * periodA)
            val a2 = leadAt(leadA, from + 2.5 * periodA)
            if (a1 in 0.0..0.34 && a2 in 0.0..0.34) return k * 4f / beats
        }
        return fallback.toFloat() * 4f / beats
    }

    /**
     * A cut on the bar line, for two songs whose tempos are too far apart to match: the old song is left at a bar line
     * (an exit, or its end) and the new song comes in at a bar line of its own. As a DJ does it with the echo: the old
     * song goes into an echo of one beat, stops at the bar line and its last beat rings on for four beats while the new
     * song comes in at full strength. Better than two tempos running through each other for ten seconds.
     */
    fun planCut(
        aEnd: BeatEdge?,
        aExits: List<BeatEdge>,
        entries: List<BeatEdge>,
        durationA: Long,
        durationB: Long,
        /** A coming-in place under this far into the next song is not used (it cannot be prepared) */
        minStartB: Long = 0L,
        /** The cut may not be before this place in song A */
        minStartA: Long = 0L
    ): BeatPlan? {
        if (durationA <= 0L || durationB <= 0L) return null
        val ways = aExits.map { it to true } + listOfNotNull(aEnd?.let { it to false })
        for ((a, isExit) in ways) {
            val periodA = 60000.0 / a.bpm
            // The time the echo rings: four beats
            val fade = (4.0 * periodA).coerceIn(2000.0, 4500.0)
            val startA = if (isExit) {
                a.anchorMs
            } else {
                val bar = 4.0 * periodA
                a.anchorMs + floor((durationA - TAIL_MS - fade - a.anchorMs) / bar) * bar
            }
            if (startA < durationA * 0.5 || startA < minStartA || startA + fade > durationA - TAIL_MS) continue
            for (b in entries) {
                if (b.anchorMs < minStartB || b.anchorMs + fade * 2.0 > durationB - 1000.0) continue
                return BeatPlan(startA.toLong(), fade.toLong(), 1.0, b.anchorMs.toLong(), true, echo = true, beatMs = periodA)
            }
        }
        return null
    }

    private fun planFor(
        a: BeatEdge, isExit: Boolean, b: BeatEdge, durationA: Long, durationB: Long, leadA: String, leadB: String,
        minWallMs: Long = 0L
    ): BeatPlan? {
        // A song can also be counted at half or double speed
        val bpmB = listOf(b.bpm * 0.5, b.bpm, b.bpm * 2.0).minByOrNull { abs(ln(it / a.bpm)) } ?: return null
        val ratio = bpmB / a.bpm
        val periodA = 60000.0 / a.bpm
        val startB = b.anchorMs
        val head = "${if (isExit) "exit" else "end"} ${a.anchorMs.toLong()} ms (${"%.1f".format(a.bpm)}) > " +
            "entry ${startB.toLong()} ms (${"%.1f".format(bpmB)}), ratio=${"%.3f".format(ratio)}: "
        if (ratio < MIN_RATIO || ratio > MAX_RATIO) {
            return null
        }
        if (startB < 0.0) {
            return null
        }

        // 16 beats (four bars), else 8 when the room is short; the swaps fall on bar lines
        val limit = minOf(MAX_WALL_FADE_MS * ratio, durationA / 3.0, (durationB - startB) / 3.0 * ratio)
        // For an exit the length is the longest one that still fits in the rest of A after the exit
        // With stems a fast tempo gets 32 beats, so that the overlap lasts long enough
        val sizes = if (minWallMs > 0L && 16 * periodA / ratio < minWallMs) listOf(32, 16, 8) else listOf(16, 8)
        val fits = sizes.filter { it * periodA <= limit }
        if (fits.isEmpty()) {
            return null
        }
        val beats = if (isExit) {
            fits.firstOrNull { a.anchorMs + it * periodA <= durationA - TAIL_MS }
        } else {
            fits.first()
        }
        if (beats == null) {
            return null
        }
        val fade = beats * periodA

        val startA: Double
        if (isExit) {
            // Leave A at this bar line; the overlap must still fit in A
            startA = a.anchorMs
            if (startA < durationA * MIN_EXIT_FRACTION) {
                return null
            }
        } else {
            // The last bar line of A from which the overlap still fits in A
            val bar = 4.0 * periodA
            val k = floor((durationA - TAIL_MS - fade - a.anchorMs) / bar)
            startA = a.anchorMs + k * bar
            if (startA < durationA / 2.0) {
                return null
            }
        }
        if (startB + fade / ratio > durationB - 1000.0) {
            return null
        }
        val clash = clashOf(leadA, leadB, startA, startB, ratio, fade / ratio)
        val swap = swapOf(leadA, startA, ratio, beats, periodA)
        val bassSwap = if (beats >= 16) 0.5f else 0.75f
        return BeatPlan(startA.toLong(), fade.toLong(), ratio, startB.toLong(), false, swap, clash, beats, bassSwap)
    }
}
