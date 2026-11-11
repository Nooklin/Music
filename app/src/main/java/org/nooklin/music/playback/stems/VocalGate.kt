package org.nooklin.music.playback.stems

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Decides when the vocals of the old song (A) stop and when the vocals of the new song (B) start, from the energy of
 * their vocal stems, so that two singers are never heard at the same time:
 *  - the vocals of A go out with a short fade at the pause in its vocals that is nearest to [TARGET] (a phrase ends
 *    there); without such a pause the same short fade is placed on the bar line nearest to [TARGET];
 *  - the vocals of B stay silent until A's vocals are completely gone, then until the first pause in B's vocals and
 *    the start of the phrase after it. When B has no such phrase start in the overlap, its vocals come in at the end of
 *    the overlap at the latest.
 * This is arithmetic on the energy curves only; no audio is touched here. Times are in seconds from the start of the
 * overlap, in the time of the song the curve belongs to.
 */
object VocalGate {
    /** Fade of A's vocals (seconds in song A). */
    const val A_FADE_SEC = 0.3

    /** Fade-in of B's vocals (seconds in song B). */
    const val B_FADE_SEC = 0.15

    /** 0.0.126: fade-in of B's vocals when they come in on a bar line in the middle of a phrase (no pause to enter at). */
    const val B_BAR_FADE_SEC = 0.4

    /** 0.0.126: a phrase start of B later than this part of the overlap is passed over for an earlier bar line where B already sings. */
    private const val LATE_PHRASE = 0.7

    /** Where in the overlap (0 to 1) A's vocals should preferably stop; the nearest pause to this place is used. */
    const val TARGET = 0.125

    /** A's vocals are completely gone by this place in the overlap (0 to 1) at the latest, when a pause allows it. */
    private const val LATEST_OUT = 0.85

    // The fade-in of B begins this long before the detected start of its phrase (the level is smoothed over frames)
    private const val B_LEAD_SEC = 0.10

    // A pause in A's vocals must be at least this long (more than the fade) and in B's vocals at least this long
    private const val A_PAUSE_SEC = 0.4
    private const val B_PAUSE_SEC = 0.3

    // A guard after A's vocals are gone before B's vocals may start
    private const val GATE_GUARD_SEC = 0.02

    // Quiet: under this part of the typical singing level. Loud: over this part, within LOUD_WITHIN_SEC of a phrase start.
    private const val QUIET = 0.04
    private const val LOUD = 0.15
    private const val LOUD_WITHIN_SEC = 0.4

    // The energy is averaged over this many frames each side of a frame
    private const val SMOOTH = 2

    /** Where A's vocals begin to fade (seconds from the start of the overlap, in song A) and how that place was found. */
    class OutPlace(val sec: Double, val how: String)

    /** Where B's vocals begin to fade in (seconds from the start of the overlap, in song B) and how that place was found. */
    class InPlace(val sec: Double, val how: String, val fade: Double = B_FADE_SEC)

    /** The energy per frame, averaged over neighbouring frames. */
    fun smooth(energy: FloatArray): DoubleArray = DoubleArray(energy.size) { t ->
        var sum = 0.0
        var n = 0
        for (i in max(0, t - SMOOTH)..min(energy.size - 1, t + SMOOTH)) {
            sum += energy[i]
            n++
        }
        if (n == 0) 0.0 else sum / n
    }

    /** The typical singing level: the mean of the loudest quarter of the frames. 0 when there is nothing. */
    fun reference(levels: DoubleArray): Double {
        if (levels.isEmpty()) return 0.0
        val sorted = levels.sortedDescending()
        val n = max(1, sorted.size / 4)
        var sum = 0.0
        for (i in 0 until n) sum += sorted[i]
        return sum / n
    }

    /** Runs of consecutive frames under [limit] that are at least [minFrames] long, as (first frame, first frame after). */
    private fun quietRuns(levels: DoubleArray, limit: Double, minFrames: Int): List<IntArray> {
        val runs = ArrayList<IntArray>()
        var t = 0
        while (t < levels.size) {
            if (levels[t] < limit) {
                var e = t
                while (e < levels.size && levels[e] < limit) e++
                if (e - t >= minFrames) runs.add(intArrayOf(t, e))
                t = e
            } else {
                t++
            }
        }
        return runs
    }

