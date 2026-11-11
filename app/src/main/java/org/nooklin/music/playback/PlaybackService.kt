@file:OptIn(UnstableApi::class)

package org.nooklin.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.IOException
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import org.nooklin.music.playback.beat.BassCutProcessor
import org.nooklin.music.playback.beat.BeatMixStore
import org.nooklin.music.playback.beat.MixOrder
import org.nooklin.music.playback.mix.MixConductor
import org.nooklin.music.playback.mix.MixMode
import org.nooklin.music.playback.mix.MixModes
import org.nooklin.music.playback.mix.NativeMix
import org.nooklin.music.playback.stems.StemTransitions
import org.nooklin.music.widget.NowPlayingWidget
import org.nooklin.music.data.playback.QueueEntry
import org.nooklin.music.data.server.Reply
import org.nooklin.music.data.library.LibraryApi
import org.nooklin.music.playback.taste.TasteEngine
import org.nooklin.music.playback.taste.TasteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.nooklin.music.MainActivity
import org.nooklin.music.airplay.AirPlayAudio
import org.nooklin.music.airplay.AirPlayController
import org.nooklin.music.airplay.PcmTapProcessor
import org.nooklin.music.data.playback.PlaybackSettings
import org.nooklin.music.data.playback.PlaybackSettingsValues
import org.nooklin.music.data.playback.QueueStore
import org.nooklin.music.data.playback.SavedQueue
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.data.server.ServerSession

// The most songs of the queue that the screen is told about
private const val UPCOMING_LIMIT = 300

// 0.0.147: when a mode, AutoPlay or the Discovery Station fills the queue, this many played songs stay in front of the song that plays
// (to go back to); once TRIM_STEP more are in front, the extra ones are taken off. A list the user made is never cut.
private const val KEEP_PLAYED = 20
private const val TRIM_STEP = 10
// The most ids kept of songs taken off the queue (they are kept so that a song does not come back before a new round)
private const val PLAYED_MEMORY = 2_000

