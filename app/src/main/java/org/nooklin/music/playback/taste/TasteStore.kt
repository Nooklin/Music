package org.nooklin.music.playback.taste

import android.content.Context
import android.util.Log
import java.io.File
import org.json.JSONObject

/**
 * What you do with songs, kept on this phone only: per song whether it was played through or skipped, with its artist
 * and genre. The server has no place for skips, so this is where they live. AutoPlay and the Discovery Station read it.
 */
class TasteStore private constructor(private val file: File) {
    class Entry(val artistId: String, var genre: String, var done: Int, var skip: Int, var at: Long)

    private val entries = LinkedHashMap<String, Entry>()

    init {
        try {
            if (file.exists()) {
                val root = JSONObject(file.readText())
                for (id in root.keys()) {
                    val o = root.getJSONObject(id)
                    entries[id] = Entry(o.optString("a"), o.optString("g"), o.optInt("d"), o.optInt("s"), o.optLong("t"))
                }
            }
        } catch (e: Exception) {
            entries.clear()
        }
    }

    @Synchronized
    fun entry(id: String): Entry? = entries[id]

    /** A song counted as played through ([done]) or as skipped. */
    @Synchronized
    fun record(id: String, artistId: String, done: Boolean) {
        val e = entries.getOrPut(id) { Entry(artistId, "", 0, 0, 0L) }
        if (done) e.done++ else e.skip++
        e.at = System.currentTimeMillis()
        // Moves the song to the end, so the oldest are the first to go
        entries.remove(id)
        entries[id] = e
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        save()
    }

    @Synchronized
    fun setGenre(id: String, genre: String) {
        val e = entries[id] ?: return
        if (e.genre != genre) {
            e.genre = genre
            save()
        }
    }

    /** How many songs of this artist were played through and how many skipped. */
    @Synchronized
    fun artistStats(artistId: String?): Pair<Int, Int> {
        if (artistId.isNullOrEmpty()) return 0 to 0
        var done = 0
        var skip = 0
        for (e in entries.values) if (e.artistId == artistId) {
            done += e.done
            skip += e.skip
        }
        return done to skip
    }

    /** Per artist: played through and skipped, for songs that were last played or skipped after [since]. */
    @Synchronized
    fun artistsSince(since: Long): Map<String, Pair<Int, Int>> {
        val out = HashMap<String, Pair<Int, Int>>()
        for (e in entries.values) {
            if (e.artistId.isEmpty() || e.at < since) continue
            val old = out[e.artistId] ?: (0 to 0)
            out[e.artistId] = (old.first + e.done) to (old.second + e.skip)
        }
        return out
    }

    /** Per genre: played through minus skipped (skips weigh more). */
    @Synchronized
    fun genreScores(): Map<String, Double> {
        val scores = HashMap<String, Double>()
        for (e in entries.values) {
            if (e.genre.isEmpty()) continue
            scores[e.genre] = (scores[e.genre] ?: 0.0) + e.done - 1.5 * e.skip
        }
        return scores
    }

    /** The last songs that were played through, newest first. */
    @Synchronized
    fun recentDone(n: Int): List<String> = entries.entries.reversed().filter { it.value.done > 0 }.take(n).map { it.key }

    /** The last songs that were played or skipped, newest first. */
    @Synchronized
    fun recent(n: Int): Set<String> = entries.keys.reversed().take(n).toSet()

    @Synchronized
    private fun save() {
        try {
            val root = JSONObject()
            for ((id, e) in entries) {
                root.put(id, JSONObject().put("a", e.artistId).put("g", e.genre).put("d", e.done).put("s", e.skip).put("t", e.at))
            }
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        file.delete()
    }

    companion object {
        private const val TAG = "NooklinTaste"
        private const val MAX_ENTRIES = 3000

        @Volatile
        private var instance: TasteStore? = null

        fun get(context: Context): TasteStore =
            instance ?: synchronized(this) {
                instance ?: TasteStore(File(context.applicationContext.filesDir, "taste.json")).also { instance = it }
            }
    }
}
