@file:OptIn(UnstableApi::class)

package org.nooklin.music.playback.mix

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs
import kotlin.math.ln
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.music.airplay.AirPlayAudio
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.data.server.ServerSession
import org.nooklin.music.playback.CrossfadeController
import org.nooklin.music.playback.EXTRA_ALBUM_ID
import org.nooklin.music.playback.EXTRA_BPM
import org.nooklin.music.playback.taste.TasteStore

/**
 * Lets the native Auto Mix engine play the songs while the ExoPlayer keeps the queue, the session and the clock.
 *
 * - The engine owns a song from its start: the song is decoded into memory, the player's own sound is silenced (it keeps
 *   running as a silent mirror so the notification, the queue, the screen and the saved position stay as they were)
 *   and the engine plays from the place where the player is.
 * - While a song plays, the next one is fetched (a streamed song is first copied to the cache), decoded, analysed and
 *   handed to the engine together with a plan; the engine does the overlap and moves on to the next song by itself.
 *   The player is then moved to the next queue item.
 * - A song that cannot be handled (not decoded yet, too long, a failed decode) is simply played by the player itself,
 *   with the old plain AutoMix as before.
 *
 * Everything runs on the main thread (the scope's dispatcher); decoding, analysis and the waits for the audio thread run
 * on other threads inside the calls.
 */