/**
 * Plays the queue in the background with a media notification and lock screen controls. The screen talks to this
 * service through a MediaController. Everything is streamed from the user's own server as the original file.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var queueStore: QueueStore
    private lateinit var settings: PlaybackSettings
    private lateinit var serverSession: ServerSession

    private var current = PlaybackSettingsValues()
    private var sleepJob: Job? = null
    private var networkRetriesLeft = 1

    // The song that counts towards a play report, and whether it was reported already
    private var scrobbleId = ""
    private var scrobbled = false

    // The song the AutoPlay already looked for similar songs of
    private var djFor = ""
    private lateinit var crossfade: CrossfadeController
    private var conductor: MixConductor? = null
    private val taste by lazy { TasteStore.get(this) }
    private var lastDiscoveryAt = 0L

    // The cover on the widget, and the cover id it belongs to
    private var widgetCoverId = ""
    private var widgetCover: Bitmap? = null

    // 0.0.147: ids of the songs taken off the front of the queue (oldest first); the fillers leave them out until a new round starts
    private val playedAway = ArrayDeque<String>()
    private var trimming = false

    /**
     * 0.0.147: while a mode, AutoPlay or the Discovery Station fills the queue, the played songs in front are taken off, so the queue
     * does not grow for hours. Not with shuffle on, not while a crossfade runs (it remembers the place of the song as an index), and
     * not for a list the user made (repeat all would loop over a shorter list).
     */
    private fun trimPlayed(player: Player) {
        val modeFills = current.autoMix && MixMode.of(current.autoMixMode) != null
        val otherFills = (current.autoPlay || current.discovery) && player.repeatMode == Player.REPEAT_MODE_OFF
        if (!modeFills && !otherFills) return
        if (player.shuffleModeEnabled || crossfade.busy) return
        val extra = player.currentMediaItemIndex - KEEP_PLAYED
        if (extra < TRIM_STEP) return
        val ids = (0 until extra).map { player.getMediaItemAt(it).mediaId }
        trimming = true
        try {
            player.removeMediaItems(0, extra)
        } finally {
            trimming = false
        }
        crossfade.frontRemoved(extra)
        playedAway.addAll(ids)
        while (playedAway.size > PLAYED_MEMORY) playedAway.removeFirst()
    }

    /** Tells the screen which songs follow, in the order this player really plays them (also with shuffle on). */
    /** 0.0.136: the last songs of the queue (oldest first); songs added to the queue come after them. */
    private fun queueTail(player: Player, count: Int = 3): List<MixOrder.Item> {
        val n = player.mediaItemCount
        return (maxOf(0, n - count) until n).map { i ->
            val m = player.getMediaItemAt(i)
            MixOrder.Item(m.mediaId, m.mediaMetadata.artist?.toString().orEmpty(), 0, "", m.mediaMetadata.albumTitle?.toString().orEmpty())
        }
    }

    private fun publishUpcoming(player: Player) {
        val session = mediaSession ?: return
        val timeline = player.currentTimeline
        val upcoming = ArrayList<Int>()
        if (!timeline.isEmpty) {
            val shuffle = player.shuffleModeEnabled
            var next = timeline.getNextWindowIndex(player.currentMediaItemIndex, Player.REPEAT_MODE_OFF, shuffle)
            while (next != C.INDEX_UNSET && upcoming.size < UPCOMING_LIMIT) {
                upcoming.add(next)
                next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, shuffle)
            }
        }
        session.setSessionExtras(Bundle().apply {
            putIntArray(EXTRA_UPCOMING, upcoming.toIntArray())
            putIntArray(EXTRA_UPCOMING_FOR, intArrayOf(player.currentMediaItemIndex, player.mediaItemCount))
        })
    }

    private fun updateWidget(player: Player) {
        val item = player.currentMediaItem
        val entry = item?.toQueueEntry()
        val playing = player.isPlaying
        scope.launch {
            val coverId = entry?.coverArt.orEmpty()
            if (coverId != widgetCoverId) {
                widgetCover = if (coverId.isEmpty()) null else serverSession.cover(coverId, 256)
                widgetCoverId = coverId
            }
            NowPlayingWidget.publish(
                this@PlaybackService, entry?.title.orEmpty(), entry?.artist.orEmpty(), playing, widgetCover
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        queueStore = QueueStore(this)
        settings = PlaybackSettings(this)
        serverSession = ServerSession.get(this)

        // Opens `nooklin://song/ID` as the downloaded file when there is one, otherwise as the real stream address,
        // made right now and never stored
        val downloads = DownloadManager.get(this)
        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(
                this,
                DefaultHttpDataSource.Factory()
                    .setUserAgent("NooklinMusic")
                    .setConnectTimeoutMs(8_000)
                    .setReadTimeoutMs(15_000)
            )
        ) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == "nooklin" && uri.host == "song") {
                val id = uri.lastPathSegment ?: throw IOException("Song without an id.")
                val local = downloads.fileFor(id)
                if (local != null) {
                    dataSpec.withUri(Uri.fromFile(local))
                } else {
                    val url = runBlocking { serverSession.streamUrl(id) }
                        ?: throw IOException("No server set up, or the saved password cannot be read.")
                    dataSpec.withUri(Uri.parse(url))
                }
            } else {
                dataSpec
            }
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        // Only the main player has the tap that feeds AirPlay; it passes the audio on untouched unless AirPlay is on
        val mainBass = BassCutProcessor()
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParameters: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
                .setAudioProcessors(arrayOf<androidx.media3.common.audio.AudioProcessor>(mainBass, PcmTapProcessor()))
                .build()
        }
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        // 0.0.131: the output chosen in the menu goes to the main player, the crossfade player (when it is made) and the Auto Mix engine
        AudioRoute.watch(this)
        scope.launch {
            AudioRoute.selectedId.collect {
                val device = AudioRoute.device(this@PlaybackService)
                player.setPreferredAudioDevice(device)
                withContext(Dispatchers.Default) { NativeMix.setOutputDevice(device?.id ?: 0) }
            }
        }
        // The short-lived player used for crossfade: it never takes audio focus, the main player keeps it
        crossfade = CrossfadeController(player, mainBass, { id -> downloads.fileFor(id) != null }) { bass ->
            // The short-lived player has the bass filter too, for the bass swap of a mix
            val secondaryFactory = object : DefaultRenderersFactory(this@PlaybackService) {
                override fun buildAudioSink(
                    context: android.content.Context,
                    enableFloatOutput: Boolean,
                    enableAudioOutputPlaybackParameters: Boolean
                ): AudioSink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
                    .setAudioProcessors(arrayOf<androidx.media3.common.audio.AudioProcessor>(bass))
                    .build()
            }
            ExoPlayer.Builder(this@PlaybackService, secondaryFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(audioAttributes, false)
                .build()
                .also { it.setPreferredAudioDevice(AudioRoute.device(this@PlaybackService)) }
        }
        crossfade.stems = StemTransitions(this)
        // Beat matched Auto Mix is played by the native engine; the player stays as the queue and the silent clock
        val mixConductor = MixConductor(this, player, crossfade, scope, downloads, serverSession) { item, positionMs ->
            recordTaste(item, positionMs, true)
        }
        conductor = mixConductor
        mixConductor.start()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // The session sees the player through a wrapper that gives the volume keys to AirPlay while it is on
        val airPlay = AirPlayController.get(this)
        val sessionPlayer = AirPlayVolumePlayer(player, airPlay)
        scope.launch {
            airPlay.state.collect { sessionPlayer.refresh() }
        }
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setBitmapLoader(CoverBitmapLoader(this))
            .setSessionActivity(openApp)
            .build()

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                // 0.0.120: shuffle stays off while AutoMix is on (random songs follow each other with poor changes): the button does nothing then
                if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) && current.autoMix && player.shuffleModeEnabled) {
                    player.shuffleModeEnabled = false
                    android.widget.Toast.makeText(this@PlaybackService, "Shuffle is off while AutoMix is on", android.widget.Toast.LENGTH_SHORT).show()
                }
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED
                    )
                ) {
                    saveQueue(player)
                }
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_MEDIA_METADATA_CHANGED
                    )
                ) {
                    updateWidget(player)
                }
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
                    )
                ) {
                    publishUpcoming(player)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 0.0.147: a new queue (or the song that plays was removed): the songs taken off the old one no longer count as played
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && !trimming) playedAway.clear()
                scrobbleId = mediaItem?.mediaId.orEmpty()
                scrobbled = false
                // The server shows what plays now
                if (mediaItem != null && !current.privateMode) {
                    val id = mediaItem.mediaId
                    scope.launch { LibraryApi(this@PlaybackService).nowPlaying(id) }
                }
                crossfade.applyBaseVolume()
                sendToAirPlay(player, mediaItem)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // After a seek or a skip the audio waiting for AirPlay belongs to the old place
                if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) AirPlayAudio.clear()
                if (!current.privateMode) recordTaste(oldPosition, reason)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) networkRetriesLeft = 1
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // Playback just paused itself at the end of the track because of the sleep timer
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && current.sleepAtTrackEnd) {
                    scope.launch { settings.clearSleep() }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Network trouble: try the other server address once, from the same place in the song
                val network = error.errorCode in 2000..2999
                if (network && networkRetriesLeft > 0 && serverSession.tryOtherAddress()) {
                    networkRetriesLeft--
                    player.prepare()
                }
            }
        })

        scope.launch {
            settings.values.collect { values ->
                current = values
                player.setPlaybackSpeed(values.speed)
                player.setSkipSilenceEnabled(values.skipSilence)
                player.setPauseAtEndOfMediaItems(values.sleepAtTrackEnd)
                crossfade.crossfadeMs = values.crossfadeSeconds * 1000L
                crossfade.gainMode = values.replayGainMode
                crossfade.sleepAtTrackEnd = values.sleepAtTrackEnd
                crossfade.speed = values.speed
                crossfade.smartFades = values.smartFades
                crossfade.autoMix = values.autoMix
                // The beat mix (and the stems) of the old player-based mixer are replaced by the native engine
                crossfade.beatMix = false
                crossfade.stemMix = false
                mixConductor.enabled = values.autoMix && values.beatMix
                mixConductor.speed = values.speed
                mixConductor.sleepAtTrackEnd = values.sleepAtTrackEnd
                mixConductor.smartFades = values.smartFades
                mixConductor.stems = values.stemMix
                crossfade.volumeLimit = values.volumeLimitPercent.coerceIn(10, 100) / 100f
                crossfade.applyBaseVolume()
                scheduleSleep(player, values)
            }
        }
        // The position is saved now and then while playing, so a closed app comes back at about the same place
        scope.launch {
            while (true) {
                delay(15_000)
                if (player.isPlaying) saveQueue(player)
            }
        }
        scope.launch {
            while (true) delay(crossfade.tick())
        }
        // Beat matching: analyse the end of the song that plays and the whole of the next one, once each
        scope.launch {
            while (true) {
                delay(3_000)
                if (!current.autoMix || !current.beatMix || player.mediaItemCount == 0) continue
                if (AirPlayAudio.tapping) continue
                val item = player.currentMediaItem ?: continue
                val duration = if (player.duration > 0L) player.duration else item.mediaMetadata.durationMs ?: 0L
                if (duration <= 0L) continue
                BeatMixStore.ensureEnd(this@PlaybackService, item.mediaId, duration)
                val next = player.nextMediaItemIndex
                if (next != C.INDEX_UNSET) {
                    val nextItem = player.getMediaItemAt(next)
                    BeatMixStore.ensureEntries(this@PlaybackService, nextItem.mediaId, nextItem.mediaMetadata.durationMs ?: 0L)
                }
            }
        }
        // A mode of the AutoMix menu: while the queue runs low, more songs for the mode are added (downloaded, analysed songs)
        scope.launch {
            while (true) {
                delay(5_000)
                // 0.0.120: AutoMix was switched on (or a saved queue came back) with shuffle on: shuffle goes off
                if (current.autoMix && player.shuffleModeEnabled) player.shuffleModeEnabled = false
                trimPlayed(player)  // 0.0.147
                val mode = MixMode.of(current.autoMixMode)
                if (!current.autoMix || mode == null || player.mediaItemCount == 0) continue
                // 0.0.119: repeat all no longer stops the refill (the queue would start over with the same songs); only shuffle and repeat one do
                if (player.shuffleModeEnabled || player.repeatMode == Player.REPEAT_MODE_ONE) continue
                if (player.mediaItemCount - 1 - player.currentMediaItemIndex >= MixModes.REFILL_BELOW) continue
                val seed = player.currentMediaItem?.mediaId
                val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
                val cur = player.currentMediaItemIndex
                val tail = queueTail(player)  // 0.0.136: the new songs come after these, the artist and album rule looks at them
                val songs = DownloadManager.get(this@PlaybackService).downloaded.value.values.map { it.song }
                val zone = current.autoMixZone
                val genres = current.autoMixGenres
                var round = false
                // 0.0.147: the songs taken off the front of the queue count as played too, until a new round starts
                val away = playedAway.toList()
                val fresh = withContext(Dispatchers.Default) {
                    MixModes.prepare(this@PlaybackService)
                    var list = MixModes.build(mode, zone, genres, songs, seed, ids.toSet() + away, MixModes.REFILL_SIZE, tail)
                    // 0.0.119: every song that fits is in the queue already: a new round starts, leaving out only the songs that played most
                    // recently (then fewer, then only the song that plays and the ones that come next), so that the same mix never comes back
                    if (list.isEmpty()) {
                        val history = away + ids
                        val at = away.size + cur
                        for (recent in intArrayOf(20, 8, 0)) {
                            list = MixModes.build(mode, zone, genres, songs, seed, history.drop(maxOf(0, at - recent)).toSet(), MixModes.REFILL_SIZE, tail)
                            if (list.isNotEmpty()) { round = true; break }
                        }
                    }
                    list
                }
                // 0.0.147: a new round starts with an empty memory (the queue itself still holds the songs that played last)
                if (round) playedAway.clear()
                if (fresh.isNotEmpty()) player.addMediaItems(fresh.map { MixModes.entryOf(it).toMediaItem() }) else delay(60_000)
            }
        }
        // AutoPlay: while the last song of the queue plays, add similar songs (or random ones when there are none)
        scope.launch {
            val library = LibraryApi(this@PlaybackService)
            while (true) {
                delay(5_000)
                if (!current.autoPlay || current.discovery || player.mediaItemCount == 0) continue
                if (current.autoMix && MixMode.of(current.autoMixMode) != null) continue  // the mode of the AutoMix menu fills the queue
                if (player.shuffleModeEnabled || player.repeatMode != Player.REPEAT_MODE_OFF) continue
                if (player.currentMediaItemIndex != player.mediaItemCount - 1) continue
                val id = player.currentMediaItem?.mediaId.orEmpty()
                if (id.isEmpty() || id == djFor) continue
                djFor = id
                val inQueue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet() + playedAway
                val picked = TasteEngine.forAutoPlay(library, taste, id, inQueue, 10)
                val tail = queueTail(player)
                // With beat mixing on, the songs are put in an order in which each suits the one before it
                val fresh = if (current.autoMix && current.beatMix && picked.size > 1) {
                    // The tempo of the songs picked is looked at first (a song not on the device is fetched, as it is when it plays)
                    conductor?.scanSongs(picked.map { it.id }, 40_000L)
                    withContext(Dispatchers.IO) {
                        BeatMixStore.preload(this@PlaybackService)
                        org.nooklin.music.playback.mix.MixFacts.preload(this@PlaybackService)
                        MixOrder.after(id, picked, tail)
                    }
                } else {
                    MixOrder.spaced(picked, tail)
                }
                if (fresh.isNotEmpty()) {
                    player.addMediaItems(fresh.map { song ->
                        QueueEntry(
                            id = song.id, title = song.title, artist = song.artist, album = song.album,
                            coverArt = song.coverArt, suffix = song.suffix, durationMs = song.durationSeconds * 1000L,
                            albumId = song.albumId, artistId = song.artistId, bitRate = song.bitRate, bpm = song.bpm,
                            replayGain = song.replayGain
                        ).toMediaItem()
                    })
                }
            }
        }
        // Discovery Station: while the queue runs low, add songs that were not played before
        scope.launch {
            val library = LibraryApi(this@PlaybackService)
            while (true) {
                delay(5_000)
                if (!current.discovery || player.mediaItemCount == 0) continue
                if (player.mediaItemCount - 1 - player.currentMediaItemIndex > 2) continue
                // When the server has nothing new, not asking again at once
                if (System.currentTimeMillis() - lastDiscoveryAt < 30_000L) continue
                lastDiscoveryAt = System.currentTimeMillis()
                val inQueue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet() + playedAway
                val fresh = MixOrder.spaced(TasteEngine.forDiscovery(library, taste, inQueue, 8), queueTail(player))
                if (fresh.isNotEmpty()) {
                    player.addMediaItems(fresh.map { song ->
                        QueueEntry(
                            id = song.id, title = song.title, artist = song.artist, album = song.album,
                            coverArt = song.coverArt, suffix = song.suffix, durationMs = song.durationSeconds * 1000L,
                            albumId = song.albumId, artistId = song.artistId, bitRate = song.bitRate, bpm = song.bpm,
                            replayGain = song.replayGain
                        ).toMediaItem()
                    })
                }
            }
        }
        // A song counts as played after half of it, or 4 minutes (the usual rule), when it is longer than 30 seconds
        scope.launch {
            val library = LibraryApi(this@PlaybackService)
            while (true) {
                delay(5_000)
                if (scrobbled || scrobbleId.isEmpty() || current.privateMode || !player.isPlaying) continue
                val duration = player.duration
                if (duration <= 30_000L) continue
                val position = player.currentPosition
                if (position >= minOf(duration / 2, 240_000L)) {
                    scrobbled = true
                    library.scrobble(scrobbleId, System.currentTimeMillis() - position)
                }
            }
        }
        scope.launch { restoreQueue(player) }
    }

    /**
     * A song that ended by itself, or that was skipped after more than half of it, counts as played through; a skip
     * earlier than that counts as a skip. Songs under 30 seconds and seeks are left out. Only kept on this phone.
     */
    private fun recordTaste(old: Player.PositionInfo, reason: Int) {
        val item = old.mediaItem ?: return
        val duration = item.mediaMetadata.durationMs ?: return
        if (duration <= 30_000L) return
        val done = when (reason) {
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> true
            Player.DISCONTINUITY_REASON_SKIP -> old.positionMs >= minOf(duration / 2, 240_000L)
            else -> return
        }
        recordTaste(item, old.positionMs, done)
    }

    /** Records one song as played through ([done]) or skipped; [positionMs] is where it was left. */
    private fun recordTaste(item: MediaItem, positionMs: Long, done: Boolean) {
        val duration = item.mediaMetadata.durationMs ?: return
        if (duration <= 30_000L || current.privateMode) return
        val id = item.mediaId
        val artistId = item.mediaMetadata.extras?.getString(EXTRA_ARTIST_ID).orEmpty()
        taste.record(id, artistId, done)
        if (taste.entry(id)?.genre.isNullOrEmpty()) {
            scope.launch {
                val r = LibraryApi(this@PlaybackService).song(id)
                if (r is Reply.Ok && r.value.genre.isNotEmpty()) taste.setGenre(id, r.value.genre)
            }
        }
    }

    private fun sendToAirPlay(player: Player, item: MediaItem?) {
        if (item == null) return
        val meta = item.mediaMetadata
        val seconds = if (player.duration > 0) player.duration / 1000.0 else 0.0
        AirPlayController.get(this).updateNowPlaying(
            meta.title?.toString(),
            meta.artist?.toString(),
            meta.albumTitle?.toString(),
            seconds
        )
    }

    private fun scheduleSleep(player: Player, values: PlaybackSettingsValues) {
        sleepJob?.cancel()
        sleepJob = null
        if (values.sleepEndAt <= 0L) return
        sleepJob = scope.launch {
            delay((values.sleepEndAt - System.currentTimeMillis()).coerceAtLeast(0L))
            player.pause()
            settings.clearSleep()
        }
    }

    private suspend fun restoreQueue(player: Player) {
        val saved = queueStore.load() ?: return
        if (player.mediaItemCount > 0) return // something was started before the queue was read
        player.setMediaItems(saved.entries.map { it.toMediaItem() }, saved.index, saved.positionMs)
        player.shuffleModeEnabled = saved.shuffle
        player.repeatMode = saved.repeatMode
        // Not prepared on purpose: nothing is downloaded until the user presses play
    }

    private fun snapshot(player: Player): SavedQueue? {
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toQueueEntry() }
        if (items.isEmpty()) return null
        return SavedQueue(
            entries = items,
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )
    }

    // 0.0.148: saves are merged: at most one write per 1.5 s, always of the newest state (onDestroy still saves directly)
    private var saveJob: kotlinx.coroutines.Job? = null
    private fun saveQueue(player: Player) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1_500)
            val saved = snapshot(player) ?: return@launch
            queueStore.save(saved)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // 0.0.129: swiping the app away in the app switcher stops the music and the downloads (before, playing music went on).
    // onDestroy saves the queue and the position and releases the player.
    override fun onTaskRemoved(rootIntent: Intent?) {
        DownloadManager.get(this).pause()
        stopSelf()
    }

    override fun onDestroy() {
        // The scope is cancelled right after, so this last save is written directly
        mediaSession?.player?.let { p -> snapshot(p)?.let { runBlocking { queueStore.save(it) } } }
        conductor?.release()
        conductor = null
        if (::crossfade.isInitialized) crossfade.release()
        AirPlayController.get(this).stopAll()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }
}
