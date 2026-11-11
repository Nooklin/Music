package org.nooklin.music.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.nooklin.music.data.library.LibraryApi
import org.nooklin.music.data.library.ReplayGain
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.local.AppPreferences
import org.nooklin.music.data.server.ServerSession

enum class JobState { QUEUED, ACTIVE, WAITING_WIFI, WAITING_NETWORK, FAILED }

data class DownloadJob(
    val song: Song,
    val state: JobState = JobState.QUEUED,
    val received: Long = 0,
    // -1 when the server does not say how large the file is
    val total: Long = -1,
    val message: String = ""
)

data class DownloadedSong(val song: Song, val fileName: String, val bytes: Long)

/**
 * Keeps the original file of songs on the device, one after the other. The files and a small index with the song
 * details (so a downloaded song can be listed and played without the server) live in the private storage of the app.
 *
 * Downloads run in the app process. [DownloadService] keeps that process alive with a notification while the queue has
 * work. Optionally the songs with a heart are added to the queue automatically (see [syncFavorites]).
 */
class DownloadManager private constructor(context: Context) {
    private val app = context.applicationContext
    private val session = ServerSession.get(app)
    private val preferences = AppPreferences(app)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val dir = File(app.filesDir, "downloads").also { it.mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloaded = MutableStateFlow(readIndex())
    val downloaded: StateFlow<Map<String, DownloadedSong>> = _downloaded.asStateFlow()

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _wifiOnly = MutableStateFlow(true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    // 0.0.140: the most the downloads may take, in MB; 0 is no limit
    private val _limitMb = MutableStateFlow(0)
    val limitMb: StateFlow<Int> = _limitMb.asStateFlow()

    private val _autoFavorites = MutableStateFlow(false)
    val autoFavorites: StateFlow<Boolean> = _autoFavorites.asStateFlow()

    private var worker: Job? = null
    private var activeId: String? = null
    private var activeItem: Deferred<Outcome>? = null
    @Volatile
    private var activeCall: Call? = null

    init {
        scope.launch { preferences.wifiOnlyDownloads.collect { _wifiOnly.value = it } }
        scope.launch { preferences.autoDownloadFavorites.collect { _autoFavorites.value = it } }
        scope.launch { preferences.downloadLimitMb.collect { _limitMb.value = it } }
        // One check when the app starts
        scope.launch { if (preferences.autoDownloadFavorites.first()) syncFavorites() }
        scope.launch { fillGenres() }
    }

    /**
     * 0.0.134: downloads made before the genre was saved have none; it is read once from the tag of the file itself
     * (the server takes its genre from the same tag) and saved.
     */
    private fun fillGenres() {
        val missing = _downloaded.value.values.filter { it.song.genre.isEmpty() }
        if (missing.isEmpty()) return
        var changed = false
        for (entry in missing) {
            val file = File(dir, entry.fileName)
            val genre = try {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(file.path)
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty().trim()
                } finally {
                    r.release()
                }
            } catch (e: Exception) {
                ""
            }
            if (genre.isEmpty()) continue
            _downloaded.update { map -> map[entry.song.id]?.let { map + (it.song.id to it.copy(song = it.song.copy(genre = genre))) } ?: map }
            changed = true
        }
        if (changed) writeIndex()
    }

    /** The file of a downloaded song, or null when it is not on the device. Safe to call from any thread. */
    fun fileFor(songId: String): File? {
        val entry = _downloaded.value[songId] ?: return null
        return File(dir, entry.fileName).takeIf { it.exists() }
    }

    fun setAutoFavorites(value: Boolean) {
        scope.launch {
            preferences.setAutoDownloadFavorites(value)
            if (value) syncFavorites()
        }
    }

    /**
     * Adds the songs with a heart that are not on the device yet. Does nothing when the option is off. A song that
     * the user removed from the downloads while it still has a heart comes back at the next check.
     */
    fun syncFavorites() {
        scope.launch {
            if (!preferences.autoDownloadFavorites.first()) return@launch
            val reply = LibraryApi(app).favorites()
            if (reply is org.nooklin.music.data.server.Reply.Ok) enqueue(reply.value.songs, resumeIfPaused = false)
        }
    }

    fun setLimitMb(value: Int) {
        scope.launch { preferences.setDownloadLimitMb(value) }
    }

    /** Removes the given songs from the device. */
    fun removeMany(songIds: Collection<String>) {
        songIds.forEach { id -> _downloaded.value[id]?.let { File(dir, it.fileName).delete() } }
        _downloaded.update { it - songIds.toSet() }
        writeIndex()
    }

    /**
     * Keeps the downloads under the limit by removing the oldest songs without a heart (the file date is the download
     * date). Songs with a heart stay: they would come back by themselves. [keepId] is the song that just arrived.
     */
    private fun enforceLimit(keepId: String?) {
        val limit = _limitMb.value * 1_048_576L
        if (limit <= 0) return
        var total = _downloaded.value.values.sumOf { it.bytes }
        if (total <= limit) return
        val victims = _downloaded.value.values
            .filter { !it.song.starred && it.song.id != keepId }
            .sortedBy { File(dir, it.fileName).lastModified() }
        val gone = mutableListOf<String>()
        for (v in victims) {
            if (total <= limit) break
            total -= v.bytes
            gone += v.song.id
        }
        if (gone.isNotEmpty()) removeMany(gone)
    }

    /** True when the downloads are at the limit and nothing can be removed to make room. */
    private fun limitReached(): Boolean {
        val limit = _limitMb.value * 1_048_576L
        if (limit <= 0) return false
        val all = _downloaded.value.values
        val fixed = all.filter { it.song.starred }.sumOf { it.bytes }
        return fixed >= limit
    }

    fun setWifiOnly(value: Boolean) {
        scope.launch { preferences.setWifiOnlyDownloads(value) }
    }

    /** Adds songs that are not on the device and not in the queue yet. A failed earlier try is replaced. */
    fun enqueue(songs: List<Song>, resumeIfPaused: Boolean = true) {
        _jobs.update { current ->
            val keep = current.filterNot { job -> job.state == JobState.FAILED && songs.any { it.id == job.song.id } }
            keep + songs
                .distinctBy { it.id }
                .filter { song -> song.id !in _downloaded.value && keep.none { it.song.id == song.id } }
                .map { DownloadJob(it) }
        }
        if (resumeIfPaused) _paused.value = false
        startWorker()
    }

    fun cancel(songId: String) {
        _jobs.update { list -> list.filterNot { it.song.id == songId } }
        if (activeId == songId) abortActive()
    }

    fun cancelAll() {
        _jobs.value = emptyList()
        abortActive()
    }

    /** Stops after the song in progress is dropped. A half-finished file is not kept, that song starts over. */
    fun pause() {
        _paused.value = true
        abortActive()
        if (writePending.get()) writeIndexNow()
    }

    fun resume() {
        _paused.value = false
        startWorker()
    }

    fun remove(songId: String) {
        _downloaded.value[songId]?.let { File(dir, it.fileName).delete() }
        _downloaded.update { it - songId }
        writeIndex()
    }

    fun removeAll() {
        _downloaded.value.values.forEach { File(dir, it.fileName).delete() }
        _downloaded.value = emptyMap()
        writeIndex()
    }

    private fun abortActive() {
        activeCall?.cancel()
        activeItem?.cancel()
    }

    @Synchronized
    private fun startWorker() {
        if (_paused.value || _jobs.value.none { it.state != JobState.FAILED }) return
        DownloadService.start(app)
        if (worker?.isActive == true) return
        worker = scope.launch { runQueue() }
    }

    private sealed interface Outcome {
        data class Done(val bytes: Long) : Outcome
        data class Failed(val message: String) : Outcome
        data object Cancelled : Outcome
    }

    private suspend fun runQueue() {
        while (currentCoroutineContext().isActive) {
            if (_paused.value) return
            val job = _jobs.value.firstOrNull { it.state != JobState.FAILED } ?: return
            val song = job.song

            val blocked = blockedBy()
            if (blocked != null) {
                update(song.id) { it.copy(state = blocked) }
                delay(5_000)
                continue
            }

            if (limitReached()) {
                update(song.id) { it.copy(state = JobState.FAILED, message = "Storage limit reached.") }
                continue
            }

            update(song.id) { it.copy(state = JobState.ACTIVE, received = 0, total = -1, message = "") }
            val item = scope.async { fetch(song) }
            activeId = song.id
            activeItem = item
            val outcome = try {
                item.await()
            } catch (e: CancellationException) {
                // Only the song was cancelled when this worker is still alive
                if (!currentCoroutineContext().isActive) throw e
                Outcome.Cancelled
            }
            activeId = null
            activeItem = null
            activeCall = null

            when (outcome) {
                is Outcome.Done -> {
                    val name = fileName(song)
                    _downloaded.update { it + (song.id to DownloadedSong(song, name, outcome.bytes)) }
                    writeIndexNow()
                    enforceLimit(song.id)
                    _jobs.update { list -> list.filterNot { it.song.id == song.id } }
                }
                is Outcome.Failed -> update(song.id) { it.copy(state = JobState.FAILED, message = outcome.message) }
                // Removed from the queue, or paused: in the second case the song waits at the front again
                Outcome.Cancelled -> update(song.id) { it.copy(state = JobState.QUEUED, received = 0, total = -1) }
            }
        }
    }

    private fun blockedBy(): JobState? {
        val network = connectivity.activeNetwork ?: return JobState.WAITING_NETWORK
        val caps = connectivity.getNetworkCapabilities(network) ?: return JobState.WAITING_NETWORK
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return JobState.WAITING_NETWORK
        if (_wifiOnly.value && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            return JobState.WAITING_WIFI
        }
        return null
    }

    private fun update(songId: String, change: (DownloadJob) -> DownloadJob) {
        _jobs.update { list -> list.map { if (it.song.id == songId) change(it) else it } }
    }

    private fun fileName(song: Song): String {
        val safeId = song.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val extension = song.suffix.replace(Regex("[^A-Za-z0-9]"), "").ifEmpty { "audio" }
        return "$safeId.$extension"
    }

    private suspend fun fetch(song: Song): Outcome {
        val url = session.streamUrl(song.id)
            ?: return Outcome.Failed("No server set up, or the saved password cannot be read.")
        val target = File(dir, fileName(song))
        val part = File(dir, "${target.name}.part")
        val call = http.newCall(Request.Builder().url(url).build())
        activeCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) return Outcome.Failed("The server answered HTTP ${response.code}.")
                val type = response.header("Content-Type").orEmpty()
                // A refused request comes back as a small document with status 200
                if (type.startsWith("text/") || type.contains("json") || type.contains("xml")) {
                    return Outcome.Failed("The server did not send the file.")
                }
                val total = response.body.contentLength()
                var received = 0L
                var lastReport = 0L
                response.body.byteStream().use { input ->
                    FileOutputStream(part).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            received += count
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 300) {
                                lastReport = now
                                update(song.id) { it.copy(received = received, total = total) }
                            }
                            if (!currentCoroutineContext().isActive) {
                                part.delete()
                                return Outcome.Cancelled
                            }
                        }
                    }
                }
                if (received == 0L) {
                    part.delete()
                    return Outcome.Failed("The server sent an empty file.")
                }
                target.delete()
                if (!part.renameTo(target)) {
                    part.delete()
                    return Outcome.Failed("The file could not be saved.")
                }
                return Outcome.Done(received)
            }
        } catch (e: IOException) {
            part.delete()
            return if (call.isCanceled()) Outcome.Cancelled else Outcome.Failed("Download failed: ${e.message ?: "connection problem"}")
        }
    }

    private fun readIndex(): Map<String, DownloadedSong> = try {
        val array = JSONArray(indexFile.readText())
        (0 until array.length())
            .mapNotNull { runCatching { fromJson(array.getJSONObject(it)) }.getOrNull() }
            .filter { File(dir, it.fileName).exists() }
            .associateBy { it.song.id }
    } catch (e: Exception) {
        // No index yet, or one that cannot be read
        emptyMap()
    }

    // 0.0.148: removals and genre updates are merged into one write a second later (a remove of many songs wrote the whole list per call).
    // A finished download and pause() still write at once.
    private val writePending = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun writeIndex() {
        if (writePending.compareAndSet(false, true)) {
            scope.launch {
                delay(1_000)
                writePending.set(false)
                writeIndexNow()
            }
        }
    }

    @Synchronized
    private fun writeIndexNow() {
        try {
            val array = JSONArray()
            _downloaded.value.values.forEach { array.put(toJson(it)) }
            val temp = File(dir, "index.json.tmp")
            temp.writeText(array.toString())
            indexFile.delete()
            temp.renameTo(indexFile)
        } catch (e: IOException) {
            // The files stay; the list is rebuilt from what could be saved next time
        }
    }

    private fun toJson(entry: DownloadedSong): JSONObject {
        val s = entry.song
        return JSONObject()
            .put("id", s.id).put("title", s.title).put("artist", s.artist).put("album", s.album)
            .put("albumId", s.albumId).put("artistId", s.artistId)
            .put("track", s.track).put("disc", s.disc).put("duration", s.durationSeconds)
            .put("suffix", s.suffix).put("bitRate", s.bitRate).put("coverArt", s.coverArt)
            .put("starred", s.starred).put("rating", s.userRating).put("genre", s.genre)
            .put("file", entry.fileName).put("bytes", entry.bytes)
            .also { json ->
                s.replayGain?.let { rg ->
                    json.put(
                        "rg",
                        JSONObject()
                            .put("trackGain", rg.trackGain ?: JSONObject.NULL)
                            .put("albumGain", rg.albumGain ?: JSONObject.NULL)
                            .put("trackPeak", rg.trackPeak ?: JSONObject.NULL)
                            .put("albumPeak", rg.albumPeak ?: JSONObject.NULL)
                            .put("fallbackGain", rg.fallbackGain ?: JSONObject.NULL)
                    )
                }
            }
    }

    private fun fromJson(j: JSONObject): DownloadedSong {
        fun number(o: JSONObject, name: String): Double? = if (o.has(name) && !o.isNull(name)) o.optDouble(name) else null
        return DownloadedSong(
            song = Song(
                id = j.getString("id"),
                title = j.optString("title"),
                artist = j.optString("artist"),
                album = j.optString("album"),
                albumId = j.optString("albumId").ifEmpty { null }.takeIf { !j.isNull("albumId") },
                artistId = j.optString("artistId").ifEmpty { null }.takeIf { !j.isNull("artistId") },
                track = j.optInt("track"),
                disc = j.optInt("disc", 1),
                durationSeconds = j.optInt("duration"),
                suffix = j.optString("suffix"),
                bitRate = j.optInt("bitRate"),
                coverArt = j.optString("coverArt").ifEmpty { null }.takeIf { !j.isNull("coverArt") },
                starred = j.optBoolean("starred"),
                userRating = j.optInt("rating"),
                genre = j.optString("genre"),
                replayGain = j.optJSONObject("rg")?.let { rg ->
                    ReplayGain(
                        trackGain = number(rg, "trackGain"),
                        albumGain = number(rg, "albumGain"),
                        trackPeak = number(rg, "trackPeak"),
                        albumPeak = number(rg, "albumPeak"),
                        fallbackGain = number(rg, "fallbackGain")
                    )
                }
            ),
            fileName = j.getString("file"),
            bytes = j.optLong("bytes")
        )
    }

    companion object {
        @Volatile
        private var instance: DownloadManager? = null

        fun get(context: Context): DownloadManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
