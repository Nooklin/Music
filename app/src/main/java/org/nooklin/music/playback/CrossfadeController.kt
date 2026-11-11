@file:OptIn(UnstableApi::class)

package org.nooklin.music.playback

import android.os.SystemClock
import android.util.Log
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import org.nooklin.music.airplay.AirPlayAudio
import org.nooklin.music.data.playback.ReplayGainMode
import org.nooklin.music.playback.beat.BassCutProcessor
import org.nooklin.music.playback.beat.BeatEdge
import org.nooklin.music.playback.beat.BeatMixPlanner
import org.nooklin.music.playback.beat.BeatMixStore
import org.nooklin.music.playback.beat.BeatPlan
import org.nooklin.music.playback.beat.KeyDetector
import org.nooklin.music.playback.stems.StemClip
import org.nooklin.music.playback.stems.StemClipSpec
import org.nooklin.music.playback.stems.StemMix
import org.nooklin.music.playback.stems.StemTransitions

/**
 * Sets the volume per song (ReplayGain) and overlaps two songs (crossfade).
 *
 * Crossfade works with a second, short-lived player. A while before the end of a song it prepares that same song at
 * the point where the fade starts, paused. The old song then moves softly from the main player to the second player:
 * the second player starts silent, is brought to the same place as the main player, and a short crossfade of the same
 * audio hands the old song over. Only then does the main player jump to the next song, and the volumes are ramped
 * against each other with an equal-power curve. The main player and the session never see the second player.
 *
 * In a beat mix the next song starts silent, is put on the beat while nobody hears it (the jumps happen there), and is
 * faded in once it is in step. After that only small speed changes of the old song keep the beats together.
 *
 * Everything runs on the main thread, called from the service's ticker.
 */
