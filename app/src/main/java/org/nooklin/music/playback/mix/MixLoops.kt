package org.nooklin.music.playback.mix

import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The beat loop (0.0.93): when A has no outro to blend over, the last bars of A repeat. The audio is laid over A as an overlay that
 * starts at the loop point and runs past the end of the song until the overlap is over: the same bars again and again, a few
 * thousandths of a second faded in and out at each joint against clicks. The engine plays A as before, it only reads other audio there.
 */
object MixLoops {
    private const val TAG = "NooklinMix"
    private const val RATE = 44100.0
    private const val JOINT_SEC = 0.003
    private const val TAIL_SEC = 0.5

    /** True when this plan is a beat loop. */
    fun needed(plan: DoubleArray) = MixPlan.loop(plan)

    /**
     * Builds and sets the overlay of A for [plan]. Returns false when that did not work or came too late: [positionA] (the place of
     * A in seconds) must still be before the loop point.
     */
    fun build(a: MixLibrary.Song, mapA: DoubleArray, plan: DoubleArray, positionA: () -> Double): Boolean {
        val beats = MixPlan.loopBeats(plan)
        if (beats <= 0) return false
        val loopAt = MixPlan.loopBeat(plan)
        val t1 = MixMap.timeAtBeat(mapA, loopAt)
        val t0 = MixMap.timeAtBeat(mapA, loopAt - beats)
        val end = MixMap.timeAtBeat(mapA, plan[1] + MixPlan.lengthBeats(plan)) + TAIL_SEC
        val loopLen = t1 - t0
        if (loopLen < 0.5 || loopLen > 8.0 || end <= t1) return false
        val total = NativeMix.frames(a.handle)
        val srcStart = (t0 * RATE).roundToInt().toLong()
        val srcFrames = (loopLen * RATE).roundToInt()
        val outStart = (t1 * RATE).roundToInt().toLong()
        val outFrames = min(((end - t1) * RATE).roundToInt().toLong(), 30L * RATE.toLong() - 1L).toInt()
        if (srcStart < 0 || outStart >= total || srcStart + srcFrames > total || srcFrames <= 0 || outFrames <= 0) return false
        val src = NativeMix.copy(a.handle, srcStart, srcFrames) ?: return false
        val joint = max(1, (JOINT_SEC * RATE).roundToInt())
        val out = ShortArray(outFrames * 2)
        for (f in 0 until outFrames) {
            val k = f % srcFrames
            var g = 1.0
            if (k < joint) g = k.toDouble() / joint
            val toEnd = srcFrames - 1 - k
            if (toEnd < joint) g = min(g, toEnd.toDouble() / joint)
            out[2 * f] = (src[2 * k] * g).roundToInt().toShort()
            out[2 * f + 1] = (src[2 * k + 1] * g).roundToInt().toShort()
        }
        if (positionA() > t1 - 0.3) {
            return false
        }
        val ok = NativeMix.setOverlay(a.handle, outStart, out, outFrames)
        return ok
    }
}
