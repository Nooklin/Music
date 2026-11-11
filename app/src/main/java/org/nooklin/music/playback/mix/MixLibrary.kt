package org.nooklin.music.playback.mix

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.data.server.ServerSession

/**
 * The songs the engine works with: the file of a song (the download, or a copy fetched now), its decoded audio in
 * native memory, and its beat grid (kept on disk between runs). Everything here is called from the main thread; the
 * heavy work runs on other threads.
 */
class MixLibrary(
    private val context: Context,
    private val downloads: DownloadManager,
    private val session: ServerSession,
    private val scope: CoroutineScope
) {
    class Song(val id: String, val handle: Long, val frames: Long) {
        /** The beat grid the engine has been given for this song (null: none yet). */
        var map: DoubleArray? = null
    }

    private val songs = HashMap<String, Song>()
    private val decoding = HashMap<String, Deferred<Song?>>()
    private val analysing = HashMap<String, Deferred<DoubleArray?>>()
    private val failedAt = HashMap<String, Long>()
    private val failedCount = HashMap<String, Int>()  // 0.0.127: a song that keeps failing is tried less and less often (log 0.0.126: the same song every change)
    private val skippedLong = HashSet<String>()  // 0.0.133: songs the tempo scan skipped for their length (logged once)
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }
    private val cacheDir = File(context.cacheDir, "automix").apply { mkdirs() }
    private val mapDir = File(context.filesDir, "automix_maps").apply { mkdirs() }

    fun peek(id: String): Song? = songs[id]

    /** True when this song failed to decode a short while ago (it is not tried again at once). */
    fun failedRecently(id: String): Boolean {
        val at = failedAt[id] ?: return false
        return SystemClock.elapsedRealtime() - at < RETRY_MS * (1L shl min(5, (failedCount[id] ?: 1) - 1))
    }

    private fun noteFailed(id: String) {
        failedAt[id] = SystemClock.elapsedRealtime()
        failedCount[id] = (failedCount[id] ?: 0) + 1
    }

    /** Artist and title of a downloaded song for the log, or the id when it is not downloaded. */
    private fun nameOf(id: String): String = downloads.downloaded.value[id]?.song?.let { "${it.artist} - ${it.title}" } ?: id

    /** The decoded song, decoding it first when needed. Null when that is not possible. */
    suspend fun decoded(id: String): Song? {
        songs[id]?.let { return it }
        if (failedRecently(id)) return null
        val job = decoding.getOrPut(id) {
            scope.async {
                val file = fileFor(id)
                val r = if (file != null) PcmDecoder.decode(file.path) else null
                if (r == null) {
                    noteFailed(id)
                    null
                } else {
                    Song(id, r.handle, r.frames).also { songs[id] = it }
                }
            }
        }
        return try {
            job.await()
        } finally {
            if (job.isCompleted) decoding.remove(id)
        }
    }

    /**
     * The beat grid of [song]: from disk when it was analysed before, otherwise analysed now (one song at a time).
     * Does not store it in [Song.map]; that is done once the engine has it.
     */
    suspend fun mapOf(song: Song): DoubleArray? {
        val job = analysing.getOrPut(song.id) {
            scope.async {
                (readMap(song.id)?.also { MixFacts.record(context, song.id, it) }) ?: gate.withPermit {
                    val t0 = SystemClock.elapsedRealtime()
                    val m = withContext(Dispatchers.Default) {
                        org.nooklin.music.playback.beat.BeatMixStore.preload(context)
                        MixTempo.fixed(song.id, song.handle, NativeMix.analyze(song.handle), genreOf(song.id))
                    }
                    // A song that was freed during the analysis gives an empty grid that is not kept
                    if (MixMap.analysed(m) && songs[song.id] === song) writeMap(song.id, m)
                    if (MixMap.analysed(m)) MixFacts.record(context, song.id, m)
                    if (MixMap.analysed(m)) m else null
                }
            }
        }
        return try {
            job.await()
        } finally {
            if (job.isCompleted) analysing.remove(song.id)
        }
    }

    /**
     * Notes the tempo and key of the songs [ids] that were not analysed yet, one at a time, so the ordering of a queue
     * knows them. A song that is not on the device is fetched into the cache first (the same copy the engine uses when it
     * plays the song, so for songs that will be played nothing is fetched twice). The decoded audio is freed at once.
     */
    suspend fun scan(ids: List<String>, localOnly: Boolean = false, shouldStop: () -> Boolean) {
        MixFacts.preload(context)
        withContext(Dispatchers.Default) { MixTempo.recheckOctaves(context); MixTempo.recheckUnsure(context) }  // 0.0.105: fast songs whose grid is at half tempo are analysed again
        // 0.0.133: a downloaded song longer than PcmDecoder.MAX_SECONDS cannot be decoded; it is not tried at all (log 0.0.132: the
        // same song failed at every change). One line in the log per song per session.
        val tooLong = ids.distinct().filter { id -> (downloads.downloaded.value[id]?.song?.durationSeconds ?: 0) > PcmDecoder.MAX_SECONDS }
        for (id in tooLong) {
        }
        val todo = ids.distinct().filter { !MixFacts.known(it) && !failedRecently(it) && songs[it] == null && it !in tooLong }
        for (id in todo) {
            if (shouldStop()) return
            // Another part of the app may have analysed it meanwhile
            if (MixFacts.known(id)) continue
            val t0 = SystemClock.elapsedRealtime()
            // localOnly: only the downloaded file, nothing is fetched from the server
            val file = if (localOnly) downloads.fileFor(id) else fileFor(id)
            if (file == null) {
                noteFailed(id)
                continue
            }
            gate.withPermit {
                // Not cancelled halfway: the decoded audio must be freed
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Default) {
                    val r = PcmDecoder.decode(file.path)
                    if (r == null) {
                        noteFailed(id)
                    } else {
                        try {
                            org.nooklin.music.playback.beat.BeatMixStore.preload(context)
                            val m = MixTempo.fixed(id, r.handle, NativeMix.analyze(r.handle), genreOf(id))
                            if (MixMap.analysed(m)) {
                                writeMap(id, m)
                                MixFacts.record(context, id, m)
                            }
                        } finally {
                            NativeMix.freeBuffer(r.handle)
                        }
                    }
                }
            }
        }
    }

    /** Frees every decoded song except those in [keep]. The engine keeps its own hold on a song it still plays. */
    fun retain(keep: Set<String>) {
        val drop = songs.keys.filter { it !in keep }
        for (id in drop) {
            songs.remove(id)?.let { NativeMix.freeBuffer(it.handle) }
        }
        for ((id, job) in decoding.toMap()) if (id !in keep) job.cancel()
    }

    fun releaseAll() {
        retain(emptySet())
        for (job in analysing.values) job.cancel()
        analysing.clear()
    }

    // ---- files ---------------------------------------------------------------------------------------------------------

    private suspend fun fileFor(id: String): File? {
        downloads.fileFor(id)?.let { return it }
        val cached = File(cacheDir, safe(id))
        if (cached.exists() && cached.length() > 0L) {
            cached.setLastModified(System.currentTimeMillis())
            return cached
        }
        return withContext(Dispatchers.IO) {
            try {
                val url = session.streamUrl(id) ?: return@withContext null
                val part = File(cacheDir, safe(id) + ".part")
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body.byteStream().use { input -> part.outputStream().use { input.copyTo(it) } }
                }
                if (!part.renameTo(cached)) return@withContext null
                trimCache()
                cached
            } catch (e: Exception) {
                Log.w(TAG, "fetching a song failed: ${e.javaClass.simpleName}")
                null
            }
        }
    }

    private fun trimCache() {
        val files = cacheDir.listFiles()?.filter { !it.name.endsWith(".part") }?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        for (f in files) {
            total += f.length()
            if (total > CACHE_BYTES && f.lastModified() < System.currentTimeMillis() - 60_000L) f.delete()
        }
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    // ---- grids on disk -------------------------------------------------------------------------------------------------

    private fun readMap(id: String): DoubleArray? = readMapFile(mapDir, id)

    private fun genreOf(id: String): String? = org.nooklin.music.playback.taste.TasteStore.get(context).entry(id)?.genre

    private fun writeMap(id: String, map: DoubleArray) {
        try {
            DataOutputStream(File(mapDir, safe(id) + ".map").outputStream().buffered()).use { out ->
                out.writeInt(MAP_VERSION)
                out.writeInt(map.size)
                for (d in map) out.writeDouble(d)
            }
        } catch (e: Exception) {
            Log.w(TAG, "saving a song grid failed: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        /** The grid of a song as saved on disk, or null when there is none (or it is of an older analysis). */
        fun readMapFile(mapDir: File, id: String): DoubleArray? {
            val file = File(mapDir, id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".map")
            if (!file.exists()) return null
            return try {
                DataInputStream(file.inputStream().buffered()).use { input ->
                    if (input.readInt() != MAP_VERSION) return null
                    val n = input.readInt()
                    if (n < 8 || n > 200_000) return null
                    DoubleArray(n) { input.readDouble() }
                }
            } catch (e: Exception) {
                null
            }
        }

        private const val TAG = "NooklinMix"
        private const val RETRY_MS = 60_000L
        private const val CACHE_BYTES = 300L * 1024 * 1024

        // One decode and analysis at a time, also when two MixLibrary objects work (the player and the scan of the library)
        private val gate = Semaphore(1)

        // Raise this when the analysis changes: older grids are then made again
        const val MAP_VERSION = 9  // 9: cue fields, phrase length, swing and edge energy (0.0.103); 8: energy relative to the 95th percentile instead of the median (0.0.102); 7: with the evidence for the tempo (0.0.80); 6: tempo level checked with the beat tracker (0.0.72); 5: with loudness and singing slot (0.0.62); 4: with sections (0.0.59); 3: with loudness and pitch classes (0.0.56); 2: steady-tempo grids; older ones are analysed again
    }
}
