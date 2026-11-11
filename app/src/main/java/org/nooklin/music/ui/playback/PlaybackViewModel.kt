package org.nooklin.music.ui.playback

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.playback.PlaybackSettings
import org.nooklin.music.data.playback.PlaybackSettingsValues
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.data.playback.QueueEntry
import org.nooklin.music.playback.EXTRA_BPM
import org.nooklin.music.playback.EXTRA_PINNED
import org.nooklin.music.playback.EXTRA_UPCOMING
import org.nooklin.music.playback.EXTRA_UPCOMING_FOR
import org.nooklin.music.playback.PlaybackService
import org.nooklin.music.playback.beat.BeatMixStore
import org.nooklin.music.playback.beat.MixOrder
import org.nooklin.music.playback.mix.MixMode
import org.nooklin.music.playback.mix.MixModes
import org.nooklin.music.playback.taste.TasteStore
import org.nooklin.music.playback.toMediaItem

data class QueueRow(val id: String, val title: String, val artist: String, val coverId: String?, val albumId: String? = null)

data class PlayerState(
    val mediaId: String = "",
    val albumId: String? = null,
    val artistId: String? = null,
    val bitRate: Int = 0,
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverId: String? = null,
    val format: String = "",
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueRow> = emptyList(),
    val index: Int = 0,
    // Positions in the queue of the songs that follow, in the order they will play (follows shuffle)
    val upcoming: List<Int> = emptyList(),
    val error: String? = null
)

