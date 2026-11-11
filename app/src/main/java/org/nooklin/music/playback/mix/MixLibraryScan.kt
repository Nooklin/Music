package org.nooklin.music.playback.mix

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.data.server.ServerSession
import java.io.File

/**
 * Scans the downloaded songs for AutoMix in one go (tempo, key and beat grid), so that it does not have to happen while
 * a song plays. The same work as the scan during playback ([MixLibrary.scan]); songs that are scanned already are skipped,
 * and songs that are not scanned are still handled by the player itself when they come up. Runs while the app lives.
 */
object MixLibraryScan {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var job: Job? = null
    private var library: MixLibrary? = null

    @Volatile
    private var stopRequested = false

    /** (scanned, downloaded): how many of the downloaded songs have been scanned. */
    fun progress(context: Context): Flow<Pair<Int, Int>> {
        val app = context.applicationContext
        MixFacts.preload(app)
        return combine(DownloadManager.get(app).downloaded, MixFacts.version) { downloaded, _ ->
            // 0.0.97: songs longer than the decoder takes are left out of both numbers (they can never be scanned)
            val scannable = downloaded.filter { it.value.song.durationSeconds <= PcmDecoder.MAX_SECONDS }
            scannable.keys.count { MixFacts.known(it) } to scannable.size
        }
    }

    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        val downloads = DownloadManager.get(app)
        val lib = library ?: MixLibrary(app, downloads, ServerSession.get(app), scope).also { library = it }
        stopRequested = false
        _running.value = true
        job = scope.launch {
            try {
                lib.scan(downloads.downloaded.value.keys.toList(), localOnly = true) { stopRequested }
            } finally {
                _running.value = false
            }
        }
    }

    /**
     * Deletes everything the scan made: the beat grids, the song facts (tempo, key, energy) and the measured singing. The scan is
     * stopped first. The older whole-song analysis (used for the order of a queue) stays. Songs are scanned again after this.
     */
    fun wipe(context: Context) {
        val app = context.applicationContext
        stopRequested = true
        val running = job
        scope.launch {
            running?.cancelAndJoin()
            withContext(Dispatchers.IO) {
                File(app.filesDir, "automix_maps").listFiles()?.forEach { it.delete() }
                MixFacts.preload(app)
                MixFacts.clear(app)
                MixVocals.clear(app)
            }
            _running.value = false
        }
    }

    /** Stops after the song that is being scanned. */
    fun stop() {
        stopRequested = true
    }
}