    /**
     * Where the vocals of A stop. [energy] is the energy per frame of A's vocal stem, [time] gives the time of a frame in
     * seconds from the start of the overlap (any frame number, also outside the array), [fadeSec] is the length of the
     * overlap in song A, [beats] its length in beats (0 when not known): the overlap starts on a bar line, a bar is 4 beats.
     */
    fun chooseOut(energy: FloatArray, time: (Int) -> Double, fadeSec: Double, beats: Int, targetFrac: Double = TARGET): OutPlace {
        val dt = if (energy.size > 1) time(1) - time(0) else 0.0232
        val levels = smooth(energy)
        val ref = reference(levels)
        val targetSec = targetFrac * fadeSec
        val latestStart = LATEST_OUT * fadeSec - A_FADE_SEC
        var outSec = targetSec
        var how = "target"
        var bestDistance = Double.MAX_VALUE
        if (ref > 1e-9) {
            val minFrames = max(1, Math.ceil(A_PAUSE_SEC / dt).toInt())
            for (run in quietRuns(levels, QUIET * ref, minFrames)) {
                val pauseStart = time(run[0])
                val pauseEnd = time(run[1])
                val from = max(pauseStart, 0.0)
                val to = min(pauseEnd - A_FADE_SEC, latestStart)
                if (to < from) continue
                // The fade may start anywhere from the start of the pause up to [to]; the distance is that of the target
                val distance = when {
                    targetSec < from -> from - targetSec
                    targetSec > to -> targetSec - to
                    else -> 0.0
                }
                if (distance < bestDistance - 1e-9) {
                    bestDistance = distance
                    outSec = from
                    how = "pause"
                }
            }
        }
        if (how != "pause" && beats >= 8) {
            // No pause: a bar line (not the first one, so that A's vocals are heard for at least a bar)
            val bar = 4.0 * fadeSec / beats
            var best = Double.MAX_VALUE
            var k = 1
            while (k * bar <= latestStart + 1e-9) {
                val d = abs(k * bar - targetSec)
                if (d < best - 1e-9) {
                    best = d
                    outSec = k * bar
                    how = "bar"
                }
                k++
            }
        }
        return OutPlace(outSec, how)
    }

    /**
     * Where the vocals of B start: at the first phrase start (the end of a pause, followed by singing) after A's vocals
     * are gone. [goneP] is the place in the overlap (0 to 1) where A's vocals are completely gone; [fadeSec] the length of
     * the overlap in song B. The other parameters are as for [chooseOut].
     */
    fun chooseIn(energy: FloatArray, time: (Int) -> Double, fadeSec: Double, goneP: Double, beats: Int = 0): InPlace {
        val dt = if (energy.size > 1) time(1) - time(0) else 0.0232
        val levels = smooth(energy)
        val ref = reference(levels)
        val gateSec = goneP * fadeSec + GATE_GUARD_SEC
        val latestSec = fadeSec - B_FADE_SEC
        if (ref <= 1e-9) return InPlace(gateSec, "gate")
        // 0.0.126: B sings but has no phrase start in the overlap (it sings right through, log 0.0.125: B's vocals in at 93% of the overlap and
        // 4 s without any singing): the first bar line after A's singing is gone where B already sings, with a longer fade-in
        fun barPlace(): InPlace? {
            if (beats < 8) return null
            val bar = 4.0 * fadeSec / beats
            var k = 1
            while (k * bar <= latestSec - 0.25) {
                val at = k * bar
                if (at >= gateSec) {
                    val idx = Math.round((at - time(0)) / dt).toInt()
                    if (idx in levels.indices && levels[idx] >= LOUD * ref) return InPlace(at, "bar", B_BAR_FADE_SEC)
                }
                k++
            }
            return null
        }
        val minFrames = max(1, Math.ceil(B_PAUSE_SEC / dt).toInt())
        val within = max(1, Math.ceil(LOUD_WITHIN_SEC / dt).toInt())
        for (run in quietRuns(levels, QUIET * ref, minFrames)) {
            val at = run[1]
            if (at >= levels.size) break
            val onset = time(at)
            if (onset < gateSec) continue
            var loud = false
            for (i in at until min(levels.size, at + within)) {
                if (levels[i] >= LOUD * ref) {
                    loud = true
                    break
                }
            }
            if (!loud) continue
            val start = onset - B_LEAD_SEC
            if (start > LATE_PHRASE * fadeSec) barPlace()?.let { return it }
            return if (start <= latestSec) InPlace(max(gateSec, start), "phrase") else InPlace(max(gateSec, latestSec), "latest")
        }
        barPlace()?.let { return it }
        return InPlace(max(gateSec, latestSec), "latest")
    }
}