class CrossfadeController(
    private val main: ExoPlayer,
    private val mainBass: BassCutProcessor,
    private val isDownloaded: (String) -> Boolean,
    private val createSecondary: (BassCutProcessor) -> ExoPlayer
) {
    var crossfadeMs: Long = 0L
    var gainMode: Int = ReplayGainMode.OFF
    var sleepAtTrackEnd: Boolean = false
    var speed: Float = 1f

    // True: no crossfade between two songs of the same album, they follow each other without a gap
    var smartFades: Boolean = false

    // True: every change between songs is a long overlap (at least AUTOMIX_MS), also with the crossfade off
    var autoMix: Boolean = false

    // True (with autoMix): match the tempo of the two songs and overlap them on the beat, when both were analysed
    var beatMix: Boolean = false

    // True (with beatMix): the overlap is built from stems (vocals, drums, bass, the rest) when that is ready in time
    var stemMix: Boolean = false

    // Makes the overlaps from stems; set by the service
    var stems: StemTransitions? = null

    private enum class Phase { NONE, HANDOVER, HOLD, FADE }

    // The report of the change that is going on: one line in the log when it ends (see [endFade])
    private var planKeys = ""
    private var repIds = ""
    private var repKind = ""
    private var repPlan = ""
    private var repKeys = ""
    private var repHandover = ""
    private var repAlign = ""
    private var repStartAt = 0L

    // The overlap of this mix as one audio file on the second player (null: the old song itself, with filters)
    private var stemClip: StemClip? = null

    private var phase = Phase.NONE
    private var plan: BeatPlan? = null
    private var planKey = ""
    private var beatFading = false
    private var secondaryBass: BassCutProcessor? = null

    // Where the second player was put (its start in the old song)
    private var armedPos = 0L

    // The soft handover of the old song from the main player to the second player
    private var handoverAt = 0L
    private var xfadeAt = 0L
    private var secondaryPos0 = 0L

    // How long before the old song reaches the place the second player has to be started, so that its sound begins
    // exactly there (the second player needs time to get going); learned from the offset found at each handover
    private var secondaryStartMs = 200L
    private var lastAlignAt = 0L
    private var firstOffset: Long? = null

    // The beat alignment of the next song
    private var seekAt = 0L
    private var landed = false
    private var jumps = 0
    private var aligned = false
    private var alignedAt = 0L
    private var holdMainUntil = 0L
    private var mainJumpAt = 0L
    private var settlePos = 0L
    private var lastNudgeAt = 0L

    // The next song of a beat mix can start early, silent, on the main player, and is put on the beat with its speed
    // while nobody hears it (preroll); this is what that needs
    private var preroll = false
    private var prerollTried = false
    private var lastMainAlignAt = 0L

    // The old song glides to the tempo of the next one (not at once): the part of song A in which that happens
    private var rampStartA = 0L
    private var rampEndA = 0L
    private var rampDone = true
    private var lastRampSpeed = 1.0

    // In a stem clip: where in the clip the next song took over (-1: not yet), and when the alignment was last logged
    private var stemXfadeFrom = -1L
    private var lastAlignLogAt = 0L

    // In an echo cut: 0 nothing yet, 1 the old song is in the echo, 2 the old song has stopped and the next one is in;
    // where in the old song (ms) and when (clock) it stopped
    private var echoStep = 0
    private var echoCutAt = 0L
    private var echoCutWall = 0L

    // How long the next song takes to be heard after the jump to it, and after a jump inside it (milliseconds);
    // learned from earlier mixes. Only the first jump into a song counts for the first.
    private class Learned(var value: Long)

    private val leadLocal = Learned(250L)
    private val leadStream = Learned(850L)
    private val restartLocal = Learned(250L)
    private val restartStream = Learned(800L)

    // Whether the next song of the mix in progress is a downloaded file
    private var fadeLocal = false

    private fun nextIsDownloaded(): Boolean {
        val next = main.nextMediaItemIndex
        return next != C.INDEX_UNSET && isDownloaded(main.getMediaItemAt(next).mediaId)
    }

    private fun leadMs(local: Boolean): Long = if (local) leadLocal.value else leadStream.value

    private var plainLogs = 0
    private var plainStart = 0L

    // The song (index and id) for which a mix did not work out; it is not tried again for that song
    private var blockedKey = ""

    private val fadeSetting: Long
        get() = if (autoMix) maxOf(crossfadeMs, AUTOMIX_MS) else crossfadeMs

    // The highest volume, from 0 to 1. It scales the volume of every song, ReplayGain included.
    var volumeLimit: Float = 1f

    private var secondary: ExoPlayer? = null
    private var armedIndex = -1
    private var armedMediaId: String? = null

    private var fading = false
    private var fadeToIndex = -1
    private var fadeMs = 0L
    private var fromFactor = 1f
    private var toFactor = 1f

    // True while the native Auto Mix engine plays the audio: the main player then runs silent as the queue and the clock
    var muted: Boolean = false

    // True while the native engine owns the song that plays: this controller leaves the players alone
    var suspended: Boolean = false

    val isFading: Boolean get() = fading

    /** The volume factor of a song (ReplayGain and the volume limit), for the native engine. */
    fun gainFor(item: MediaItem?): Float = factorOf(item)

    /** Applies the song volume now. Called when the song or a setting changes. */
    fun applyBaseVolume() {
        if (fading) return
        main.volume = if (muted) 0f else factorOf(main.currentMediaItem)
    }

    /** Runs the checks once and returns how long to wait before the next call. */
    fun tick(): Long {
        if (suspended && !fading) {
            disarm()
            return IDLE_TICK_MS
        }
        if (fading) {
            stepFade()
            return if (beatFading || phase == Phase.HANDOVER) BEAT_TICK_MS else FADE_TICK_MS
        }
        val canFade = fadeSetting > 0 &&
            main.isPlaying &&
            main.repeatMode != Player.REPEAT_MODE_ONE &&
            !sleepAtTrackEnd &&
            main.hasNextMediaItem() &&
            // The second player is not tapped, so with AirPlay on the songs just follow each other
            !AirPlayAudio.tapping &&
            !(smartFades && nextIsSameAlbum())
        if (!canFade || blockedKey == currentKey()) {
            disarm()
            return IDLE_TICK_MS
        }
        val fullDuration = durationOf(main)
        if (fullDuration <= 0L) {
            disarm()
            return IDLE_TICK_MS
        }
        // Where the music of this song really ends: a silent tail is not part of the overlap
        val duration = main.currentMediaItem?.mediaId?.let { BeatMixStore.cueOut(it, fullDuration) } ?: fullDuration
        val beat = if (autoMix && beatMix) currentPlan(duration) else null
        // A plan that came too late (its bar line is behind us) leaves a normal AutoMix at the end, not no mix at all
        if (beat != null && main.currentPosition <= beat.startA + BEAT_LATE_MS) return tickBeat(beat)
        // A very short song gets a shorter fade
        val fade = if (autoMix) {
            // The overlap may take at most a third of this song and a third of the next one
            val nextDuration = main.getMediaItemAt(main.nextMediaItemIndex).mediaMetadata.durationMs ?: duration
            min(fadeSetting, min(duration, if (nextDuration > 0L) nextDuration else duration) / 3)
        } else {
            min(fadeSetting, duration / 2)
        }
        val remaining = duration - main.currentPosition
        if (remaining > fade + LEAD_MS || remaining < 0L) {
            disarm()
            return IDLE_TICK_MS
        }
        val index = main.currentMediaItemIndex
        val id = main.currentMediaItem?.mediaId
        if (armedIndex != index || armedMediaId != id) arm(fade, duration)
        val startLead = (secondaryStartMs * speed).toLong()
        if (remaining <= fade + startLead) startFade(fade)
        return if (remaining <= fade + startLead + 300L) BEAT_TICK_MS else ARMED_TICK_MS
    }

    fun release() {
        releaseSecondary()
        fading = false
        phase = Phase.NONE
        mainBass.snapAll(0f, 0f, 0f)
        if (preroll) main.playbackParameters = PlaybackParameters(speed)
        preroll = false
        main.volume = factorOf(main.currentMediaItem)
    }

    /** The plan for the change from the song that plays to the next one, or null while there is none. */
    private fun currentPlan(duration: Long): BeatPlan? {
        val id = main.currentMediaItem?.mediaId ?: return null
        val next = main.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return null
        val nextItem = main.getMediaItemAt(next)
        val key = id + ">" + nextItem.mediaId
        if (key == planKey) return plan
        val aEnd = BeatMixStore.end(id)
        val aExits = BeatMixStore.exits(id).orEmpty()
        if (aEnd == null && aExits.isEmpty()) return null
        val b = BeatMixStore.entries(nextItem.mediaId) ?: return null
        if (!sameKind(id, nextItem.mediaId)) {
            plan = null
            planKey = key
            return null
        }
        val durationB = nextItem.mediaMetadata.durationMs ?: 0L
        val leadA = BeatMixStore.lead(id)
        val leadB = BeatMixStore.lead(nextItem.mediaId)
        val keyA = aEnd?.key?.takeIf { it >= 0 } ?: BeatMixStore.songKey(id)
        val keyB = BeatMixStore.songKey(nextItem.mediaId)
        val wantStems = stemsWanted()
        val position = main.currentPosition
        val st = stems
        // The overlap must still be far enough ahead: for a stem clip the time to make it and the lead-in, else the
        // time to prepare the soft handover
        val stemFrom = position + (if (st != null) (st.estimateMs() * speed).toLong() else 0L) + STEM_LEAD_MS
        val filterFrom = position + FILTER_LEAD_MS
        // With stems the next song may come in right at its start; the overlap then lasts long enough for them
        var found = if (wantStems) {
            BeatMixPlanner.plan(aEnd, aExits, b, duration, durationB, leadA, leadB, 0L, STEM_MIN_FADE_MS, stemFrom, keyA, keyB)
        } else {
            null
        }
        if (found != null && (found.startB < MIN_START_B_MS || abs(found.ratio - 1.0) > STEM_MAX_RATIO_DEVIATION)) {
            val why = stemRefusal(found)
            if (why != null) {
                // Without stems a coming-in place so early in the next song cannot be prepared: plan again without it
                found = null
            }
        }
        if (found == null) {
            found = BeatMixPlanner.plan(aEnd, aExits, b, duration, durationB, leadA, leadB, MIN_START_B_MS, 0L, filterFrom, keyA, keyB)
        }
        // When the tempos cannot be matched, a cut on the bar line is better than a long overlap of two tempos
        // The next song comes in from its start (the first phrases of its intro) when that fits, else where it rises
        plan = found ?: BeatMixPlanner.planCut(aEnd, aExits, introEntries(nextItem.mediaId, b) + b, duration, durationB, MIN_START_B_MS, filterFrom)
        planKey = key
        planKeys = keyReport(keyA, keyB)
        val p = plan
        if (p != null) {
            requestStems(key, id, nextItem, p)
        } else {
        }
        return plan
    }

    private fun keyReport(a: Int, b: Int): String =
        "keys ${KeyDetector.name(a)} > ${KeyDetector.name(b)}" +
            (if (a >= 0 && b >= 0) " (${KeyDetector.distance(a, b)} apart)" else " (not judged)")

    /** Logs what was found, and remembers it for the report of the change. */
    private fun alignNote(text: String) {
        repAlign = text
    }

    private fun stemsWanted(): Boolean = stemMix && stems?.available() == true

    // A clip belongs to one plan: the same two songs can get another plan another time
    private fun stemKey(p: BeatPlan): String = planKey + "@" + p.startA + "/" + p.startB + "/" + p.fadeMs

    /** Why this change cannot be made from stems, or null when it can. */
    private fun stemRefusal(p: BeatPlan): String? {
        val st = stems
        if (!stemsWanted() || st == null) return "stems off"
        if (p.cut) return "a cut"
        if (abs(p.ratio - 1.0) > STEM_MAX_RATIO_DEVIATION) return "tempo differs ${"%.1f".format(abs(p.ratio - 1.0) * 100.0)}%"
        val wall = p.fadeMs / p.ratio
        if (wall < STEM_MIN_FADE_MS) return "overlap only ${wall.toLong()} ms"
        val baseMs = StemMix.baseMs(StemClipSpec(p.startA, p.fadeMs, p.ratio, p.startB, 1f, 1f, p.bassSwap))
        if (baseMs < 0L) return "song A too short for the lead-in"
        val ahead = ((baseMs + StemMix.HANDOVER_AT_MS - main.currentPosition) / speed).toLong()
        if (ahead < st.estimateMs()) return "only $ahead ms left, ${st.estimateMs()} needed"
        return null
    }

    /** Starts making the overlap from stems when this change is suited to it and there is time enough. */
    private fun requestStems(key: String, idA: String, next: MediaItem, p: BeatPlan) {
        val st = stems ?: return
        if (!stemsWanted() || p.cut) return
        val why = stemRefusal(p)
        if (why != null) {
            return
        }
        st.request(stemKey(p), idA, next.mediaId, p, factorOf(main.currentMediaItem), factorOf(next))
    }

    /** The position of the second player in the time of the old song (a stem clip begins somewhere in the song). */
    private fun songTime(s: ExoPlayer): Long = s.currentPosition + (stemClip?.baseMs ?: 0L)

    /** A calm song is not mixed on a busy one, nor the other way round. Unknown or in between: allowed. */
    private fun sameKind(a: String, b: String): Boolean {
        val la = BeatMixStore.loudness(a) ?: return true
        val lb = BeatMixStore.loudness(b) ?: return true
        return !((la < CALM_BELOW && lb > BUSY_ABOVE) || (lb < CALM_BELOW && la > BUSY_ABOVE))
    }

    // Where the second player is put (its start in the old song): in a stem clip a little after the start of the clip,
    // else this long before the bar line minus the lead: room for the soft handover and for the glide to the new tempo
    private fun beatArmPos(p: BeatPlan): Long {
        // Once the second player is armed the choice is made; before that it depends on whether the clip is ready
        val armed = armedIndex == main.currentMediaItemIndex && armedMediaId == main.currentMediaItem?.mediaId
        val clip = if (armed) stemClip else stemClipFor(p)
        if (clip != null) return clip.baseMs + StemMix.HANDOVER_AT_MS
        val glide = if (p.cut || abs(p.ratio - 1.0) < GLIDE_MIN_DEVIATION) 0L else (TEMPO_GLIDE_MS * speed).toLong()
        val before = ((leadMs(nextIsDownloaded()) + HANDOVER_BUDGET_MS + prerollMs(nextIsDownloaded())) * speed * p.ratio).toLong()
        return maxOf(0L, p.startA - before - glide)
    }

    /** The finished stem clip for this plan, when there is one and the old song has not passed its start yet. */
    private fun stemClipFor(p: BeatPlan): StemClip? {
        if (!stemsWanted()) return null
        val clip = stems?.clip(stemKey(p)) ?: return null
        return if (main.currentPosition <= clip.baseMs + StemMix.HANDOVER_AT_MS - STEM_LATE_MS) clip else null
    }

    // How long before the overlap the next song is started silent, to be put on the beat: a streamed song needs more
    private fun prerollMs(local: Boolean): Long = if (local) PREROLL_LOCAL_MS else PREROLL_STREAM_MS

    private fun tickBeat(p: BeatPlan): Long {
        val position = main.currentPosition
        val armAt = beatArmPos(p)
        if (position < armAt - BEAT_LEAD_MS || position > p.startA + BEAT_LATE_MS) {
            disarm()
            return IDLE_TICK_MS
        }
        val index = main.currentMediaItemIndex
        val id = main.currentMediaItem?.mediaId
        if (armedIndex != index || armedMediaId != id) {
            if (!p.cut && p.startB < MIN_START_B_MS && stemClipFor(p) == null) {
                // This coming-in place is only for a stem clip, and there is none in time: a normal AutoMix instead
                plan = null
                disarm()
                return IDLE_TICK_MS
            }
            armBeat(p, armAt)
        }
        if (position >= armAt - (secondaryStartMs * speed).toLong()) startBeatFade(p)
        return BEAT_TICK_MS
    }

    private fun armBeat(p: BeatPlan, at: Long) {
        stemClip = null
        releaseSecondary()
        val item = main.currentMediaItem ?: return
        armedIndex = main.currentMediaItemIndex
        armedMediaId = item.mediaId
        val bass = BassCutProcessor()
        val s = createSecondary(bass)
        secondaryBass = bass
        armedPos = at
        stemClip = stemClipFor(p)
        val clip = stemClip
        if (clip != null) {
            // The overlap made from stems: song A as it is, a glide to the tempo of the next song, then the mix of both
            val start = beatArmPos(p)
            armedPos = start
            s.setMediaItem(MediaItem.fromUri(Uri.fromFile(clip.file)), start - clip.baseMs)
        } else {
            // The same song again, waiting at the place where the soft handover starts, at the speed of the next song's tempo
            s.setMediaItem(item, at)
        }
        s.volume = 0f
        s.playbackParameters = PlaybackParameters(speed)
        s.playWhenReady = false
        s.prepare()
        secondary = s
    }

    private fun arm(fade: Long, duration: Long) {
        releaseSecondary()
        val item = main.currentMediaItem ?: return
        armedIndex = main.currentMediaItemIndex
        armedMediaId = item.mediaId
        val s = createSecondary(BassCutProcessor())
        armedPos = duration - fade
        // The same song again, waiting at the point where the fade starts
        s.setMediaItem(item, armedPos)
        s.volume = 0f
        s.playbackParameters = PlaybackParameters(speed)
        s.playWhenReady = false
        s.prepare()
        secondary = s
    }

    private fun startFade(fade: Long) {
        val s = secondary
        if (s == null || s.playbackState != Player.STATE_READY) {
            // Not buffered in time: no crossfade for this song, the normal gapless change happens
            blockedKey = currentKey()
            releaseSecondary()
            return
        }
        beginFade(s, fade, false)
    }

    private fun startBeatFade(p: BeatPlan) {
        val s = secondary
        if (s == null || s.playbackState != Player.STATE_READY) {
            blockedKey = currentKey()
            releaseSecondary()
            return
        }
        beginFade(s, p.fadeMs, true)
    }

    private fun beginFade(s: ExoPlayer, fade: Long, beat: Boolean) {
        val next = main.nextMediaItemIndex
        if (next == C.INDEX_UNSET) {
            releaseSecondary()
            return
        }
        fromFactor = factorOf(main.currentMediaItem)
        toFactor = factorOf(main.getMediaItemAt(next))
        fadeToIndex = next
        fadeMs = fade
        fading = true
        beatFading = beat
        val now = SystemClock.elapsedRealtime()
        handoverAt = now
        xfadeAt = 0L
        secondaryPos0 = s.currentPosition
        fadeLocal = nextIsDownloaded()
        lastAlignAt = 0L
        firstOffset = null
        jumps = 0
        landed = false
        aligned = false
        lastNudgeAt = 0L
        preroll = false
        prerollTried = false
        lastMainAlignAt = 0L
        holdMainUntil = 0L
        mainJumpAt = 0L
        settlePos = 0L
        rampDone = true
        stemXfadeFrom = -1L
        lastAlignLogAt = 0L
        echoStep = 0
        echoCutAt = 0L
        echoCutWall = 0L
        val reportPlan = plan
        repIds = "${main.currentMediaItem?.mediaId}>${main.getMediaItemAt(next).mediaId}"
        repKind = when {
            !beat -> "plain crossfade"
            stemClip != null -> "stems"
            reportPlan?.cut == true -> if (reportPlan?.echo == true) "echo cut" else "cut"
            else -> "beat mix with filters"
        }
        repPlan = if (beat && reportPlan != null) {
            "ratio ${"%.3f".format(reportPlan.ratio)}, fade $fade ms, ${reportPlan.beats} beats"
        } else {
            "fade $fade ms"
        }
        repKeys = if (beat) planKeys else ""
        repHandover = ""
        repAlign = ""
        repStartAt = now
        if (!beat && fade < HANDOVER_MIN_FADE_MS) {
            // A short crossfade has no room for the soft handover
            s.volume = fromFactor
            s.play()
            finishHandover(s)
            return
        }
        s.volume = 0f
        s.play()
        phase = Phase.HANDOVER
    }

    private fun stepFade() {
        val s = secondary
        if (s == null || s.playerError != null || s.playbackState == Player.STATE_IDLE) {
            endFade("second player gone or failed")
            return
        }
        // The song was changed from outside while the old song was still on the main player
        val expectedIndex = if (phase == Phase.HANDOVER || (!preroll && (phase == Phase.HOLD || stemClip != null))) armedIndex else fadeToIndex
        if (main.currentMediaItemIndex != expectedIndex) {
            endFade("song changed from outside")
            return
        }
        // Pausing, or losing audio focus, pauses both
        if (s.playWhenReady != main.playWhenReady) s.playWhenReady = main.playWhenReady
        when (phase) {
            Phase.HANDOVER -> stepHandover(s)
            Phase.HOLD -> stepHold(s)
            else -> {
                val p = plan
                if (beatFading && p != null) stepBeat(s, p) else stepPlain(s)
            }
        }
    }

    /**
     * The old song moves from the main player to the second player without a gap or a repeat. The second player is
     * started silent a little early. While nobody hears it, its speed is changed a little until it sounds in exactly the
     * same place as the main player (nothing is searched, because a player that was just searched reports a wrong
     * position). Then a short crossfade of the same audio hands the old song over.
     */
    private fun stepHandover(s: ExoPlayer) {
        val now = SystemClock.elapsedRealtime()
        if (xfadeAt == 0L) {
            if (now - handoverAt > HANDOVER_TIMEOUT_MS) {
                // The second player does not get going or does not come in step: the old song plays on the main player
                blockedKey = currentKey()
                endFade("handover gave up after ${now - handoverAt} ms")
                return
            }
            // Wait until the second player really runs
            if (s.playbackState != Player.STATE_READY || !s.isPlaying || s.currentPosition <= secondaryPos0 + 15L) return
            // Positive: the second player is ahead of the main player
            val d = songTime(s) - main.currentPosition
            if (firstOffset == null) {
                firstOffset = d
                // Next time start earlier when it was behind, later when it was ahead
                secondaryStartMs = (secondaryStartMs - d / 2).coerceIn(100L, 700L)
                repHandover = "second player ran after ${now - handoverAt} ms, first offset $d ms"
                if (abs(d) > HANDOVER_GIVE_UP_MS) {
                    blockedKey = currentKey()
                    endFade("handover too far apart ($d ms)")
                    return
                }
            }
            if (abs(d) > HANDOVER_ALIGN_MS) {
                // A little slower when ahead, a little faster when behind
                if (now - lastAlignAt >= 80L) {
                    lastAlignAt = now
                    val m = 1.0 - (d * 0.005).coerceIn(-0.3, 0.3)
                    s.playbackParameters = PlaybackParameters((speed * m).toFloat())
                }
                return
            }
            s.playbackParameters = PlaybackParameters(speed)
            repHandover += ", in step after ${now - handoverAt} ms (offset $d ms)"
            xfadeAt = now
        }
        val u = ((now - xfadeAt).toFloat() / XFADE_MS).coerceIn(0f, 1f)
        main.volume = fromFactor * cos(u * PI.toFloat() / 2f)
        // The volume of the songs is already in a stem clip
        s.volume = (if (stemClip != null) 1f else fromFactor) * sin(u * PI.toFloat() / 2f)
        if (u >= 1f) finishHandover(s)
    }

    private fun finishHandover(s: ExoPlayer) {
        main.volume = 0f
        s.volume = if (stemClip != null) 1f else fromFactor
        if (beatFading) {
            val p = plan
            if (stemClip == null && p != null) {
                // From here the old song glides to the tempo of the next one (a stem clip has that in it)
                val now = songTime(s)
                rampStartA = now
                rampEndA = if (p.cut || abs(p.ratio - 1.0) < GLIDE_MIN_DEVIATION) {
                    now
                } else {
                    p.startA - ((prerollMs(fadeLocal) + leadMs(fadeLocal)) * speed * p.ratio).toLong()
                }
                if (rampEndA - rampStartA < 300L) rampEndA = rampStartA
                rampDone = false
                lastRampSpeed = speed.toDouble()
            }
            phase = Phase.HOLD
        } else {
            launchPlain()
        }
    }

    private fun launchPlain() {
        plainLogs = 0
        plainStart = SystemClock.elapsedRealtime()
        phase = Phase.FADE
        // A next song with a silent start begins where its music begins
        val next = main.nextMediaItemIndex
        val cueIn = if (next != C.INDEX_UNSET) BeatMixStore.cueIn(main.getMediaItemAt(next).mediaId) else null
        if (cueIn != null) main.seekTo(next, cueIn) else main.seekToNextMediaItem()
    }

    /**
     * The old song plays on the second player. The next song is started silent right away, at the place where it has to
     * be, when that place is not before its start (preroll); its speed is then changed a little until it is in step, as
     * was done for the old song at the handover. Without preroll it is started later, so that it is heard on the bar line.
     */
    private fun stepHold(s: ExoPlayer) {
        val p = plan
        if (p == null) {
            endFade("no plan")
            return
        }
        val now = SystemClock.elapsedRealtime()
        val sPos = songTime(s)
        val clip = stemClip
        if (clip != null) {
            // The overlap is in the clip: the next song is started later, during the overlap (see stepStem)
            s.volume = 1f
            main.volume = 0f
            val c = s.currentPosition
            // The next song is started a little before the overlap when that is possible: more time to put it in step
            if (!preroll && c >= clip.overlapMs - STEM_EARLY_MS) startStemPreroll(p, clip, now, c)
            if (preroll) alignMain(s, p, now, 0f)
            if (c >= clip.overlapMs) phase = Phase.FADE
            return
        }
        s.volume = fromFactor
        if (p.echo) echoLead(s, p)
        if (!rampDone) {
            // The old song glides to the tempo of the next one; the next song is started when that is done
            val span = rampEndA - rampStartA
            val u = if (span <= 0L) 1.0 else ((sPos - rampStartA).toDouble() / span).coerceIn(0.0, 1.0)
            val target = speed * (1.0 + (p.ratio - 1.0) * u * u * (3.0 - 2.0 * u))
            // Small steps only: every change of the speed is a change of the audio processing
            if (u >= 1.0 || abs(target - lastRampSpeed) >= 0.002 * speed) {
                lastRampSpeed = target
                s.playbackParameters = PlaybackParameters(target.toFloat())
            }
            if (u < 1.0) return
            rampDone = true
        }
        if (!prerollTried) {
            prerollTried = true
            // Where the next song has to be when it is heard, if the bar lines of both songs fall together
            val target = p.startB + (sPos - p.startA) / p.ratio + leadMs(fadeLocal) * speed
            if (target >= 0.0) {
                preroll = true
                mainBass.snapAll(1f, 1f, 0f)
                main.volume = 0f
                seekAt = now
                settlePos = target.toLong()
                main.seekTo(fadeToIndex, target.toLong())
            }
        }
        if (preroll) {
            main.volume = 0f
            alignMain(s, p, now, 0f)
            if (sPos >= p.startA) phase = Phase.FADE
            return
        }
        val leadSong = leadMs(fadeLocal) * speed * p.ratio
        if (sPos >= p.startA - leadSong) {
            mainBass.snapAll(1f, 1f, 0f)
            seekAt = now
            phase = Phase.FADE
            settlePos = p.startB
            main.seekTo(fadeToIndex, p.startB)
        }
    }

    /**
     * Puts the next song, which runs on the main player, on the beat of the old song with its speed. Silent: up to 30%
     * faster or slower until it is in step. Heard: only small changes. Does nothing until the player really runs.
     */
    private fun alignMain(s: ExoPlayer, p: BeatPlan, now: Long, progress: Float) {
        if (main.playbackState != Player.STATE_READY || !main.isPlaying || main.currentPosition < settlePos + 20L) return
        if (!landed) {
            landed = true
            val took = now - seekAt
            val learned = if (fadeLocal) leadLocal else leadStream
            // A slow start counts at once (the next song is then started earlier), a fast one only half
            learned.value = (if (took > learned.value) took else (learned.value + took) / 2).coerceIn(150L, 2_500L)
        }
        // Positive: the next song is ahead
        val clip = stemClip
        val expected = if (clip != null) {
            // The clip is at the tempo of the next song: both run at the same speed
            (p.startB + (s.currentPosition - clip.overlapMs)).toDouble()
        } else {
            p.startB + (songTime(s) - p.startA) / p.ratio
        }
        val error = main.currentPosition - expected
        if (!aligned && abs(error) <= ALIGN_MS) {
            aligned = true
            alignedAt = now
            alignNote("next song in step, error ${error.toLong()} ms, progress $progress")
        }
        if (!aligned && now - lastAlignLogAt >= 400L) {
            lastAlignLogAt = now
        }
        if (now - lastMainAlignAt < 80L) return
        lastMainAlignAt = now
        val heard = progress > 0f
        val m = when {
            abs(error) <= (if (heard) 8.0 else ALIGN_MS) -> 1.0
            heard -> 1.0 - (error * 0.002).coerceIn(-0.03, 0.03)
            else -> 1.0 - (error * 0.005).coerceIn(-0.3, 0.3)
        }
        main.playbackParameters = PlaybackParameters((speed * m).toFloat())
    }

    /** Volumes, the bass swap and the beat alignment during an overlap on the beat. */
    private fun stepBeat(s: ExoPlayer, p: BeatPlan) {
        val now = SystemClock.elapsedRealtime()
        val clip = stemClip
        if (clip != null) {
            stepStem(s, p, clip, now)
            return
        }
        val sPos = songTime(s)
        val progress = ((sPos - p.startA).toFloat() / fadeMs).coerceIn(0f, 1f)
        if (p.cut && p.echo) {
            // An echo cut has no filters: the old song stops at the bar line (see stepEchoCut)
            secondaryBass?.setBands(0f, 0f)
        } else if (p.cut) {
            // A cut: nothing is taken out, only the bass swaps halfway so that two kicks do not play together
            val swapped = progress >= 0.5f
            secondaryBass?.set(if (swapped) 1f else 0f)
            secondaryBass?.setBands(0f, 0f)
            mainBass.set(if (swapped) 0f else 1f)
            mainBass.setBands(0f, 0f)
        } else {
            // The new song begins with only its highs. At the first swap (a bar line) the melody changes over at once:
            // the old song loses its mids and highs, the new song gets its mids, so its melody runs on the kick and the
            // bass of the old song. At the second swap the bass changes over and the new song is alone.
            val melodySwapped = progress >= p.swap
            val bassSwapped = progress >= p.bassSwap
            secondaryBass?.set(if (bassSwapped) 1f else 0f)
            secondaryBass?.setBands(if (melodySwapped) 1f else 0f, if (melodySwapped) 1f else 0f)
            mainBass.set(if (bassSwapped) 0f else 1f)
            mainBass.setBands(if (melodySwapped) 0f else 1f, 0f)
        }

        // Only a main player that really runs after its last seek tells where the next song is
        if (preroll) {
            // The next song already runs; its speed keeps it in step, and it comes in anyway when it never got there
            alignMain(s, p, now, progress)
            if (!aligned && progress >= ENTER_BY) {
                aligned = true
                alignedAt = now
                alignNote("next song not in step at progress $progress, comes in anyway")
            }
        } else if (main.playbackState == Player.STATE_READY && main.isPlaying &&
            main.currentPosition >= settlePos + 20L && now >= holdMainUntil
        ) {
            if (!landed) {
                landed = true
                val took = now - seekAt
                val learned = if (fadeLocal) leadLocal else leadStream
                learned.value = ((learned.value + took) / 2).coerceIn(150L, 2_500L)
            }
            if (mainJumpAt != 0L) {
                val learned = if (fadeLocal) restartLocal else restartStream
                learned.value = ((learned.value + (now - mainJumpAt)) / 2).coerceIn(80L, 1_500L)
                mainJumpAt = 0L
            }
            // Where song B should be now if the bar lines of both songs fall together; positive: B is ahead
            val expected = p.startB + (sPos - p.startA) / p.ratio
            val errorMs = main.currentPosition - expected
            if (!aligned && abs(errorMs) <= ALIGN_MS) {
                aligned = true
                alignedAt = now
                alignNote("in step after $jumps jumps, error ${errorMs.toLong()} ms, progress $progress")
            }
            if (!aligned && abs(errorMs) > JUMP_MS) {
                if (jumps < MAX_JUMPS && progress < ENTER_BY) {
                    // Nobody hears the next song yet, so it may jump. It lands where it has to be after the restart.
                    jumps++
                    val target = (expected + (if (fadeLocal) restartLocal else restartStream).value).toLong().coerceAtLeast(0L)
                    holdMainUntil = now + 200L
                    mainJumpAt = now
                    settlePos = target
                    main.seekTo(target)
                } else {
                    aligned = true
                    alignedAt = now
                    alignNote("not in step (error ${errorMs.toLong()} ms), next song comes in anyway")
                }
            } else if (abs(errorMs) > 8.0 && now - lastNudgeAt > 250L) {
                lastNudgeAt = now
                // The old song a little faster or slower until the beats agree
                val correction = (errorMs * 0.0004).coerceIn(-MAX_NUDGE, MAX_NUDGE)
                s.playbackParameters = PlaybackParameters((speed * p.ratio * (1 + correction)).toFloat())
            }
        }
        if (!aligned && progress >= LATEST_ENTER) {
            aligned = true
            alignedAt = now
            alignNote("next song never ran in step, comes in anyway at progress $progress")
        }
        if (p.cut && p.echo) {
            stepEchoCut(s, p, now, sPos)
            return
        }
        val enter = if (aligned) ((now - alignedAt).toFloat() / ENTER_RAMP_MS).coerceIn(0f, 1f) else 0f
        main.volume = toFactor * gainIn(progress, p.cut, p.swap, p.bassSwap) * enter
        s.volume = fromFactor * gainOut(progress, p.cut, p.bassSwap)
        if (progress >= 1f || s.playbackState == Player.STATE_ENDED) endFade(if (progress >= 1f) "complete" else "second player ended early")
    }

    /**
     * An echo cut: two beats before the bar line the old song goes into an echo (its own sound plays on as before). The
     * echo is one beat long, so the second beat makes up for the position of the player being known only roughly: the
     * echo is always full.
     */
    private fun echoLead(s: ExoPlayer, p: BeatPlan) {
        if (echoStep != 0 || p.beatMs <= 0.0) return
        val sPos = songTime(s)
        if (sPos < p.startA - 2L * p.beatMs.toLong()) return
        echoStep = 1
        secondaryBass?.echoStart(p.beatMs)
    }

    /**
     * An echo cut, as a DJ does it: the old song is in an echo of one beat; at the bar line, once the next song is in
     * step, the old song itself stops so that only its echo rings on, and the next song comes in at full strength.
     */
    private fun stepEchoCut(s: ExoPlayer, p: BeatPlan, now: Long, sPos: Long) {
        echoLead(s, p)
        if (echoStep == 1 && aligned && sPos >= p.startA) {
            echoStep = 2
            echoCutAt = sPos
            echoCutWall = now
            secondaryBass?.echoCut()
            mainBass.snapAll(0f, 0f, 0f)
        }
        if (echoStep < 2) {
            // Not yet: the old song plays on, the next song waits silent
            main.volume = 0f
            s.volume = fromFactor
            if (s.playbackState == Player.STATE_ENDED) endFade("second player ended early")
            return
        }
        val into = ((now - echoCutWall).toFloat() / ECHO_ENTER_MS).coerceIn(0f, 1f)
        main.volume = toFactor * into
        // The echo rings for the time of the plan and fades out in the last part of it
        val q = ((sPos - echoCutAt).toFloat() / p.fadeMs).coerceIn(0f, 1f)
        s.volume = fromFactor * (if (q < ECHO_HOLD) 1f else cos((q - ECHO_HOLD) / (1f - ECHO_HOLD) * PI.toFloat() / 2f))
        if (q >= 1f || s.playbackState == Player.STATE_ENDED) endFade(if (q >= 1f) "complete" else "second player ended early")
    }

    /**
     * The bar lines at the start of a phrase (every four bars) near the start of a song, from which it can come in: the
     * song then comes in from its beginning, not in the middle. Only places the next song can be prepared for.
     */
    private fun introEntries(id: String, entries: List<BeatEdge>): List<BeatEdge> {
        val e = entries.firstOrNull() ?: return emptyList()
        if (e.bpm <= 0.0) return emptyList()
        val bar = 4.0 * 60000.0 / e.bpm
        val phrase = 4.0 * bar
        val from = (BeatMixStore.cueIn(id) ?: 0L).toDouble()
        // The first bar line of the music, then every four bars
        var pos = e.anchorMs - Math.floor((e.anchorMs - (from - 100.0)) / bar) * bar
        val out = ArrayList<BeatEdge>()
        while (out.size < 2 && pos < INTRO_UNTIL_MS) {
            if (pos >= MIN_START_B_MS) out.add(BeatEdge(e.bpm, pos, e.key))
            pos += phrase
        }
        return out
    }

    /** Starts the next song silent on the main player at the place where it has to be, if that place is not before its start. */
    private fun startStemPreroll(p: BeatPlan, clip: StemClip, now: Long, c: Long) {
        val target = p.startB + (c - clip.overlapMs) + leadMs(fadeLocal) * speed
        if (target < 0f) return
        preroll = true
        mainBass.snapAll(0f, 0f, 0f)
        seekAt = now
        settlePos = target.toLong()
        main.seekTo(fadeToIndex, target.toLong())
    }

    /**
     * The overlap made from stems plays on the second player, at the tempo of the next song. The next song waits silent
     * on the main player: it is started a little before the overlap and put in step with the clip while nobody hears
     * it. It takes over from the clip in a short crossfade once it is in step, in the part of the clip where only the
     * next song plays (at the latest at the end of the clip).
     */
    private fun stepStem(s: ExoPlayer, p: BeatPlan, clip: StemClip, now: Long) {
        s.volume = 1f
        val c = s.currentPosition
        val inOverlap = (c - clip.overlapMs).toFloat()
        val progress = (inOverlap / clip.fadeWallMs).coerceIn(0f, 1f)
        // Milliseconds of playing time left in the overlap
        val leftMs = (clip.fadeWallMs - inOverlap) / speed
        if (!preroll) {
            main.volume = 0f
            if (leftMs <= prerollMs(fadeLocal) + STEM_ALIGN_MS) startStemPreroll(p, clip, now, c)
        } else {
            // Silent until the takeover; then only small changes
            alignMain(s, p, now, if (stemXfadeFrom >= 0L) progress.coerceAtLeast(0.001f) else 0f)
        }
        val xfade = STEM_XFADE_MS * speed
        val overlapEnd = clip.overlapMs + clip.fadeWallMs
        if (preroll && stemXfadeFrom < 0L) {
            val mainRuns = main.playbackState == Player.STATE_READY && main.isPlaying
            val inStep = aligned && mainRuns && c >= overlapEnd - xfade
            val lastMoment = c >= clip.lengthMs - xfade - 150L
            if (inStep || lastMoment) {
                stemXfadeFrom = c
                if (!aligned) repAlign = "next song not in step at the takeover"
            }
        }
        val u = if (stemXfadeFrom >= 0L) ((c - stemXfadeFrom) / xfade).coerceIn(0f, 1f) else 0f
        if (preroll) main.volume = toFactor * u
        if (u > 0f) s.volume = 1f - u
        if (s.playbackState == Player.STATE_ENDED || u >= 1f) {
            endFade(if (u >= 1f) "complete" else "clip ended early")
        }
    }

    // The new song comes up to 0.8 in the first part (only its highs), stays there while its melody runs on the old
    // beat, and comes up to full after the bass swap. A cut is a plain fade of two beats.
    private fun gainIn(p: Float, cut: Boolean, swap: Float, bassSwap: Float): Float = when {
        cut -> sin(p * PI.toFloat() / 2f)
        p < swap -> 0.8f * sin(p / swap * PI.toFloat() / 2f)
        p < bassSwap -> 0.8f
        else -> 0.8f + 0.2f * ((p - bassSwap) / (1f - bassSwap))
    }

    // The old song stays full until the bass swap (what is left of it is its bass), then it is out
    private fun gainOut(p: Float, cut: Boolean, bassSwap: Float): Float = when {
        cut -> cos(p * PI.toFloat() / 2f)
        p < bassSwap -> 1f
        else -> cos((p - bassSwap) / (1f - bassSwap) * PI.toFloat() / 2f)
    }

    private fun stepPlain(s: ExoPlayer) {
        // The fade follows the old song on the second player, so that it ends at the end of the old song
        val progress = ((s.currentPosition - armedPos).toFloat() / fadeMs).coerceIn(0f, 1f)
        if (plainLogs < 8) {
            plainLogs++
        }
        main.volume = toFactor * sin(progress * PI.toFloat() / 2f)
        s.volume = fromFactor * cos(progress * PI.toFloat() / 2f)
        if (progress >= 1f || s.playbackState == Player.STATE_ENDED) endFade(if (progress >= 1f) "complete" else "second player ended early")
    }

    private fun endFade(outcome: String) {
        if (repIds.isNotEmpty()) {
            // One line per change: what kind it was, how it was planned, how the handover and the alignment went
            repIds = ""
        }
        fading = false
        beatFading = false
        phase = Phase.NONE
        mainBass.snapAll(0f, 0f, 0f)
        if (preroll) main.playbackParameters = PlaybackParameters(speed)
        preroll = false
        releaseSecondary()
        armedIndex = -1
        armedMediaId = null
        main.volume = factorOf(main.currentMediaItem)
    }

    /** 0.0.147: true while a handover, a hold or a fade runs. Songs are not taken off the front of the queue then (the indexes would move). */
    val busy: Boolean
        get() = phase != Phase.NONE || fading

    /** 0.0.147: [count] songs were taken off the front of the queue; the place of the armed song moves with it. */
    fun frontRemoved(count: Int) {
        if (armedIndex >= 0) armedIndex = maxOf(-1, armedIndex - count)
    }

    private fun disarm() {
        if (secondary != null) releaseSecondary()
        armedIndex = -1
        armedMediaId = null
    }

    private fun releaseSecondary() {
        secondary?.release()
        secondary = null
        secondaryBass = null
        stemClip = null
    }

    private fun currentKey(): String = main.currentMediaItemIndex.toString() + ":" + (main.currentMediaItem?.mediaId ?: "")

    private fun factorOf(item: MediaItem?): Float =
        replayGainFactor(item?.mediaMetadata?.extras, gainMode) * volumeLimit

    private fun nextIsSameAlbum(): Boolean {
        val next = main.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return false
        val here = main.currentMediaItem?.mediaMetadata?.extras?.getString(EXTRA_ALBUM_ID).orEmpty()
        val there = main.getMediaItemAt(next).mediaMetadata.extras?.getString(EXTRA_ALBUM_ID).orEmpty()
        return here.isNotEmpty() && here == there
    }

    private fun durationOf(player: Player): Long {
        val d = player.duration
        return if (d != C.TIME_UNSET) d else (player.currentMediaItem?.mediaMetadata?.durationMs ?: 0L)
    }

    private companion object {
        const val TAG = "NooklinMix"
        const val AUTOMIX_MS = 10_000L
        const val BEAT_LEAD_MS = 6_000L
        const val BEAT_LATE_MS = 600L
        // Loudness of a song (see BeatAnalyzer) under which it counts as calm and above which it counts as busy
        const val CALM_BELOW = 1.6
        const val BUSY_ABOVE = 2.4
        const val BEAT_TICK_MS = 20L
        const val LEAD_MS = 6_000L
        const val IDLE_TICK_MS = 500L
        const val ARMED_TICK_MS = 100L
        const val FADE_TICK_MS = 50L

        // The soft handover: time allowed for it before a beat mix, how close the two players must be, the crossfade
        // between them, when it gives up, and the shortest fade that gets one
        const val HANDOVER_BUDGET_MS = 900L
        const val HANDOVER_GIVE_UP_MS = 1_000L
        const val HANDOVER_ALIGN_MS = 30L
        const val XFADE_MS = 150f
        const val HANDOVER_TIMEOUT_MS = 3_000L
        const val HANDOVER_MIN_FADE_MS = 3_000L

        // The beat alignment of the next song: close enough, how many silent jumps, the progress by which the next
        // song must come in anyway, how fast it comes in, and the largest speed change of the old song
        const val ALIGN_MS = 25.0
        const val JUMP_MS = 60.0
        const val MAX_JUMPS = 2
        const val LATEST_ENTER = 0.6f
        const val ENTER_BY = 0.4f
        const val ENTER_RAMP_MS = 1_000f
        const val MAX_NUDGE = 0.06

        // The next song starts this long before the overlap, silent, to be put on the beat (downloaded, streamed)
        const val PREROLL_LOCAL_MS = 2_500L
        const val PREROLL_STREAM_MS = 6_000L

        // A coming-in place under this far into the next song is not used when the overlap is not made from stems
        const val MIN_START_B_MS = 8_000L

        // The intro phrases of the next song are only looked for in the first minute; an echo cut: how fast the next song
        // comes in, and the part of the echo that rings at full strength before it fades out
        const val INTRO_UNTIL_MS = 60_000.0
        const val ECHO_ENTER_MS = 40f
        const val ECHO_HOLD = 0.7f

        // Stems: the tempos may differ by this much (song A is read faster or slower, the pitch follows), the shortest
        // overlap, the latest start of the second player in a clip (before the place it starts), how long before the
        // end of the overlap the next song is put in step (on top of its preroll), the last part with only small speed
        // changes, the final crossfade
        const val STEM_MAX_RATIO_DEVIATION = 0.06
        const val STEM_MIN_FADE_MS = 5_000L
        const val STEM_LATE_MS = 200L
        const val STEM_ALIGN_MS = 3_000L
        const val STEM_EARLY_MS = 2_000L

        // The overlap must begin this far after the end of the clip's making (stems: the lead-in of the clip) or after
        // now (filters: the time to prepare the handover)
        const val STEM_LEAD_MS = 9_000L
        const val FILTER_LEAD_MS = 9_000L
        const val STEM_XFADE_MS = 300f

        // Without stems: the old song glides to the tempo of the next one in about this long, and a tempo difference
        // under this much needs no glide
        const val TEMPO_GLIDE_MS = 4_000L
        const val GLIDE_MIN_DEVIATION = 0.002
    }
}