/** Connects the screen to the playback service. All playing happens in the service, not here. */
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = PlaybackSettings(application)
    private var controller: MediaController? = null
    private var tickJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    // 0.0.147: the position ticks twice a second; in PlayerState it redrew every screen that reads the state each time
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    val settings: StateFlow<PlaybackSettingsValues> = settingsStore.values
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackSettingsValues())

    // Songs that wait for the connection to the service when the user taps before it is ready
    private var pending: (() -> Unit)? = null

    init {
        val token = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        val future = MediaController.Builder(application, token)
            .setListener(object : MediaController.Listener {
                // The service tells which songs follow, in the order it really plays them
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    refresh()
                }
            })
            .buildAsync()
        future.addListener({
            val connected = try {
                future.get()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not start the player.") }
                null
            }
            if (connected != null) {
                controller = connected
                connected.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        // 0.0.147: the rows of the queue and the order of the songs that follow only change with these events
                        refresh(
                            rebuildQueue = events.containsAny(
                                Player.EVENT_TIMELINE_CHANGED,
                                Player.EVENT_MEDIA_ITEM_TRANSITION,
                                Player.EVENT_MEDIA_METADATA_CHANGED,
                                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
                            )
                        )
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val reason = error.cause?.message ?: error.errorCodeName
                        _state.update { it.copy(error = "Playback failed: $reason") }
                    }
                })
                refresh()
                pending?.invoke()
                pending = null
            }
        }, ContextCompat.getMainExecutor(application))
    }

    private fun refresh(rebuildQueue: Boolean = true) {
        val c = controller ?: return
        val item = c.currentMediaItem
        val meta = item?.mediaMetadata
        val duration = if (c.duration != C.TIME_UNSET) c.duration else (meta?.durationMs ?: 0L)
        _state.update { old ->
            old.copy(
                mediaId = item?.mediaId.orEmpty(),
                albumId = meta?.extras?.getString("album_id")?.ifEmpty { null },
                artistId = meta?.extras?.getString("artist_id")?.ifEmpty { null },
                bitRate = meta?.extras?.getInt("bitrate") ?: 0,
                hasMedia = item != null,
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                title = meta?.title?.toString().orEmpty(),
                artist = meta?.artist?.toString().orEmpty(),
                album = meta?.albumTitle?.toString().orEmpty(),
                coverId = meta?.extras?.getString("cover")?.ifEmpty { null },
                format = meta?.extras?.getString("suffix").orEmpty().uppercase(),
                durationMs = duration.coerceAtLeast(0L),
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                queue = if (rebuildQueue) (0 until c.mediaItemCount).map { rowOf(c.getMediaItemAt(it)) } else old.queue,
                index = c.currentMediaItemIndex,
                upcoming = if (rebuildQueue) upcomingIndices(c) else old.upcoming,
                // A good state clears an old error
                error = if (c.playbackState == Player.STATE_READY) null else old.error
            )
        }
        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
        tickJob?.cancel()
        if (c.isPlaying) {
            tickJob = viewModelScope.launch {
                while (true) {
                    delay(500)
                    _positionMs.value = c.currentPosition.coerceAtLeast(0L)
                }
            }
        }
    }

    private fun upcomingIndices(c: MediaController): List<Int> {
        // The order the player really plays, as the service published it for this queue and this song
        val extras = c.sessionExtras
        val published = extras.getIntArray(EXTRA_UPCOMING)
        val forQueue = extras.getIntArray(EXTRA_UPCOMING_FOR)
        if (published != null && forQueue != null && forQueue.size == 2 &&
            forQueue[0] == c.currentMediaItemIndex && forQueue[1] == c.mediaItemCount
        ) {
            return published.filter { it in 0 until c.mediaItemCount }
        }
        val timeline = c.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val result = mutableListOf<Int>()
        var next = timeline.getNextWindowIndex(c.currentMediaItemIndex, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        while (next != C.INDEX_UNSET && result.size < MAX_UPCOMING) {
            result += next
            next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return result
    }

    private fun rowOf(item: MediaItem) = QueueRow(
        id = item.mediaId,
        title = item.mediaMetadata.title?.toString().orEmpty(),
        artist = item.mediaMetadata.artist?.toString().orEmpty(),
        coverId = item.mediaMetadata.extras?.getString("cover")?.ifEmpty { null },
        albumId = item.mediaMetadata.extras?.getString("album_id")?.ifEmpty { null }
    )

    /** Replaces the queue with [songs] and starts at [startIndex]. */
    /** [discovery] true: this is the Discovery Station, which keeps adding songs; any other start ends it. */
    fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean, discovery: Boolean = false) {
        if (songs.isEmpty()) return
        viewModelScope.launch { settingsStore.setDiscovery(discovery) }
        if (shuffle && settings.value.autoMix && settings.value.beatMix) {
            // With beat mixing on, a shuffle is a mix: a random start, then each song suits the one before it. The player
            // itself does not shuffle then, because that would undo the order.
            viewModelScope.launch {
                val ordered = withContext(Dispatchers.Default) {
                    BeatMixStore.preload(getApplication<Application>())
                    org.nooklin.music.playback.mix.MixFacts.preload(getApplication<Application>())
                    MixOrder.shuffled(songs)
                }
                startQueue(ordered, 0, false)
            }
            return
        }
        startQueue(songs, startIndex, shuffle)
    }

    /**
     * A tap on a song. With AutoMix on (and a mode chosen) the song starts and a new mix is made around it; otherwise the
     * list plays from that song.
     */
    fun playSongFrom(songs: List<Song>, index: Int) {
        val song = songs.getOrNull(index) ?: return
        val mode = if (settings.value.autoMix) MixMode.of(settings.value.autoMixMode) else null
        if (mode == null) {
            playSongs(songs, index, false)
        } else {
            viewModelScope.launch { settingsStore.setDiscovery(false) }
            chooseAutoMixMode(mode, startWith = song)
        }
    }

    /**
     * The Radio block on Home with AutoMix on: an AutoMix mix of the downloaded songs of one artist, started with one of them.
     * With fewer than [MIN_ARTIST_MIX] downloaded songs of the artist a normal AutoMix of all downloaded songs is made instead.
     */
    fun startArtistMix(artistId: String) {
        val app = getApplication<Application>()
        val mode = MixMode.of(settings.value.autoMixMode) ?: MixMode.MIXED
        val mine = DownloadManager.get(app).downloaded.value.values.map { it.song }.filter { it.artistId == artistId }
        if (mine.size < MIN_ARTIST_MIX) {
            Toast.makeText(app, "AutoMix is on and too few songs of this artist are downloaded, so no radio AutoMix is made. A normal AutoMix of your downloaded songs starts instead.", Toast.LENGTH_LONG).show()
            chooseAutoMixMode(mode, announce = true)
        } else {
            viewModelScope.launch { settingsStore.setDiscovery(false) }
            chooseAutoMixMode(mode, startWith = mine.random(), onlyArtistId = artistId)
        }
    }

    private fun startQueue(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        val items = songs.map { song ->
            QueueEntry(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                coverArt = song.coverArt,
                suffix = song.suffix,
                durationMs = song.durationSeconds * 1000L,
                albumId = song.albumId,
                artistId = song.artistId,
                bitRate = song.bitRate,
                bpm = song.bpm,
                replayGain = song.replayGain
            ).toMediaItem()
        }
        val start = if (shuffle) songs.indices.random() else startIndex.coerceIn(0, songs.lastIndex)
        whenConnected {
            val c = controller ?: return@whenConnected
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(items, start, 0L)
            c.prepare()
            c.play()
        }
    }

    private fun whenConnected(action: () -> Unit) {
        if (controller != null) action() else pending = action
    }

    private fun mediaItemOf(song: Song): MediaItem = QueueEntry(
        id = song.id,
        title = song.title,
        artist = song.artist,
        album = song.album,
        coverArt = song.coverArt,
        suffix = song.suffix,
        durationMs = song.durationSeconds * 1000L,
        albumId = song.albumId,
        artistId = song.artistId,
        bitRate = song.bitRate,
                bpm = song.bpm,
        replayGain = song.replayGain
    ).toMediaItem()

    /** Puts [songs] right after the song that plays now. With an empty queue they start playing. */
    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        val c = controller
        if (c == null || c.mediaItemCount == 0) {
            playSongs(songs, 0, false)
            return
        }
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { pinned(mediaItemOf(it)) })
        djOrderIfMixing()
    }

    /** Puts [songs] at the end of the queue. With an empty queue they start playing. */
    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        val c = controller
        if (c == null || c.mediaItemCount == 0) {
            playSongs(songs, 0, false)
            return
        }
        c.addMediaItems(songs.map { pinned(mediaItemOf(it)) })
        djOrderIfMixing()
    }

    /** Marks a song the user put in the queue by hand: the DJ order keeps it in place. */
    private fun pinned(item: MediaItem): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply { putBoolean(EXTRA_PINNED, true) }
        return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }

    private fun djOrderIfMixing() {
        val s = settings.value
        if (s.autoMix && s.beatMix) djOrderUpcoming()
    }

    /**
     * Applies an edited queue. [order] holds the positions (as they are now) of the songs that stay, in the order they
     * must end up in. Songs whose position is not in [order] are removed.
     */
    fun applyQueueEdit(order: List<Int>) {
        val c = controller ?: return
        val count = c.mediaItemCount
        val keep = order.toSet()
        if (order.isEmpty() || keep.size != order.size || order.any { it !in 0 until count }) return
        // Removals first, from the back so the other positions stay valid
        for (i in (0 until count).reversed()) {
            if (i !in keep) c.removeMediaItem(i)
        }
        // The positions that are left, in their current order; each song is moved to its place one by one
        val working = (0 until count).filter { it in keep }.toMutableList()
        for (target in order.indices) {
            val from = working.indexOf(order[target])
            if (from != target) {
                c.moveMediaItem(from, target)
                working.add(target, working.removeAt(from))
            }
        }
    }

    /** Takes one song out of the queue. The song that plays now cannot be removed this way. */
    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount || index == c.currentMediaItemIndex) return
        c.removeMediaItem(index)
    }

    /** Moves one song in the queue to another position. */
    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount || from == to) return
        c.moveMediaItem(from, to)
    }

    /**
     * Puts the downloaded songs after the song that plays now in a DJ order: each suits the one before it in tempo, key and
     * loudness, without the same artist, genre or tempo over and over (see [MixOrder]). The playing song, the songs before
     * it, the songs that are not downloaded and the songs the user put in by hand stay where they are, and the order is made
     * around them. Not done with shuffle on, or when the queue changed meanwhile.
     */
    private fun djOrderUpcoming() {
        val c = controller ?: return
        if (c.shuffleModeEnabled) return
        val first = c.currentMediaItemIndex + 1
        if (c.mediaItemCount - first < 2) return
        val seed = c.currentMediaItem?.mediaId ?: return
        val taste = TasteStore.get(getApplication<Application>())
        val downloaded = DownloadManager.get(getApplication<Application>()).downloaded.value
        val ids = (first until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        val fixed = (first until c.mediaItemCount).map { i ->
            val m = c.getMediaItemAt(i)
            m.mediaMetadata.extras?.getBoolean(EXTRA_PINNED) == true || m.mediaId !in downloaded
        }
        if (fixed.count { !it } < 2) return
        val items = (first until c.mediaItemCount).map { i ->
            val m = c.getMediaItemAt(i)
            MixOrder.Item(
                m.mediaId,
                m.mediaMetadata.artist?.toString().orEmpty(),
                m.mediaMetadata.extras?.getInt(EXTRA_BPM) ?: 0,
                taste.entry(m.mediaId)?.genre.orEmpty(),
                m.mediaMetadata.albumTitle?.toString().orEmpty()
            )
        }
        // 0.0.135: the song playing now counts for the artist and album rule
        val playing = c.currentMediaItem?.mediaMetadata?.let {
            MixOrder.Item(seed, it.artist?.toString().orEmpty(), 0, "", it.albumTitle?.toString().orEmpty())
        }
        viewModelScope.launch {
            val ordered = withContext(Dispatchers.Default) {
                BeatMixStore.preload(getApplication<Application>())
                org.nooklin.music.playback.mix.MixFacts.preload(getApplication<Application>())
                MixOrder.queueAround(seed, items, fixed, listOfNotNull(playing))
            }
            val cc = controller ?: return@launch
            val now = (cc.currentMediaItemIndex + 1 until cc.mediaItemCount).map { cc.getMediaItemAt(it).mediaId }
            if (cc.currentMediaItemIndex + 1 != first || now != ids) return@launch
            val at = HashMap<MixOrder.Item, Int>()
            items.forEachIndexed { i, item -> at[item] = first + i }
            applyQueueEdit((0 until first).toList() + ordered.map { at.getValue(it) })
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            // A restored queue is not loaded yet: prepare it first
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    /** Goes to the start of the song first, and to the previous song when already near the start. */
    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    /** Removes everything after the song that plays now. */
    fun clearUpcoming() {
        val c = controller ?: return
        val first = c.currentMediaItemIndex + 1
        if (first < c.mediaItemCount) c.removeMediaItems(first, c.mediaItemCount)
    }

    /** Turns shuffle on and starts with a random one of the songs that come next. */
    fun shuffleAndPlayNext() {
        val c = controller ?: return
        c.shuffleModeEnabled = true
        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) {
        viewModelScope.launch { settingsStore.setSpeed(speed) }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setSkipSilence(enabled) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch { settingsStore.setCrossfadeSeconds(seconds) }
    }

    fun setReplayGainMode(mode: Int) {
        viewModelScope.launch { settingsStore.setReplayGainMode(mode) }
    }

    fun setStemMix(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setStemMix(enabled) }
    }

    /**
     * Picks a mode of the AutoMix menu: AutoMix goes on, the mode is kept (also after a restart), and everything after the song
     * that plays now is replaced by a list for the mode. Only downloaded songs that were analysed are used; the player's own
     * shuffle goes off so that it does not undo the order.
     */
    fun chooseAutoMixMode(
        mode: MixMode,
        zone: Int = settings.value.autoMixZone,
        genres: Set<String> = settings.value.autoMixGenres,
        announce: Boolean = false,
        startWith: Song? = null,
        onlyArtistId: String? = null
    ) {
        val app = getApplication<Application>()
        val c = controller
        // With startWith the song that was tapped is the start of a new queue: it is the seed and nothing else is kept
        val seed = startWith?.id ?: c?.currentMediaItem?.mediaId
        // Songs the user put in the queue by hand (pinned) stay where they are; the new list is added after them
        val firstUpcoming = if (c == null || c.currentMediaItem == null) 0 else c.currentMediaItemIndex + 1
        val pinnedIds = if (c == null || startWith != null) emptySet() else (firstUpcoming until c.mediaItemCount).filter { c.getMediaItemAt(it).mediaMetadata.extras?.getBoolean(EXTRA_PINNED) == true }.map { c.getMediaItemAt(it).mediaId }.toSet()
        // 0.0.137: only the last RECENT_PLAYED songs that played (and the one playing) are left out, not everything that ever sat before it in
        // the queue; in a long queue that was nearly the whole library, so a mode could choose from a few leftovers (log 0.0.135: 24 of 226)
        val exclude = if (startWith != null) setOf(startWith.id) else (if (c == null || c.currentMediaItem == null) emptySet() else (maxOf(0, c.currentMediaItemIndex - RECENT_PLAYED + 1)..c.currentMediaItemIndex).map { c.getMediaItemAt(it).mediaId }.toSet()) + pinnedIds
        viewModelScope.launch {
            // 0.0.139: the screen shows that a list is being made, and the log shows where the seconds go
            val t0 = android.os.SystemClock.elapsedRealtime()
            _mixBusy.value = true
            try {
            settingsStore.setAutoMixMode(mode.id, zone, genres)
            settingsStore.setAutoMix(true)
            val songs = DownloadManager.get(app).downloaded.value.values.map { it.song }.filter { onlyArtistId == null || it.artistId == onlyArtistId }
            val list = withContext(Dispatchers.Default) {
                // 0.0.143: log 0.0.142 showed 16 s for the first list and 0.2 s for the next ones; these two numbers show which step it is
                val tPrep0 = android.os.SystemClock.elapsedRealtime()
                MixModes.prepare(app)
                val prepMs = android.os.SystemClock.elapsedRealtime() - tPrep0
                val built = MixModes.build(mode, zone, genres, songs, seed, exclude, MixModes.LIST_SIZE)
                built
            }
            val tList = android.os.SystemClock.elapsedRealtime() - t0
            prefetchCovers(app, list.map { it.coverArt })
            if (list.isEmpty()) {
                Toast.makeText(app, "No downloaded, scanned songs fit ${mode.label}. Scan the library in Settings > AutoMix.", Toast.LENGTH_LONG).show()
                // The song that was tapped still plays
                if (startWith != null) startQueue(listOf(startWith), 0, false)
                return@launch
            }
            val cc = controller
            if (startWith != null) {
                startQueue(listOf(startWith) + list, 0, false)
            } else if (cc == null || cc.mediaItemCount == 0 || cc.currentMediaItem == null) {
                startQueue(list, 0, false)
            } else {
                cc.shuffleModeEnabled = false
                val first = cc.currentMediaItemIndex + 1
                // From the back, so the indexes stay right; pinned songs stay
                for (i in cc.mediaItemCount - 1 downTo first) {
                    if (cc.getMediaItemAt(i).mediaMetadata.extras?.getBoolean(EXTRA_PINNED) != true) cc.removeMediaItem(i)
                }
                cc.addMediaItems(list.map { MixModes.entryOf(it).toMediaItem() })
                // 0.0.153: from the Home card the music starts when it was paused or had run out; a song that plays keeps playing
                if (announce) {
                    if (cc.playbackState == androidx.media3.common.Player.STATE_ENDED) cc.seekToNextMediaItem()
                    cc.prepare()
                    cc.play()
                }
            }
            // 0.0.151: from the Home card the result is told, so a new mix is visible
            if (announce) Toast.makeText(app, "New AutoMix started: ${mode.label}, ${list.size} songs", Toast.LENGTH_SHORT).show()
            } finally {
                _mixBusy.value = false
            }
        }
    }

    /** True while a mode list is being made. */
    private val _mixBusy = MutableStateFlow(false)
    val mixBusy: StateFlow<Boolean> = _mixBusy.asStateFlow()

    /** Loads the covers of the first songs of a new list in the background, so the queue rows find them in memory. */
    private fun prefetchCovers(app: Application, ids: List<String?>) {
        viewModelScope.launch(Dispatchers.IO) {
            val started = android.os.SystemClock.elapsedRealtime()
            val session = org.nooklin.music.data.server.ServerSession.get(app)
            ids.filterNotNull().distinct().take(PREFETCH_COVERS).map { id ->
                async {
                    val key = org.nooklin.music.data.library.CoverCache.key(id, 128)
                    if (org.nooklin.music.data.library.CoverCache.get(key) == null) {
                        session.cover(id, 128)?.let { org.nooklin.music.data.library.CoverCache.put(key, it) }
                    }
                }
            }.awaitAll()
        }
    }

    fun setBeatMix(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setBeatMix(enabled) }
    }

    fun setAutoMix(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoMix(enabled) }
        // Switched on: the downloaded songs that come next are put in a DJ order
        if (enabled && settings.value.beatMix) djOrderUpcoming()
    }

    fun setAutoMixNoticeHidden(hidden: Boolean) {
        viewModelScope.launch { settingsStore.setAutoMixNoticeHidden(hidden) }
    }

    fun setSmartFades(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setSmartFades(enabled) }
    }

    fun stopDiscovery() {
        viewModelScope.launch { settingsStore.setDiscovery(false) }
    }

    fun setAutoPlay(enabled: Boolean) {
        // AutoPlay only works with shuffle and repeat off, so switching it on switches those off
        if (enabled) {
            controller?.let {
                it.shuffleModeEnabled = false
                it.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
        viewModelScope.launch { settingsStore.setAutoPlay(enabled) }
    }

    fun setPrivateMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setPrivateMode(enabled) }
    }

    fun setVolumeLimitPercent(percent: Int) {
        viewModelScope.launch { settingsStore.setVolumeLimitPercent(percent) }
    }

    fun sleepAfterMinutes(minutes: Int) {
        viewModelScope.launch { settingsStore.setSleepAfterMinutes(minutes) }
    }

    fun sleepAtTrackEnd() {
        viewModelScope.launch { settingsStore.setSleepAtTrackEnd() }
    }

    fun clearSleep() {
        viewModelScope.launch { settingsStore.clearSleep() }
    }

    private companion object {
        const val MAX_UPCOMING = 300
        const val RECENT_PLAYED = 20  // 0.0.137: this many played songs are left out when a mode of the AutoMix menu is chosen
        const val PREFETCH_COVERS = 12  // 0.0.139: covers loaded ahead for a new mode list
        const val MIN_ARTIST_MIX = 5  // fewer downloaded songs of an artist than this: no artist AutoMix from Home
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}
