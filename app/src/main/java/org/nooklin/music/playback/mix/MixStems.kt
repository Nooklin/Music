package org.nooklin.music.playback.mix

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.nooklin.music.playback.stems.Stem
import org.nooklin.music.playback.stems.StemSeparator
import org.nooklin.music.playback.stems.VocalGate
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Takes the singing apart for a blend where both songs sing: in the overlap the singing of A goes out at the start and the
 * singing of B comes in near the end, so the voices never sound at the same time. The stretch of each song is split in
 * vocals, drums, bass and the rest with the Spleeter model, put together again with the vocals at the wanted volume, and
 * laid over the decoded song ([NativeMix.setOverlay]); the engine plays the song as before, it only reads other audio there.
 * Runs on one thread of a slightly lowered priority (0.0.128: was the lowest).
 */
object MixStems {
    private const val TAG = "NooklinMix"
    private const val ASSET_DIR = "stems"
    private const val MARGIN_SEC = 2.0
    private const val MAX_SEGMENT_SEC = 40.0
    private const val THREADS = 3
    private const val A_OUT_SEC = 1.5
    private const val B_IN_SEC = 1.5
    private const val B_IN_FROM = 0.6
    private const val RATE = 44100.0
    // Stem mix: share of the overlap. B's melody comes in first (over the beat and bass of A), then drums and bass swap in one go, then
    // the melody of A leaves. Singing keeps its own curves above.
    private const val MELODY_IN_FROM = 0.1
    private const val MELODY_OUT_FROM = 0.55
    private const val MELODY_LEN = 0.3
    private const val SWAP_AT = 0.5
    private const val SWAP_LEN = 0.06

    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "mix-stems").apply { priority = Thread.NORM_PRIORITY - 1 } }
        .asCoroutineDispatcher()
    private var modelChecked = false
    private var modelThere = false

    /** True when the four model files are in the app. */
    @Synchronized
    fun available(context: Context): Boolean {
        if (!modelChecked) {
            modelChecked = true
            modelThere = try {
                val names = context.applicationContext.assets.list(ASSET_DIR)?.toSet().orEmpty()
                Stem.NAMES.all { "$it.fp16.onnx" in names }
            } catch (e: Exception) {
                false
            }
        }
        return modelThere
    }

    /** True when this plan is a blend in which both songs sing, so that the singing has to be taken apart. */
    fun needed(plan: DoubleArray): Boolean {
        val kind = MixPlan.kind(plan)
        if (MixPlan.loop(plan)) return false  // 0.0.105: the loop is the overlay of A; stems would replace it (blend cut off at the end of A)
        return MixPlan.stemMix(plan) || ((kind == MixPlan.LONG_BLEND || kind == MixPlan.BASS_SWAP_SHORT) && MixPlan.vocalOverlap(plan) > 0.05)
    }

    /**
     * Builds and sets the overlays of A and B for [plan]. Returns false when that did not work or came too late: [positionA]
     * (the place of A in seconds) must still be before the start of the overlap when the overlay of A is ready.
     */
    suspend fun build(
        context: Context, a: MixLibrary.Song, mapA: DoubleArray, b: MixLibrary.Song, mapB: DoubleArray,
        plan: DoubleArray, positionA: () -> Double
    ): Boolean = withContext(dispatcher) {
        val job = coroutineContext[Job]
        val length = MixPlan.lengthBeats(plan).toDouble()
        val a0 = MixMap.timeAtBeat(mapA, plan[1])
        val a1 = MixMap.timeAtBeat(mapA, plan[1] + length)
        val b0 = MixMap.timeAtBeat(mapB, plan[2])
        val b1 = MixMap.timeAtBeat(mapB, plan[2] + length)
        if (a1 <= a0 || b1 <= b0) return@withContext false
        val separator = StemSeparator({ name -> context.applicationContext.assets.open("$ASSET_DIR/$name").use { it.readBytes() } }, THREADS, keepSessions = true)
        val t0 = System.currentTimeMillis()
        // 0.0.129: the four model sessions are loaded once for A and B together (was 4 for each song) and closed here, whatever happens
        try {
            val mix = MixPlan.stemMix(plan)
            // 0.0.110: the singing of A goes out at the end of a sentence (the first pause in its vocal stem inside the overlap) instead of
            // always in the first seconds, where a sentence was cut off (user, 21 Oct); B's singing then starts at its first phrase after that
            var aOut = 0.0
            var aFade = A_OUT_SEC
            var aHow = "start"
            // A: the singing goes out at the end of the sentence; in a stem mix drums and bass leave at the middle, the melody after that
            val okA = overlay(separator, a, a0, a1, job, { sep, base ->
                val place = VocalGate.chooseOut(
                    separator.energyPerFrame(sep, Stem.VOCALS), { t -> separator.frameTime(t) + base - a0 }, a1 - a0, length.toInt(), 0.0
                )
                aOut = place.sec
                aFade = VocalGate.A_FADE_SEC
                aHow = place.how
            }) { stem, t ->
                val u = (t - a0) / (a1 - a0)
                when {
                    stem == Stem.VOCALS -> if (t <= a0 + aOut) 1f else if (t >= a0 + aOut + aFade) 0f else smooth(1.0 - (t - a0 - aOut) / aFade)
                    !mix -> 1f
                    stem == Stem.OTHER -> 1f - smooth((u - MELODY_OUT_FROM) / MELODY_LEN)
                    else -> 1f - smooth((u - SWAP_AT) / SWAP_LEN)
                }
            }
            val tA = System.currentTimeMillis() - t0
            if (!okA) return@withContext false
            // The overlay of A has to be there before the overlap starts
            if (positionA() > a0 - 0.3) {
                NativeMix.clearOverlay(a.handle)
                return@withContext false
            }
            // B: the singing comes in at its first phrase after A's singing is gone (at the latest near the end of the overlap)
            var bIn = B_IN_FROM * (b1 - b0)
            var bFade = B_IN_SEC
            var bHow = "default"
            val goneP = ((aOut + aFade) / (a1 - a0)).coerceIn(0.0, 1.0)
            val okB = overlay(separator, b, b0, b1, job, { sep, base ->
                val place = VocalGate.chooseIn(separator.energyPerFrame(sep, Stem.VOCALS), { t -> separator.frameTime(t) + base - b0 }, b1 - b0, goneP, length.toInt())
                // Not before 30% of the overlap (B's melody comes in from 10%), and never later than its fade needs
                bIn = place.sec.coerceAtLeast(0.3 * (b1 - b0)).coerceAtMost(max(0.0, (b1 - b0) - place.fade))
                bFade = place.fade
                bHow = place.how
            }) { stem, t ->
                val u = (t - b0) / (b1 - b0)
                when {
                    stem == Stem.VOCALS -> if (t <= b0 + bIn) 0f else if (t >= b0 + bIn + bFade) 1f else smooth((t - b0 - bIn) / bFade)
                    !mix -> 1f
                    stem == Stem.OTHER -> smooth((u - MELODY_IN_FROM) / MELODY_LEN)
                    else -> smooth((u - SWAP_AT) / SWAP_LEN)
                }
            }
            if (!okB) {
                NativeMix.clearOverlay(a.handle)
                return@withContext false
            }
            if (positionA() > a0 - 0.1) {
                NativeMix.clearOverlay(a.handle)
                NativeMix.clearOverlay(b.handle)
                return@withContext false
            }
            true
        } finally {
            separator.close()
        }
    }

    /** 0.0.127: why the stems take 70 to 90 s instead of 22 s (log 0.0.126): thermal state, battery saver, screen. */
    private fun deviceState(context: Context): String = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus.toString() else "-"
        "thermal $thermal, saver ${pm.isPowerSaveMode}, screen ${pm.isInteractive}, cores ${Runtime.getRuntime().availableProcessors()}"
    } catch (e: Exception) {
        "state unknown"
    }

    /** 0 to 1 along a half cosine. */
    private fun smooth(x: Double): Float = (0.5 - 0.5 * cos(PI * x.coerceIn(0.0, 1.0))).toFloat()

    private fun overlay(
        separator: StemSeparator, song: MixLibrary.Song, from: Double, to: Double, job: Job?,
        prepare: (StemSeparator.Separated, Double) -> Unit, gain: (Int, Double) -> Float
    ): Boolean {
        val total = song.frames
        val start = max(0L, ((from - MARGIN_SEC) * RATE).toLong())
        val end = min(total, ((to + MARGIN_SEC) * RATE).toLong())
        if (end - start < RATE || (end - start) / RATE > MAX_SEGMENT_SEC) return false
        val frames = (end - start).toInt()
        val pcm = NativeMix.copy(song.handle, start, frames) ?: return false
        val n = pcm.size / 2
        val left = FloatArray(n) { pcm[2 * it] / 32768f }
        val right = FloatArray(n) { pcm[2 * it + 1] / 32768f }
        val sep = separator.separate(left, right, { job?.ensureActive() })
        val base = start / RATE
        prepare(sep, base)  // 0.0.110: the places for the singing are chosen from the vocal stem before it is mixed
        val out = separator.render(sep) { stem, seconds -> gain(stem, base + seconds) }
        job?.ensureActive()
        val data = ShortArray(n * 2)
        for (i in 0 until n) {
            data[2 * i] = (out[0][i] * 32768f).roundToInt().coerceIn(-32768, 32767).toShort()
            data[2 * i + 1] = (out[1][i] * 32768f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return NativeMix.setOverlay(song.handle, start, data, n)
    }
}
