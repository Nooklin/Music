package org.nooklin.music.playback.beat

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.data.server.ServerSession

/**
 * What the analysis found, kept in memory while the service runs and saved in a file on this phone, so that a song is
 * analysed only once (a restart does not cost the reading of the songs again). The end of a song is analysed for the
 * bar line to leave from; the whole of a song is analysed for the places to come in and to leave. A downloaded file is
 * read from the phone, other songs are read from the server (the whole file, once). [end] and [entries] return null
 * when there is no result yet or the song has no clear beat. A saved result is dropped when the length of the song
 * changed, and the whole file is dropped when the analysis changed ([VERSION]).
 */
object BeatMixStore {
    private const val WINDOW_MS = 35_000L
    private const val TAG = "NooklinMix"

    // Raise this when the analysis changes, so that old results are not used
    private const val VERSION = 6
    private const val FILE = "beatmix.json"
    private const val MAX_KEYS = 3_000
    private const val LENGTH_TOLERANCE_MS = 2_000L

    private val ends = ConcurrentHashMap<String, BeatEdge>()
    private val endLengths = ConcurrentHashMap<String, Long>()
    private val songs = ConcurrentHashMap<String, SongMap>()
    private val songLengths = ConcurrentHashMap<String, Long>()
    private val tried = ConcurrentHashMap.newKeySet<String>()
    private val loaded = AtomicBoolean(false)

    /** Reads the saved results, so that the tempo and loudness of songs that were analysed before are known. */
    fun preload(context: Context) = load(context.applicationContext)

    fun end(id: String): BeatEdge? = ends[id]

    fun entries(id: String): List<BeatEdge>? = songs[id]?.entries

    fun exits(id: String): List<BeatEdge>? = songs[id]?.exits

    /** Where the music of a song really ends, when there is a silent tail of a second or more; else null. */
    fun cueOut(id: String, durationMs: Long): Long? =
        songs[id]?.cueOutMs?.takeIf { it > durationMs / 2 && it < durationMs - 1_000L }

    /** Where the music of a song really starts, when there is a silent start of 0.3 to 20 seconds; else null. */
    fun cueIn(id: String): Long? = songs[id]?.cueInMs?.takeIf { it in 300L..20_000L }

    /** How loud the song is in general (see [BeatAnalyzer]), or null when the song is not analysed. */
    fun loudness(id: String): Double? = songs[id]?.loudness

    /** The melody curve of a song, one digit per second (see [SongMap.lead]); empty when not known. */
    /** The key of the whole song (see KeyDetector), or -1 when not known. */
    fun songKey(id: String): Int = songs[id]?.key ?: -1

    fun lead(id: String): String = songs[id]?.lead.orEmpty()

    suspend fun ensureEnd(context: Context, id: String, durationMs: Long) {
        val key = "$id:end"
        withContext(Dispatchers.IO) { load(context) }
        val saved = endLengths[id]
        if (saved != null && abs(saved - durationMs) > LENGTH_TOLERANCE_MS) {
            ends.remove(id)
            endLengths.remove(id)
        }
        if (!begin(key)) return
        if (ends.containsKey(id)) {
            return
        }
        val source = sourceOf(context, id) ?: run { tried.remove(key); return }
        val startMs = maxOf(0L, durationMs - WINDOW_MS)
        val t0 = SystemClock.elapsedRealtime()
        val edge = withContext(Dispatchers.IO) {
            BeatAnalyzer.analyzeEnd(source, startMs, WINDOW_MS + 5_000L)
        }
        val took = SystemClock.elapsedRealtime() - t0
        if (edge != null) {
            ends[id] = edge
            endLengths[id] = durationMs
            withContext(Dispatchers.IO) { save(context) }
        } else {
        }
    }

