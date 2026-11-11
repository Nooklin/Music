package org.nooklin.music.playback.stems

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.nooklin.music.playback.beat.BeatMixStore
import org.nooklin.music.playback.beat.BeatPlan

/**
 * A finished overlap as an audio file at the tempo of song B. It begins at [baseMs] in song A; song A plays as it is
 * up to [StemMix.PLAIN_MS] in the file. The overlap begins at [overlapMs] in the file and takes [fadeWallMs].
 */
class StemClip(val file: File, val baseMs: Long, val lengthMs: Long, val overlapMs: Long, val fadeWallMs: Long)

/**
 * Makes the overlaps of beat mixes from stems, ahead of time and on a low priority, and hands the result to the mix.
 * A mix that has no result in time simply uses the filters. Everything stays on this phone.
 */
class StemTransitions(context: Context) {
    private val app = context.applicationContext
    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "nooklin-stems").apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
        }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val states = ConcurrentHashMap<String, Any>()
    private val folder = File(app.cacheDir, "stems")

    init {
        // Clips of an earlier run are of no use
        folder.listFiles()?.forEach { it.delete() }
    }

    /** True when the model files are part of the app. */
    fun available(): Boolean = hasModel

    private val hasModel: Boolean by lazy {
        try {
            val names = app.assets.list(ASSET_DIR)?.toSet().orEmpty()
            Stem.NAMES.all { (it + ".fp16.onnx") in names }
        } catch (e: Exception) {
            false
        }
    }

    @Volatile
    private var lastBuildMs = 0L

    /** About how long a clip takes to make (milliseconds): the last one with some room, or a guess before the first. */
    fun estimateMs(): Long =
        if (lastBuildMs <= 0L) FIRST_ESTIMATE_MS else ((lastBuildMs * 1.25).toLong() + 3_000L).coerceAtMost(MAX_ESTIMATE_MS)

    /** The finished clip for the change [key], or null while there is none (or it failed). */
    fun clip(key: String): StemClip? {
        val clip = states[key] as? StemClip ?: return null
        // The folder keeps only the newest clips: one that was removed is not a clip any more
        if (!clip.file.isFile) {
            states.remove(key)
            return null
        }
        return clip
    }

    /** Starts making the clip for [key] unless that was started before. */
    fun request(key: String, idA: String, idB: String, plan: BeatPlan, factorA: Float, factorB: Float) {
        if (states.putIfAbsent(key, RUNNING) != null) return
        scope.launch {
            val t0 = SystemClock.elapsedRealtime()
            try {
                val spec = StemClipSpec(plan.startA, plan.fadeMs, plan.ratio, plan.startB, factorA, factorB, plan.bassSwap, plan.beats)
                if (StemMix.baseMs(spec) < 0L) {
                    states[key] = FAILED
                    return@launch
                }
                val rangeA = StemMix.rangeA(spec)
                val rangeB = StemMix.rangeB(spec)
                val sourceA = BeatMixStore.sourceOf(app, idA)
                val sourceB = BeatMixStore.sourceOf(app, idB)
                if (sourceA == null || sourceB == null) {
                    states[key] = FAILED
                    return@launch
                }
                val a = PcmReader.read(sourceA, rangeA.first, rangeA.second, READ_LIMIT_MS)
                val b = PcmReader.read(sourceB, rangeB.first, rangeB.second, READ_LIMIT_MS)
                val tRead = SystemClock.elapsedRealtime()
                if (a == null || b == null) {
                    states[key] = FAILED
                    return@launch
                }
                val separator = StemSeparator({ name -> app.assets.open("$ASSET_DIR/$name").use { it.readBytes() } }, THREADS)
                val clip = StemMix.mix(separator, a, b, spec) {  }
                if (clip == null) {
                    states[key] = FAILED
                    return@launch
                }
                folder.mkdirs()
                folder.listFiles()?.sortedBy { it.lastModified() }?.dropLast(2)?.forEach { it.delete() }
                val file = File(folder, "clip-${System.nanoTime()}.wav")
                file.writeBytes(StemMix.wavBytes(clip[0], clip[1]))
                val lengthMs = clip[0].size * 1000L / StemSeparator.SAMPLE_RATE
                states[key] = StemClip(file, StemMix.baseMs(spec), lengthMs, StemMix.OVERLAP_AT_MS, StemMix.fadeWallMs(spec))
                lastBuildMs = SystemClock.elapsedRealtime() - t0
            } catch (e: Throwable) {
                states[key] = FAILED
            }
        }
    }

    private companion object {
        const val TAG = "NooklinMix"
        const val ASSET_DIR = "stems"
        const val THREADS = 4
        const val FIRST_ESTIMATE_MS = 30_000L
        const val MAX_ESTIMATE_MS = 50_000L
        const val READ_LIMIT_MS = 60_000L
        val RUNNING = Any()
        val FAILED = Any()
    }
}