class MixConductor(
    context: Context,
    private val player: ExoPlayer,
    private val crossfade: CrossfadeController,
    private val scope: CoroutineScope,
    private val downloads: DownloadManager,
    session: ServerSession,
    /** Called when the engine moved on from a song by itself: the song and the place it was left. */
    private val onAdvance: (MediaItem, Long) -> Unit
) {
    private val appContext = context.applicationContext
    private val library = MixLibrary(appContext, downloads, session, scope)

    /** Auto Mix with beat matching is on. */
    var enabled = false
        set(value) {
            val was = field
            field = value
            if (value && !was) scheduleScan()
        }
    var speed = 1f
    var sleepAtTrackEnd = false
    var smartFades = false
    /** The singing of a song can be taken out of an overlap (needs the model files and downloaded songs). */
    var stems = false

    private var job: Job? = null
    private var busy = false

    // The song the engine plays now and the one it has waiting
    private var engineId: String? = null
        set(value) {
            val was = field != null
            field = value
            if ((value != null) != was) {
                // 0.0.110: AirPlay takes the audio from the engine while it owns the song, from the player otherwise; what waits in the
                // queue of the other source is thrown away (it is a few seconds of the same song at another position)
                AirPlayAudio.mixOwns = value != null
                AirPlayAudio.clear()
                NativeMix.tapFlush()
            }
        }
    private var nextId: String? = null
    private var epoch = -1
    private var lastChanges = 0
    private var playGen = 0
    private var planKey = ""

    // 0.0.146: what the plan check of [step] worked out, with the inputs it was worked out from (compared as objects)
    private class PlanInputs(
        val id: String, val next: String,
        val a0: DoubleArray?, val b0: DoubleArray?, val va: FloatArray?, val vb: FloatArray?, val oldA: Double?, val oldB: Double?,
        val a: DoubleArray?, val b: DoubleArray?, val doubtA: Boolean, val doubtB: Boolean
    )
    private var planInputs: PlanInputs? = null
    // 0.0.101: the plan that is running or about to run, and the style and exit kind of the transition before it (the planner avoids repeating them)
    private var currentPlan: DoubleArray? = null
    private var prevStyle = -1
    private var prevExit = -1
    private var loadingNext: String? = null

    // The job that takes the singing apart for the coming overlap, and the change for which that did not work (then no stems are planned)
    private var stemJob: Job? = null
    private var stemsFailedFor = ""
    // The change for which the audio of a beat loop could not be made (then no loop is planned)
    private var loopFailedFor = ""
    private var stemHandles: List<Long> = emptyList()

    // A song the engine played to the end by itself, and the one that was asked to be decoded
    private var finishedId: String? = null
    private var requestedDecode = ""
    private var lastSeenId = ""
    private var itemSeenAt = 0L

    private var pendingSeekSec: Double? = null

    // Seeks the conductor makes itself on the silent player (the listener must not take them for seeks by the user)
    private var selfSeeks = 0
    private var selfSeekUntil = 0L

    // How often the silent player was set back to the engine for this song, and when that may happen again
    private var followCount = 0
    private var nextFollowAt = 0L

    // The queue item the engine plays (the player may already have moved on to the next one by itself)
    private var engineItem: MediaItem? = null
    private var maxPhaseErr = 0.0

    // 0.0.133: the phase error through the whole overlap (armed and blend): the first value, the largest with the beat of the
    // overlap and the part where it was seen, and the last one (log 0.0.132: 7 of 26 changes had more than 4 ms, 3 more than 10 ms)
    private var phaseSamples = 0
    private var phaseFirst = 0.0
    private var phaseLast = 0.0
    private var phaseWorst = 0.0
    private var phaseWorstBeat = 0.0
    private var phaseWorstArmed = false
    // 0.0.141: the phase error every 2 beats (key: beat of the overlap divided by 2, rounded down), to see whether it grows or comes back
    private val phaseTrack = java.util.TreeMap<Int, Double>()
    private var pausedSince = 0L
    private var slept = false

    private val listener = object : Player.Listener {
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            if (selfSeeks > 0 && SystemClock.elapsedRealtime() < selfSeekUntil) {
                selfSeeks--  // one of our own seeks
                return
            }
            selfSeeks = 0
            val id = newPosition.mediaItem?.mediaId ?: return
            if (id == engineId && oldPosition.mediaItem?.mediaId == id) {
                pendingSeekSec = newPosition.positionMs / 1000.0  // a seek inside the song the engine plays
            } else if (id == finishedId) {
                finishedId = null
            }
        }
    }

    private var scanJob: Job? = null

    /**
     * Notes the tempo of the next songs of the queue and of the downloaded songs that were not looked at yet, for the order of a
     * queue. Runs once at a time; called when Auto Mix is switched on, at the start and at every change of song.
     */
    private fun scheduleScan() {
        if (scanJob?.isActive == true || !enabled) return
        scanJob = scope.launch {
            kotlinx.coroutines.delay(5_000)
            if (!enabled) return@launch
            val from = maxOf(0, player.currentMediaItemIndex)
            val queue = (from until minOf(player.mediaItemCount, from + SCAN_AHEAD)).map { player.getMediaItemAt(it).mediaId }
            library.scan(queue + downloads.downloaded.value.keys) { !enabled || job == null }
        }
    }

    /** Notes the tempo of [ids] (fetching those that are not on the device), at most [maxWaitMs]; for ordering a queue. */
    suspend fun scanSongs(ids: List<String>, maxWaitMs: Long) {
        if (!enabled) return
        kotlinx.coroutines.withTimeoutOrNull(maxWaitMs) { library.scan(ids) { !enabled } }
    }

    fun start() {
        if (job != null) return
        player.addListener(listener)
        scheduleScan()
        MixAirPlayTap.start()
        job = scope.launch {
            while (isActive) {
                try {
                    NativeMix.tap(AirPlayAudio.tapping, AirPlayAudio.muteLocal)  // 0.0.110: the engine also plays for AirPlay
                    step()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    busy = false
                    Log.w(TAG, "conductor: ${e.javaClass.simpleName}")
                }
                delay(if (engineId != null) 100L else if (!player.isPlaying) 500L else 250L)
            }
        }
    }

    fun release() {
        job?.cancel()
        job = null
        player.removeListener(listener)
        crossfade.suspended = false
        crossfade.muted = false
        AirPlayAudio.mixOwns = false
        if (NativeMix.available) {
            NativeMix.tap(false, false)
            NativeMix.stop()
            NativeMix.release()
        }
        library.releaseAll()
    }

    // ---- the loop -------------------------------------------------------------------------------------------------

    private suspend fun step() {
        if (busy) return
        val item = player.currentMediaItem
        val on = enabled && NativeMix.available && item != null &&
            player.repeatMode != Player.REPEAT_MODE_ONE && player.playbackState != Player.STATE_IDLE
        if (!on || item == null) {
            if (engineId != null || crossfade.muted) disown()
            return
        }
        val id = item.mediaId
        if (id != lastSeenId) {
            lastSeenId = id
            itemSeenAt = SystemClock.elapsedRealtime()
        }
        val st = NativeMix.status()

        if (engineId != null && st.changes != lastChanges) {
            onHandoff(st)
            return
        }
        if (engineId != null && st.ended) {
            finishedId = engineId
            engineId = null
            engineItem = null
            nextId = null
            planKey = ""
            loadingNext = null
            return
        }

        if (engineId != id) {
            if (id == finishedId) return
            val song = library.peek(id)
            if (song == null) {
                // Not ready: the player plays this song itself and the engine, if it still plays another song, stops
                if (engineId != null) {
                    disown(setOf(id))
                } else if (crossfade.muted) {
                    fadeGhost(false)
                }
                if (requestedDecode != id && !library.failedRecently(id)) {
                    requestedDecode = id
                    scope.launch { library.decoded(id) }
                }
                return
            }
            if (crossfade.isFading) return
            // The engine moves on to this song by itself at about this moment: give it time before taking over
            if (nextId == id && SystemClock.elapsedRealtime() - itemSeenAt < HANDOFF_WAIT_MS) return
            takeOver(id, song)
            return
        }

        // ---- the engine owns the song that plays
        pendingSeekSec?.let {
            pendingSeekSec = null
            restart(id, it)
            return
        }
        val paused = !player.playWhenReady || player.playbackState == Player.STATE_ENDED
        NativeMix.setPaused(paused)
        NativeMix.setSpeed(speed.toDouble())
        NativeMix.setGain(crossfade.gainFor(item).toDouble())
        if (paused && st.pausedSilent) {
            if (pausedSince == 0L) pausedSince = SystemClock.elapsedRealtime()
            if (!slept && SystemClock.elapsedRealtime() - pausedSince > SLEEP_AFTER_MS) {
                NativeMix.sleepStream()
                slept = true
            }
        } else {
            pausedSince = 0L
            slept = false
        }
        if (st.blending) maxPhaseErr = maxOf(maxPhaseErr, abs(st.phaseErrorMs))
        if (st.state == 3 || st.blending) {  // 3 is armed: B is cued and follows the grid of A, the blend has not started yet
            if (phaseSamples == 0) phaseFirst = st.phaseErrorMs
            phaseSamples++
            phaseLast = st.phaseErrorMs
            phaseTrack.putIfAbsent(Math.floor(st.blendBeats / 2.0).toInt(), st.phaseErrorMs)
            if (abs(st.phaseErrorMs) > abs(phaseWorst)) {
                phaseWorst = st.phaseErrorMs
                phaseWorstBeat = st.blendBeats
                phaseWorstArmed = st.state == 3
            }
        }

        // The silent player follows the engine, but only for a real difference: a seek makes the player stop and start
        // again (the play and pause icon flickers, its decoder restarts), so it is rare, never twice within
        // FOLLOW_GAP_MS and at most FOLLOW_MAX times per song. Small differences (a stretched overlap) are no problem for a
        // silent player: it is set to the right place when the engine hands over.
        if (!paused && st.state == 1 && player.playbackState == Player.STATE_READY) {  // only while one song plays alone
            val now = SystemClock.elapsedRealtime()
            val drift = abs(player.currentPosition / 1000.0 - st.positionSec)
            if (drift > DRIFT_SEC && now >= nextFollowAt && followCount < FOLLOW_MAX) {
                followCount++
                nextFollowAt = now + FOLLOW_GAP_MS
                seekSelf(C.INDEX_UNSET, (st.positionSec * 1000.0).toLong())
            }
        }

        // The song after this one
        val nextIdx = player.nextMediaItemIndex
        val upcoming = if (nextIdx == C.INDEX_UNSET || sleepAtTrackEnd) null else player.getMediaItemAt(nextIdx)
        val upcomingId = upcoming?.mediaId
        if (nextId != null && nextId != upcomingId) {
            stopStems()
            withContext(Dispatchers.Default) {
                NativeMix.clearPlan()
                NativeMix.dropNext()
            }
            nextId = null
            planKey = ""
            loadingNext = null
        }
        if (upcoming != null && nextId == null && loadingNext != upcomingId) {
            loadingNext = upcomingId
            scope.launch { prepareNext(id, upcoming) }
        }
        library.retain(setOfNotNull(engineId, nextId, loadingNext))

        // The plan for the change
        val next = nextId
        if (next != null && upcoming != null) {
            val a0 = library.peek(id)?.map
            val b0 = library.peek(next)?.map
            // The singing values (measured in the background) go into the maps; a new plan is made when they arrive
            val va = MixVocals.seconds(appContext, id)
            val vb = MixVocals.seconds(appContext, next)
            // The tempo of the grid against the tempo of the older analysis of the whole song (done a while after the grid, so a
            // new plan is made when it arrives; 0.0.70: a 4/3 mistake in the grid had A sped up 15% and B slowed down 13%)
            val oldA = org.nooklin.music.playback.beat.BeatMixStore.entries(id)?.firstOrNull()?.bpm
            val oldB = org.nooklin.music.playback.beat.BeatMixStore.entries(next)?.firstOrNull()?.bpm
            // 0.0.146: this runs every 100 ms on the main thread; copying the two maps and checking the tempo (with text formatting)
            // each time showed in the stack samples of log 0.0.144 as about a quarter of the main thread's work. The result only
            // changes when one of the inputs does, so it is kept while they stay the same objects.
            val kept = planInputs
            val inputs = if (kept != null && kept.id == id && kept.next == next && kept.a0 === a0 && kept.b0 === b0 &&
                kept.va === va && kept.vb === vb && kept.oldA == oldA && kept.oldB == oldB
            ) {
                kept
            } else {
                val aNew = if (a0 != null && va != null) MixMap.withVocal(a0, va) else a0
                val bNew = if (b0 != null && vb != null) MixMap.withVocal(b0, vb) else b0
                PlanInputs(id, next, a0, b0, va, vb, oldA, oldB, aNew, bNew, tempoCheck(aNew, oldA).doubt, tempoCheck(bNew, oldB).doubt)
                    .also { planInputs = it }
            }
            val a = inputs.a
            val b = inputs.b
            val doubtA = inputs.doubtA
            val doubtB = inputs.doubtB
            val stemsOn = stems && NativeMix.available && MixStems.available(appContext) && stemsFailedFor != "$id>$next>$playGen" &&
                downloads.downloaded.value.keys.let { id in it && next in it }
            val loopsOn = NativeMix.available && loopFailedFor != "$id>$next>$playGen"
            val key = "$id>$next>$playGen>${a != null}>${a?.let { MixMap.hasVocal(it) }}>${b?.let { MixMap.hasVocal(it) }}>${oldA != null}>${oldB != null}>$stemsOn>$loopsOn"
            if (key != planKey) {
                planKey = key
                makePlan(item, upcoming, a, b, st.positionSec, doubtA, doubtB, stemsOn, loopsOn)
            }
        }
    }

    // ---- taking over, handing over, giving back ------------------------------------------------------------------------

    private suspend fun takeOver(id: String, song: MixLibrary.Song) {
        busy = true
        try {
            if (engineId != null) {
                NativeMix.setPaused(true)  // another song was sounding: it fades out first
                delay(30L)
            }
            val pos = player.currentPosition / 1000.0
            val ok = withContext(Dispatchers.Default) { NativeMix.play(song.handle, song.map ?: MixMap.EMPTY, pos) }
            if (!ok) {
                Log.w(TAG, "the engine did not start")
                finishedId = id  // not tried again for this song; the player plays it
                if (crossfade.muted) fadeGhost(false)
                return
            }
            val st = NativeMix.status()
            engineId = id
            engineItem = player.currentMediaItem
            scheduleVocals(id)
            followCount = 0
            nextFollowAt = 0L
            nextId = null
            planKey = ""
            loadingNext = null
            finishedId = null
            epoch = st.epoch
            lastChanges = st.changes
            playGen++
            maxPhaseErr = 0.0
            resetPhase()
            pausedSince = 0L
            slept = false
            NativeMix.setSpeed(speed.toDouble())
            NativeMix.setGain(crossfade.gainFor(player.currentMediaItem).toDouble())
            crossfade.suspended = true
            NativeMix.setPaused(!player.playWhenReady)
            fadeGhost(true)
            if (song.map == null) attachMap(song)
        } finally {
            busy = false
        }
    }

    private suspend fun restart(id: String, sec: Double) {
        val song = library.peek(id) ?: return
        busy = true
        try {
            NativeMix.setPaused(true)
            delay(30L)
            val ok = withContext(Dispatchers.Default) { NativeMix.play(song.handle, song.map ?: MixMap.EMPTY, sec) }
            if (!ok) return
            val st = NativeMix.status()
            epoch = st.epoch
            lastChanges = st.changes
            followCount = 0
            nextFollowAt = 0L
            nextId = null
            planKey = ""
            loadingNext = null
            playGen++
            NativeMix.setPaused(!player.playWhenReady)
            if (song.map == null) attachMap(song)
        } finally {
            busy = false
        }
    }

    private suspend fun onHandoff(st: NativeMix.Status) {
        busy = true
        try {
            lastChanges = st.changes
            epoch = st.epoch
            stemJob?.cancel()  // the overlap is over; the overlays stay on their songs until the next plan
            stemJob = null
            stemHandles = emptyList()
            val newId = nextId
            // The song the engine just left (the silent player may already have moved on to the next one)
            val old = engineItem ?: player.currentMediaItem
            val oldPos = if (player.currentMediaItem?.mediaId == old?.mediaId) player.currentPosition
            else old?.mediaMetadata?.durationMs ?: 0L
            currentPlan?.let { cp ->
                prevStyle = MixPlan.style(cp)
                prevExit = if (MixPlan.style(cp) == 4) MixPlan.kind(cp) else -1
            }
            currentPlan = null
            scheduleScan()
            maxPhaseErr = 0.0
            resetPhase()
            engineId = newId
            engineItem = null
            followCount = 0
            nextFollowAt = 0L
            nextId = null
            planKey = ""
            loadingNext = null
            playGen++
            if (newId == null) return
            var idx = -1
            val nextIdx = player.nextMediaItemIndex
            if (nextIdx != C.INDEX_UNSET && player.getMediaItemAt(nextIdx).mediaId == newId) {
                idx = nextIdx
            } else {
                for (i in 0 until player.mediaItemCount) {
                    if (i != player.currentMediaItemIndex && player.getMediaItemAt(i).mediaId == newId) {
                        idx = i
                        break
                    }
                }
            }
            // The silent player may have moved on to this song itself (the engine handed over at the end of the song)
            if (idx < 0 && player.currentMediaItem?.mediaId == newId) idx = player.currentMediaItemIndex
            if (idx < 0) {
                disown(emptySet())
                return
            }
            if (old != null) onAdvance(old, oldPos)
            engineItem = player.getMediaItemAt(idx)
            seekSelf(idx, (st.positionSec * 1000.0).toLong())
            lastSeenId = newId
            itemSeenAt = SystemClock.elapsedRealtime()
            val song = library.peek(newId)
            if (song != null && song.map == null) attachMap(song)
        } finally {
            busy = false
        }
    }

    /** Stops the engine and lets the player sound again. */
    private suspend fun disown(keep: Set<String> = emptySet()) {
        stopStems()
        val wasOwned = engineId != null
        if (wasOwned) {
            val st = NativeMix.status()
            if (abs(player.currentPosition / 1000.0 - st.positionSec) > 0.15 && !st.ended) {
                seekSelf(C.INDEX_UNSET, (st.positionSec * 1000.0).toLong())
            }
            NativeMix.setPaused(true)
            delay(30L)
            withContext(Dispatchers.Default) { NativeMix.stop() }
        }
        engineId = null
        engineItem = null
        nextId = null
        planKey = ""
        loadingNext = null
        crossfade.suspended = false
        if (crossfade.muted) fadeGhost(false)
        library.retain(keep)
    }

    /** A seek of the silent player made by the conductor itself: [index] is the queue item, or INDEX_UNSET for the current one. */
    private fun seekSelf(index: Int, positionMs: Long) {
        selfSeeks++
        selfSeekUntil = SystemClock.elapsedRealtime() + SELF_SEEK_MS
        if (index == C.INDEX_UNSET) player.seekTo(positionMs) else player.seekTo(index, positionMs)
    }

    /** Brings the volume of the player down (silent mirror) or up (the player plays itself) in a few steps. */
    private suspend fun fadeGhost(toMuted: Boolean) {
        crossfade.muted = toMuted
        val target = if (toMuted) 0f else crossfade.gainFor(player.currentMediaItem)
        val from = player.volume
        for (i in 1..4) {
            player.volume = from + (target - from) * i / 4f
            delay(15L)
        }
    }

    // ---- the next song --------------------------------------------------------------------------------------------------

    private suspend fun prepareNext(aId: String, bItem: MediaItem) {
        val bId = bItem.mediaId
        val b = library.decoded(bId)
        if (b == null) return  // cannot be decoded: the change is made by the player
        if (engineId != aId || upcomingId() != bId) {
            loadingNext = null
            return
        }
        var map = b.map
        if (map == null) {
            val remaining = durationSec(player.currentMediaItem) - NativeMix.status().positionSec
            // With little time left the next song goes in without its grid (a plain fade)
            if (remaining > NO_GRID_BELOW_SEC) map = library.mapOf(b)
            if (engineId != aId || upcomingId() != bId) {
                loadingNext = null
                return
            }
        }
        val ok = withContext(Dispatchers.Default) { NativeMix.loadNext(b.handle, map ?: MixMap.EMPTY) }
        if (ok) {
            nextId = bId
            scheduleVocals(bId)
            b.map = map
        } else {
            loadingNext = null
        }
    }

    private fun upcomingId(): String? {
        if (sleepAtTrackEnd) return null
        val i = player.nextMediaItemIndex
        return if (i == C.INDEX_UNSET) null else player.getMediaItemAt(i).mediaId
    }

    private fun durationSec(item: MediaItem?): Double {
        val d = player.duration
        val ms = if (d != C.TIME_UNSET && d > 0L) d else item?.mediaMetadata?.durationMs ?: 0L
        return ms / 1000.0
    }

    /** Gives the engine the beat grid of the song that plays, when it started without one. */
    private fun attachMap(song: MixLibrary.Song) {
        scope.launch {
            val map = library.mapOf(song) ?: return@launch
            if (engineId != song.id) return@launch
            // The epoch is read now: after a start the engine counts it up when its audio thread takes the command, which
            // is later than the moment the start returned, so the value kept then can be one too old
            if (NativeMix.setCurrentMap(map, NativeMix.status().epoch)) song.map = map
        }
    }

    /** Measures the singing at the start and the end of a song in the background (once per song). */
    private fun scheduleVocals(id: String) {
        scope.launch {
            val song = library.peek(id) ?: return@launch
            val map = song.map ?: library.mapOf(song) ?: return@launch
            MixVocals.analyse(appContext, id, MixMap.duration(map), head = true, tail = true)
        }
    }

    /**
     * 0.0.103: the family of the genre, only a prior for the planner (the measured properties of the two songs decide):
     * 0 unknown, 1 D (hip-hop, pop, r&b, dancehall, reggaeton, trap, rock: short, vocal), 2 A (house, techno, trance, disco, afro house,
     * amapiano, minimal: long blends), 3 B (drum and bass, jungle, UK garage, dubstep, riddim: 16 to 32 beats, drop swaps),
     * 4 C (hardstyle, hardcore, hard techno, psytrance: kick swap, "energy timing").
     */
    private fun genreOf(id: String): Int {
        val g = TasteStore.get(appContext).entry(id)?.genre?.lowercase().orEmpty()
        if (g.isEmpty()) return 0
        fun has(vararg w: String) = w.any { g.contains(it) }
        return when {
            has("hardstyle", "hardcore", "hard techno", "gabber", "rawstyle", "frenchcore", "psy", "goa") -> 4
            has("drum", "dnb", "d&b", "jungle", "dubstep", "riddim", "uk garage", "ukg", "2-step", "2 step", "speed garage", "bassline", "bass") -> 3
            has("house", "techno", "trance", "minimal", "disco", "afro", "amapiano", "garage", "balearic", "nu-disco") -> 2
            has("hip", "rap", "pop", "r&b", "rnb", "reggaeton", "dancehall", "trap", "soul", "rock", "indie", "metal", "funk", "punk") -> 1
            else -> 0
        }
    }

    private fun genreOf(a: String, b: String): Int {
        val x = genreOf(a)
        val y = genreOf(b)
        return if (x == y) x else if (x == 0) y else if (y == 0) x else 0
    }

    /**
     * True when the tempo of the grid and the tempo of the older analysis of the whole song do not agree up to a factor
     * 1, 2 or 1/2 (the planner folds those): one of them follows another pulse (for example 4/3), so the grid cannot be
     * trusted for a beat match.
     */
    private fun tempoAgrees(a: Double, b: Double): Boolean {
        val r = a / b
        return listOf(0.5, 1.0, 2.0).any { abs(ln(r / it)) < TEMPO_AGREE }
    }

    private class TempoCheck(val doubt: Boolean, val why: String)

    /**
     * The grid against the older analysis. When they disagree the song itself decides: the analysis kept how well a grid at
     * each candidate tempo fits the strong onsets. The grid stands when its coverage is at least that of the candidate at the
     * older tempo (or the older tempo was not among the candidates at all); otherwise the doubt stays and the change is an exit.
     * A map without this evidence keeps the doubt, as before.
     */
    private fun tempoCheck(map: DoubleArray?, old: Double?): TempoCheck {
        val grid = map?.let { MixMap.bpm(it) }
        if (map == null || grid == null || old == null || grid <= 0.0 || old <= 0.0) return TempoCheck(false, "no older analysis")
        if (tempoAgrees(grid, old)) return TempoCheck(false, "grid and older analysis agree")
        val ev = MixMap.tempoEvidence(map)
        if (ev.size < 2) return TempoCheck(true, "disagree, no evidence in the map")
        val own = ev[0].second
        val rival = ev.drop(1).filter { tempoAgrees(it.first, old) && !tempoAgrees(it.first, grid) }.maxByOrNull { it.second }
            ?: return TempoCheck(false, "grid stands: the older tempo is not among the candidates")
        return if (own >= rival.second) {
            TempoCheck(false, "grid stands: coverage %.2f against %.2f at %.1f bpm".format(own, rival.second, rival.first))
        } else {
            TempoCheck(true, "doubt: coverage %.2f at the grid against %.2f at %.1f bpm".format(own, rival.second, rival.first))
        }
    }

    private suspend fun makePlan(
        a: MediaItem, b: MediaItem, mapA: DoubleArray?, mapB: DoubleArray?, nowA: Double,
        doubtA: Boolean = false, doubtB: Boolean = false, stemsOn: Boolean = false, loopsOn: Boolean = false
    ) {
        stopStems()
        val sameAlbum = run {
            val x = a.mediaMetadata.extras?.getString(EXTRA_ALBUM_ID).orEmpty()
            x.isNotEmpty() && x == b.mediaMetadata.extras?.getString(EXTRA_ALBUM_ID).orEmpty()
        }
        if (smartFades && sameAlbum) {
            withContext(Dispatchers.Default) { NativeMix.clearPlan() }  // songs of one album follow each other
            return
        }
        // A grid that is in doubt is not used for a beat match: the maps stay (the planner needs the end of A and the
        // sections), but no tempo change is allowed, so the change is an exit or a fade at the end of A. With an empty
        // map of A (0.0.70) the planner did not know where A ends and started the fade one second after the plan.
        val maxTempo = if (doubtA || doubtB) 0.0 else MAX_TEMPO_CHANGE
        val plan = withContext(Dispatchers.Default) {
            val p = NativeMix.plan(mapA ?: MixMap.EMPTY, mapB ?: MixMap.EMPTY, nowA, maxTempo, genreOf(a.mediaId, b.mediaId), stemsOn, loopsOn, prevStyle, prevExit)
            NativeMix.setPlan(p)
            p
        }
        currentPlan = plan
        // 0.0.99: no stems when the overlap is too close to split the songs in time (they would come too late)
        if (stemsOn && MixStems.needed(plan) && mapA != null && mapB != null && MixMap.timeAtBeat(mapA, plan[1]) - nowA >= STEM_LEAD_SEC) startStems(a.mediaId, b.mediaId, mapA, mapB, plan)
        if (loopsOn && MixLoops.needed(plan) && mapA != null) startLoop(a.mediaId, b.mediaId, mapA, plan)
        withContext(Dispatchers.IO) { org.nooklin.music.playback.beat.BeatMixStore.preload(appContext) }
        tallyPlan(a.mediaId, b.mediaId, plan)
    }

    // 0.0.103: how the changes of this session went, for the target of the rebuild (exits plus blends of 8 beats or less under 15%).
    // 0.0.106: one count per pair of songs (the last plan counts): a new plan for the same pair (a seek, stems that came too late) is not a new change.
    private val tallyPairs = LinkedHashMap<String, Int>()  // pair -> 0 blend, 1 exit, 2 blend of 8 beats or less
    private fun tallyPlan(a: String, b: String, plan: DoubleArray) {
        val kind = MixPlan.kind(plan)
        tallyPairs["$a>$b"] = if (kind in MixPlan.FALLBACK..MixPlan.FADE_OUT) 1 else if (MixPlan.lengthBeats(plan) <= 8) 2 else 0
        val all = tallyPairs.size
        val exits = tallyPairs.values.count { it == 1 }
        val short = tallyPairs.values.count { it == 2 }
    }

    /** Stops the job for the overlays and takes the overlays off the songs. */
    private fun stopStems() {
        stemJob?.cancel()
        stemJob = null
        val handles = stemHandles
        stemHandles = emptyList()
        if (handles.isNotEmpty()) scope.launch(Dispatchers.Default) { handles.forEach { NativeMix.clearOverlay(it) } }
    }

    /** Takes the singing apart for the overlap of this plan; when that fails or is too late, the change is planned again without stems. */
    private fun startStems(idA: String, idB: String, mapA: DoubleArray, mapB: DoubleArray, plan: DoubleArray) {
        val sa = library.peek(idA) ?: return
        val sb = library.peek(idB) ?: return
        val failKey = "$idA>$idB>$playGen"
        stemHandles = listOf(sa.handle, sb.handle)
        stemJob = scope.launch {
            val ok = try {
                MixStems.build(appContext, sa, mapA, sb, mapB, plan) { NativeMix.status().positionSec }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "stems failed: ${e.javaClass.simpleName}")
                false
            }
            // 0.0.99: once the overlap has begun the plan is not changed any more (B was already playing when a new plan replaced it)
            val overlapBegun = NativeMix.status().positionSec >= MixMap.timeAtBeat(mapA, plan[1]) - 1.0
            if (!ok && engineId == idA && nextId == idB && !overlapBegun) {
                stemsFailedFor = failKey
                planKey = ""
            }
        }
    }

    /** Makes the audio of the beat loop for this plan; when that fails or is too late, the change is planned again without a loop. */
    private fun startLoop(idA: String, idB: String, mapA: DoubleArray, plan: DoubleArray) {
        val sa = library.peek(idA) ?: return
        val failKey = "$idA>$idB>$playGen"
        stemHandles = listOf(sa.handle)
        stemJob = scope.launch(Dispatchers.Default) {
            val ok = try {
                MixLoops.build(sa, mapA, plan) { NativeMix.status().positionSec }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "loop failed: ${e.javaClass.simpleName}")
                false
            }
            if (!ok && engineId == idA && nextId == idB) {
                loopFailedFor = failKey
                planKey = ""
            }
        }
    }

    // ---- log --------------------------------------------------------------------------------------------------------------

    private fun resetPhase() {
        phaseSamples = 0
        phaseFirst = 0.0
        phaseLast = 0.0
        phaseWorst = 0.0
        phaseWorstBeat = 0.0
        phaseWorstArmed = false
        phaseTrack.clear()
    }

    private companion object {
        const val TAG = "NooklinMix"
        const val STEM_LEAD_SEC = 60.0  // the overlap of a plan with stems must start at least this long from now (splitting takes 25 to 55 s); the same value is in the planner (JNI)
        // The most the old song is sped up or slowed down to reach the tempo of the next one (0.10 = 10%); more: a clean fade
        const val SCAN_AHEAD = 10  // songs of the queue that the tempo scan looks at (a streamed song is fetched for it)
        const val MAX_TEMPO_CHANGE = 0.30  // combined (0.0.107: the user chose 30%; 0.0.106: 0.15; was 0.40 before that; 0.0.68: 0.20, 0.0.66: 0.10); one song alone moves at most 5% (planner), more is shared by both
        const val TEMPO_AGREE = 0.04  // how far (in ln) the tempo of the grid may be from 1, 2 or 1/2 times the older analysis
        const val HANDOFF_WAIT_MS = 600L
        const val DRIFT_SEC = 1.5
        const val FOLLOW_GAP_MS = 20_000L
        const val FOLLOW_MAX = 3
        const val SELF_SEEK_MS = 3_000L
        const val SLEEP_AFTER_MS = 3_000L
        const val NO_GRID_BELOW_SEC = 12.0
    }
}