    suspend fun ensureEntries(context: Context, id: String, durationMs: Long) {
        val key = "$id:in"
        withContext(Dispatchers.IO) { load(context) }
        val saved = songLengths[id]
        if (saved != null && abs(saved - durationMs) > LENGTH_TOLERANCE_MS) {
            songs.remove(id)
            songLengths.remove(id)
        }
        if (!begin(key)) return
        if (songs.containsKey(id)) {
            return
        }
        val source = sourceOf(context, id) ?: run { tried.remove(key); return }
        val t0 = SystemClock.elapsedRealtime()
        val map = withContext(Dispatchers.IO) { BeatAnalyzer.analyzeSong(source, durationMs) }
        val took = SystemClock.elapsedRealtime() - t0
        if (map != null) {
            songs[id] = map
            songLengths[id] = durationMs
            withContext(Dispatchers.IO) { save(context) }
        } else {
        }
    }

    private fun begin(key: String): Boolean {
        if (!tried.add(key)) return false
        if (tried.size > MAX_KEYS) {
            tried.clear()
            ends.clear()
            endLengths.clear()
            songs.clear()
            songLengths.clear()
            tried.add(key)
        }
        return true
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    /** Reads the saved results, once. Anything unreadable is ignored. */
    private fun load(context: Context) {
        if (!loaded.compareAndSet(false, true)) return
        try {
            val f = file(context)
            if (!f.exists()) return
            val root = JSONObject(f.readText())
            if (root.optInt("v") != VERSION) return
            val e = root.optJSONObject("ends")
            if (e != null) {
                for (id in e.keys()) {
                    val o = e.getJSONObject(id)
                    ends.putIfAbsent(id, edgeOf(o.getJSONArray("e")))
                    endLengths.putIfAbsent(id, o.getLong("d"))
                }
            }
            val s = root.optJSONObject("songs")
            if (s != null) {
                for (id in s.keys()) {
                    val o = s.getJSONObject(id)
                    val map = SongMap(
                        edgesOf(o.getJSONArray("in")),
                        edgesOf(o.getJSONArray("out")),
                        if (o.has("ci")) o.getLong("ci") else null,
                        if (o.has("co")) o.getLong("co") else null,
                        o.getDouble("l"),
                        o.optString("m", ""),
                        o.optInt("k", -1)
                    )
                    songs.putIfAbsent(id, map)
                    songLengths.putIfAbsent(id, o.getLong("d"))
                }
            }
        } catch (e: Exception) {
        }
    }

    /** Writes all results; first to a temporary file, so that a stop halfway does not leave a broken file. */
    @Synchronized
    private fun save(context: Context) {
        try {
            val root = JSONObject().put("v", VERSION)
            val e = JSONObject()
            for ((id, edge) in ends) {
                e.put(id, JSONObject().put("e", edgeJson(edge)).put("d", endLengths[id] ?: 0L))
            }
            root.put("ends", e)
            val s = JSONObject()
            for ((id, map) in songs) {
                val o = JSONObject()
                    .put("in", edgesJson(map.entries))
                    .put("out", edgesJson(map.exits))
                    .put("l", map.loudness)
                    .put("m", map.lead)
                    .put("k", map.key)
                    .put("d", songLengths[id] ?: 0L)
                if (map.cueInMs != null) o.put("ci", map.cueInMs)
                if (map.cueOutMs != null) o.put("co", map.cueOutMs)
                s.put(id, o)
            }
            root.put("songs", s)
            val target = file(context)
            val temp = File(target.parentFile, "$FILE.tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } catch (e: Exception) {
        }
    }

    private fun edgeJson(e: BeatEdge) = JSONArray().put(e.bpm).put(e.anchorMs).also { if (e.key >= 0) it.put(e.key) }

    private fun edgesJson(list: List<BeatEdge>) = JSONArray().also { a -> list.forEach { a.put(edgeJson(it)) } }

    private fun edgeOf(a: JSONArray) = BeatEdge(a.getDouble(0), a.getDouble(1), if (a.length() > 2) a.getInt(2) else -1)

    private fun edgesOf(a: JSONArray): List<BeatEdge> = (0 until a.length()).map { edgeOf(a.getJSONArray(it)) }

    internal suspend fun sourceOf(context: Context, id: String): ((android.media.MediaExtractor) -> Unit)? {
        val local = DownloadManager.get(context).fileFor(id)
        val url = if (local == null) ServerSession.get(context).streamUrl(id) else null
        if (local == null && url == null) return null
        return { extractor ->
            if (local != null) extractor.setDataSource(local.absolutePath) else extractor.setDataSource(url!!)
        }
    }
}
