package org.nooklin.music.data.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.nooklin.music.data.library.ReplayGain

private val Context.queueDataStore by preferencesDataStore(name = "nooklin_music_queue")

private val QUEUE_KEY = stringPreferencesKey("queue")

/** One song in the saved queue. Only text and ids: no address and no login token is ever saved. */
data class QueueEntry(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverArt: String?,
    val suffix: String,
    val durationMs: Long,
    val albumId: String? = null,
    val artistId: String? = null,
    val bitRate: Int = 0,
    val replayGain: ReplayGain? = null,
    val bpm: Int = 0  // the tempo tag of the server (0 when there is none); only for the mix log
)

data class SavedQueue(
    val entries: List<QueueEntry>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int
)

class QueueStore(private val context: Context) {

    suspend fun save(queue: SavedQueue) {
        // 0.0.146: the service calls this on the main thread; building the text of a long queue showed up in the stack samples
        val text = withContext(Dispatchers.Default) { toJson(queue).toString() }
        context.queueDataStore.edit { it[QUEUE_KEY] = text }
    }

    private fun toJson(queue: SavedQueue): JSONObject {
        val json = JSONObject()
            .put("index", queue.index)
            .put("position", queue.positionMs)
            .put("shuffle", queue.shuffle)
            .put("repeat", queue.repeatMode)
            .put(
                "entries",
                JSONArray().also { array ->
                    queue.entries.forEach { e ->
                        array.put(
                            JSONObject()
                                .put("id", e.id)
                                .put("title", e.title)
                                .put("artist", e.artist)
                                .put("album", e.album)
                                .put("cover", e.coverArt ?: "")
                                .put("suffix", e.suffix)
                                .put("duration", e.durationMs)
                                .put("albumId", e.albumId ?: "")
                                .put("artistId", e.artistId ?: "")
                                .put("bitRate", e.bitRate)
                                .also { o -> e.replayGain?.let { o.put("rg", gainToJson(it)) } }
                        )
                    }
                }
            )
        return json
    }

    suspend fun load(): SavedQueue? {
        val text = context.queueDataStore.data.first()[QUEUE_KEY] ?: return null
        return try {
            val json = JSONObject(text)
            val array = json.getJSONArray("entries")
            val entries = (0 until array.length()).map { i ->
                val e = array.getJSONObject(i)
                QueueEntry(
                    id = e.getString("id"),
                    title = e.optString("title"),
                    artist = e.optString("artist"),
                    album = e.optString("album"),
                    coverArt = e.optString("cover").ifEmpty { null },
                    suffix = e.optString("suffix"),
                    durationMs = e.optLong("duration"),
                    albumId = e.optString("albumId").ifEmpty { null },
                    artistId = e.optString("artistId").ifEmpty { null },
                    bitRate = e.optInt("bitRate"),
                    replayGain = e.optJSONObject("rg")?.let { gainFromJson(it) }
                )
            }
            if (entries.isEmpty()) {
                null
            } else {
                SavedQueue(
                    entries = entries,
                    index = json.optInt("index").coerceIn(0, entries.lastIndex),
                    positionMs = json.optLong("position").coerceAtLeast(0L),
                    shuffle = json.optBoolean("shuffle"),
                    repeatMode = json.optInt("repeat")
                )
            }
        } catch (e: JSONException) {
            null
        }
    }

    private fun gainToJson(g: ReplayGain): JSONObject = JSONObject().also { o ->
        g.trackGain?.let { o.put("tg", it) }
        g.albumGain?.let { o.put("ag", it) }
        g.trackPeak?.let { o.put("tp", it) }
        g.albumPeak?.let { o.put("ap", it) }
        g.fallbackGain?.let { o.put("fg", it) }
    }

    private fun gainFromJson(o: JSONObject) = ReplayGain(
        trackGain = if (o.has("tg")) o.getDouble("tg") else null,
        albumGain = if (o.has("ag")) o.getDouble("ag") else null,
        trackPeak = if (o.has("tp")) o.getDouble("tp") else null,
        albumPeak = if (o.has("ap")) o.getDouble("ap") else null,
        fallbackGain = if (o.has("fg")) o.getDouble("fg") else null
    )
}
