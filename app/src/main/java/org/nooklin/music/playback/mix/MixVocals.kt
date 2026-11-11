package org.nooklin.music.playback.mix

import android.content.Context
import android.util.Log
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.nooklin.music.playback.beat.BeatMixStore
import org.nooklin.music.playback.stems.PcmReader
import org.nooklin.music.playback.stems.Stem
import org.nooklin.music.playback.stems.StemSeparator
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * How much of the sound is singing, per second of a song, measured with the Spleeter model that ships in the app (the
 * "vocals" stem against the music as a whole; only that one model is run). Only the stretches the mix needs are measured: the start and the end of a song.
 * Seconds that were not measured stay at -1. Runs on one thread of the lowest priority, one song at a time; the result is
 * kept on disk so that a song is measured once.
 */
object MixVocals {
    private const val TAG = "NooklinMix"
    private const val DIR = "automix_vocals2"
    private const val ASSET_DIR = "stems"
    private const val WINDOW_SEC = 60.0
    private const val CHUNK_SEC = 20.0
    private const val THREADS = 3

    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "mix-vocals").apply { priority = Thread.MIN_PRIORITY } }
        .asCoroutineDispatcher()
    private val memory = HashMap<String, FloatArray>()
    private val busy = HashSet<String>()
    private var modelChecked = false
    private var modelThere = false

    /** The singing share per second of [id] (-1 where not measured), or null when nothing was measured. */
    @Synchronized
    fun seconds(context: Context, id: String): FloatArray? {
        memory[id]?.let { return it }
        val file = fileOf(context, id)
        if (!file.exists()) return null
        return try {
            val text = file.readText().trim()
            if (text.isEmpty()) null else FloatArray(text.split(',').size).also { out ->
                text.split(',').forEachIndexed { i, s -> out[i] = s.toFloatOrNull() ?: -1f }
                memory[id] = out
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Forgets the singing measured for every song. */
    @Synchronized
    fun clear(context: Context) {
        memory.clear()
        File(context.filesDir, DIR).listFiles()?.forEach { it.delete() }
    }

    /** True when the start (and, when [tail], the end) of the song has been measured. */
    fun covered(context: Context, id: String, durationSec: Double, head: Boolean, tail: Boolean): Boolean {
        val v = seconds(context, id) ?: return false
        return (!head || measured(v, 0.0, min(durationSec, WINDOW_SEC))) &&
            (!tail || measured(v, maxOf(0.0, durationSec - WINDOW_SEC), durationSec))
    }

    private fun measured(v: FloatArray, from: Double, to: Double): Boolean {
        var s = floor(from).toInt()
        val e = min(v.size, ceil(to).toInt())
        if (e <= s) return false
        while (s < e) {
            if (v[s] < 0f) return false
            s++
        }
        return true
    }

    /** Measures what is missing of the start and the end of [id]. Safe to call again while it runs. */
    suspend fun analyse(context: Context, id: String, durationSec: Double, head: Boolean, tail: Boolean) {
        if (durationSec < 20.0) return
        if (covered(context, id, durationSec, head, tail)) return
        synchronized(this) {
            if (!busy.add(id)) return
        }
        try {
            withContext(dispatcher) { run(context.applicationContext, id, durationSec, head, tail) }
        } finally {
            synchronized(this) { busy.remove(id) }
        }
    }

    private suspend fun run(app: Context, id: String, durationSec: Double, head: Boolean, tail: Boolean) {
        if (!modelChecked) {
            modelChecked = true
            modelThere = try {
                app.assets.list(ASSET_DIR)?.contains("vocals.fp16.onnx") == true
            } catch (e: Exception) {
                false
            }
        }
        if (!modelThere) return
        val source = BeatMixStore.sourceOf(app, id)
        if (source == null) {
            return
        }
        val total = ceil(durationSec).toInt()
        val values = seconds(app, id)?.takeIf { it.size == total } ?: FloatArray(total) { -1f }
        val ranges = ArrayList<Pair<Double, Double>>()
        if (head) ranges.add(0.0 to min(durationSec, WINDOW_SEC))
        if (tail) ranges.add(maxOf(0.0, durationSec - WINDOW_SEC) to durationSec)
        val t0 = System.currentTimeMillis()
        try {
            val separator = StemSeparator({ name -> app.assets.open("$ASSET_DIR/$name").use { it.readBytes() } }, THREADS)
            for ((from, to) in ranges) {
                var at = from
                while (at < to - 1.0) {
                    val end = min(to, at + CHUNK_SEC)
                    if (!measured(values, at, end)) {
                        val pcm = PcmReader.read(source, (at * 1000).toLong(), (end * 1000).toLong(), 60_000L) ?: break
                        val sep = separator.separate(pcm.left, pcm.right, only = Stem.VOCALS)
                        val share = separator.shareOf(sep, Stem.VOCALS)
                        val sum = DoubleArray(total)
                        val cnt = IntArray(total)
                        val base = pcm.startMs / 1000.0
                        for (t in 0 until sep.frames) {
                            val sec = floor(base + separator.frameTime(t)).toInt()
                            if (sec < 0 || sec >= total) continue
                            sum[sec] += share[t]
                            cnt[sec]++
                        }
                        for (s in floor(at).toInt() until min(total, ceil(end).toInt())) if (cnt[s] > 0) values[s] = (sum[s] / cnt[s]).toFloat()
                        synchronized(this) { memory[id] = values }
                    }
                    at = end
                }
            }
            save(app, id, values)
        } catch (e: Throwable) {
        }
    }

    private fun fileOf(context: Context, id: String): File {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        return File(dir, id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".txt")
    }

    private fun save(context: Context, id: String, v: FloatArray) {
        try {
            fileOf(context, id).writeText(v.joinToString(",") { "%.3f".format(java.util.Locale.US, it) })
        } catch (e: Exception) {
        }
    }
}
