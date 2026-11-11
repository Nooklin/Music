package org.nooklin.music.playback.mix

/**
 * The native Auto Mix engine (see app/src/main/cpp): decoded songs live in native memory as buffers, one output stream
 * plays them, and the engine does the overlaps. Maps and plans cross the border as packed double arrays.
 */
object NativeMix {
    /** False when the library could not be loaded (then Auto Mix falls back to the normal player). */
    val available: Boolean by lazy {
        try {
            System.loadLibrary("nooklinmix")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    /** What the engine reports; read with [status]. */
    class Status(private val v: DoubleArray) {
        val state: Int get() = v[0].toInt()  // 0 idle, 1 playing, 2 ramp, 3 armed, 4 blend, 5 fade, 6 outro (echo, filter or cut)
        val positionSec: Double get() = v[1]
        val phaseErrorMs: Double get() = v[2]
        val blendBeats: Double get() = v[3]
        val changes: Int get() = v[4].toInt()
        val ended: Boolean get() = v[5] != 0.0
        val pausedSilent: Boolean get() = v[6] != 0.0
        val epoch: Int get() = v[7].toInt()
        val blending: Boolean get() = state == 4
    }

    fun status(): Status = Status(nativeStatus())

    fun start(): Boolean = available && nativeStart()
    fun release() { if (available) nativeRelease() }
    fun sleepStream() { if (available) nativeSleepStream() }

    /** The output device of the engine (AudioDeviceInfo.id); 0 lets Android decide. An open stream is reopened on it. */
    fun setOutputDevice(id: Int) { if (available) nativeSetOutputDevice(id) }

    fun newBuffer(sampleRate: Int, expectedFrames: Long): Long = nativeNewBuffer(sampleRate, expectedFrames)
    fun append(handle: Long, interleaved: ShortArray, frames: Int) = nativeAppend(handle, interleaved, frames)
    fun finish(handle: Long) = nativeFinish(handle)
    fun frames(handle: Long): Long = nativeFrames(handle)
    fun freeBuffer(handle: Long) { if (available) nativeFreeBuffer(handle) }

    /** ALAC (Apple Lossless) decoding without a device codec: 0 when the stream is not supported. */
    fun alacOpen(cookie: ByteArray): Long = nativeAlacOpen(cookie)
    /** [sampleRate, channels, bitDepth, frameLength] */
    fun alacInfo(alac: Long): IntArray = nativeAlacInfo(alac)
    /** Decodes one packet into [out] (16-bit stereo, room for frameLength frames); returns the frames, -1 when damaged. */
    fun alacDecode(alac: Long, packet: ByteArray, size: Int, out: ShortArray): Int = nativeAlacDecode(alac, packet, size, out)
    fun alacClose(alac: Long) = nativeAlacClose(alac)

    fun analyze(handle: Long): DoubleArray = nativeAnalyze(handle, 0.0)
    /** The grid built at [bpm] instead of the tempo the search chose (see MixTempo). */
    fun analyzeAt(handle: Long, bpm: Double): DoubleArray = nativeAnalyze(handle, bpm)
    /** The key of a whole song (root + 12 when minor), -1 when it is not clear. */
    fun keyOf(map: DoubleArray): Int = nativeKeyOf(map)
    /** What the planner saw and why blends were turned down (for the log) */
    fun explain(a: DoubleArray, b: DoubleArray, nowA: Double, maxTempoChange: Double, genre: Int, stems: Boolean = false, loops: Boolean = false, prevStyle: Int = -1, prevExit: Int = -1): String =
        nativeExplain(a, b, nowA, maxTempoChange, genre, stems, loops, prevStyle, prevExit)
    /** [stems]: the singing of one song can be taken out of the overlap, so two songs that sing together cost little. */
    fun plan(a: DoubleArray, b: DoubleArray, nowA: Double, maxTempoChange: Double = 0.06, genre: Int = 0, stems: Boolean = false, loops: Boolean = false, prevStyle: Int = -1, prevExit: Int = -1): DoubleArray =
        nativePlan(a, b, nowA, maxTempoChange, genre, stems, loops, prevStyle, prevExit)

    /** A copy of [frames] frames of a song as decoded (16-bit stereo, 44.1 kHz, interleaved) from frame [start]; null when not possible. */
    fun copy(handle: Long, start: Long, frames: Int): ShortArray? = nativeCopy(handle, start, frames)
    /** Puts other audio (interleaved 16-bit stereo) over a stretch of a song from frame [start]; one stretch per song. */
    fun setOverlay(handle: Long, start: Long, data: ShortArray, frames: Int): Boolean = nativeSetOverlay(handle, start, data, frames)
    fun clearOverlay(handle: Long) { if (available) nativeClearOverlay(handle) }

    fun play(handle: Long, map: DoubleArray, startSec: Double): Boolean = nativePlay(handle, map, startSec)
    fun loadNext(handle: Long, map: DoubleArray): Boolean = nativeLoadNext(handle, map)
    fun setPlan(plan: DoubleArray): Boolean = nativeSetPlan(plan)
    fun clearPlan(): Boolean = nativeClearPlan()
    fun dropNext(): Boolean = nativeDropNext()
    fun stop(): Boolean = available && nativeStop()
    fun setCurrentMap(map: DoubleArray, epoch: Int): Boolean = nativeSetCurrentMap(map, epoch)
    fun setSpeed(v: Double) = nativeSetSpeed(v)
    fun setGain(v: Double) = nativeSetGain(v)
    fun setPaused(p: Boolean) = nativeSetPaused(p)

    /** 0.0.110: AirPlay takes what the engine plays: [on] copies the audio into a ring, [muteLocal] makes the phone's own output silent. */
    fun tap(on: Boolean, muteLocal: Boolean) { if (available) nativeTapEnable(on, muteLocal) }
    fun tapFlush() { if (available) nativeTapFlush() }
    /** Takes up to [maxFrames] frames (16-bit stereo, interleaved) out of the ring; returns how many. */
    fun tapRead(out: ShortArray, maxFrames: Int): Int = if (available) nativeTapRead(out, maxFrames) else 0

    @JvmStatic private external fun nativeTapEnable(on: Boolean, muteLocal: Boolean)
    @JvmStatic private external fun nativeTapFlush()
    @JvmStatic private external fun nativeTapRead(out: ShortArray, maxFrames: Int): Int
    @JvmStatic private external fun nativeStart(): Boolean
    @JvmStatic private external fun nativeSleepStream()
    @JvmStatic private external fun nativeSetOutputDevice(id: Int)
    @JvmStatic private external fun nativeRelease()
    @JvmStatic private external fun nativeNewBuffer(srcRate: Int, expectedFrames: Long): Long
    @JvmStatic private external fun nativeAppend(handle: Long, data: ShortArray, frames: Int)
    @JvmStatic private external fun nativeFinish(handle: Long)
    @JvmStatic private external fun nativeFrames(handle: Long): Long
    @JvmStatic private external fun nativeFreeBuffer(handle: Long)
    @JvmStatic private external fun nativeAlacOpen(cookie: ByteArray): Long
    @JvmStatic private external fun nativeAlacInfo(alac: Long): IntArray
    @JvmStatic private external fun nativeAlacDecode(alac: Long, packet: ByteArray, size: Int, out: ShortArray): Int
    @JvmStatic private external fun nativeAlacClose(alac: Long)
    @JvmStatic private external fun nativeAnalyze(handle: Long, forcedBpm: Double): DoubleArray
    @JvmStatic private external fun nativeKeyOf(map: DoubleArray): Int
    @JvmStatic private external fun nativeExplain(a: DoubleArray, b: DoubleArray, nowA: Double, maxTempo: Double, genre: Int, stems: Boolean, loops: Boolean, prevStyle: Int, prevExit: Int): String
    @JvmStatic private external fun nativePlan(a: DoubleArray, b: DoubleArray, nowA: Double, maxTempo: Double, genre: Int, stems: Boolean, loops: Boolean, prevStyle: Int, prevExit: Int): DoubleArray
    @JvmStatic private external fun nativeCopy(handle: Long, start: Long, frames: Int): ShortArray?
    @JvmStatic private external fun nativeSetOverlay(handle: Long, start: Long, data: ShortArray, frames: Int): Boolean
    @JvmStatic private external fun nativeClearOverlay(handle: Long)
    @JvmStatic private external fun nativePlay(handle: Long, map: DoubleArray, startSec: Double): Boolean
    @JvmStatic private external fun nativeLoadNext(handle: Long, map: DoubleArray): Boolean
    @JvmStatic private external fun nativeSetPlan(plan: DoubleArray): Boolean
    @JvmStatic private external fun nativeClearPlan(): Boolean
    @JvmStatic private external fun nativeDropNext(): Boolean
    @JvmStatic private external fun nativeStop(): Boolean
    @JvmStatic private external fun nativeReclaim()
    @JvmStatic private external fun nativeSetCurrentMap(map: DoubleArray, epoch: Int): Boolean
    @JvmStatic private external fun nativeSetSpeed(v: Double)
    @JvmStatic private external fun nativeSetGain(v: Double)
    @JvmStatic private external fun nativeSetPaused(p: Boolean)
    @JvmStatic private external fun nativeNextSlotFree(): Boolean
    @JvmStatic private external fun nativeStatus(): DoubleArray

}

/** A packed beat grid (see mix_jni.cpp for the layout) with the few things the Kotlin side reads. */
object MixMap {
    val EMPTY = DoubleArray(8)
    fun bpm(m: DoubleArray) = m[0]
    fun duration(m: DoubleArray) = m[6]
    fun beatCount(m: DoubleArray) = m[7].toInt()
    /** The time in seconds of beat [beat] (a fraction is interpolated, outside the grid the last gap goes on). */
    fun timeAtBeat(m: DoubleArray, beat: Double): Double {
        val n = beatCount(m)
        if (m.size < 8 + n || n < 2) return 0.0
        val i = Math.floor(beat).toInt().coerceIn(0, n - 2)
        return m[8 + i] + (beat - i) * (m[9 + i] - m[8 + i])
    }
    fun valid(m: DoubleArray) = m[0] > 0.0 && m[7] >= 16.0
    /** True when the song was really analysed (an empty map is the "not analysed" marker). */
    fun analysed(m: DoubleArray) = m.size >= 8 && m[6] > 0.0

    /** Where the count of singing values sits in a map (after beats, loudness, pitch classes and sections); -1 for an older map. */
    private fun vocalIndex(m: DoubleArray): Int {
        if (m.size < 8) return -1
        var i = 8 + m[7].toInt()
        repeat(2) {
            if (i >= m.size) return -1
            i += 1 + m[i].toInt()
        }
        if (i >= m.size) return -1
        i += 1 + 3 * m[i].toInt()
        return if (i + 1 < m.size) i + 1 else -1  // [i] is the loudness, the count follows
    }

    /** The map with a singing share per beat (0 to 1, -1 where not measured); [perSecond] is indexed by whole seconds. */
    fun withVocal(m: DoubleArray, perSecond: FloatArray): DoubleArray {
        val at = vocalIndex(m)
        if (at < 0) return m
        val n = m[7].toInt()
        // What follows the singing values (the tempo evidence) is kept
        val tailAt = at + 1 + m[at].toInt()
        val tail = if (tailAt < m.size) m.copyOfRange(tailAt, m.size) else DoubleArray(0)
        val out = DoubleArray(at + 1 + n + tail.size)
        System.arraycopy(m, 0, out, 0, at)
        System.arraycopy(tail, 0, out, at + 1 + n, tail.size)
        out[at] = n.toDouble()
        for (b in 0 until n) {
            val s = m[8 + b].toInt()
            out[at + 1 + b] = if (s >= 0 && s < perSecond.size) perSecond[s].toDouble() else -1.0
        }
        return out
    }

    /**
     * How well a grid at each tempo fits the strong onsets of the song, as (bpm, coverage): the first pair is the grid itself,
     * the others the strongest candidates of the tempo search. Empty for a map without this evidence.
     */
    fun tempoEvidence(m: DoubleArray): List<Pair<Double, Double>> {
        val at = vocalIndex(m)
        if (at < 0) return emptyList()
        val i = at + 1 + m[at].toInt()
        if (i >= m.size) return emptyList()
        val n = minOf(m[i].toInt(), (m.size - i - 1) / 2)
        return List(n) { Pair(m[i + 1 + 2 * it], m[i + 2 + 2 * it]) }
    }

    /** Level of the music in dB (0 when not known); the engine uses it to bring songs to the same loudness. */
    fun loudness(m: DoubleArray): Double {
        val at = vocalIndex(m)
        return if (at >= 1) m[at - 1] else 0.0
    }

    /**
     * One number from 0 to 1 for how energetic the whole song is, for the modes of the AutoMix menu: 60% the level of the music
     * (-14 dB is 0, -7 dB is 1) and 40% the share of the song that lies in loud sections. -1 when the map does not say.
     */
    fun energyIndex(m: DoubleArray): Double {
        if (m.size < 8) return -1.0
        val n = m[7].toInt()
        var i = 8 + n
        repeat(2) {
            if (i >= m.size) return -1.0
            i += 1 + m[i].toInt()
        }
        if (i >= m.size) return -1.0
        val c = m[i].toInt()
        val loudAt = i + 1 + 3 * c
        if (c < 0 || loudAt >= m.size || n <= 0) return -1.0
        var high = 0.0
        for (k in 0 until c) {
            val from = m[i + 1 + 3 * k]
            val to = if (k + 1 < c) m[i + 1 + 3 * (k + 1)] else n.toDouble()
            if (m[i + 2 + 3 * k] > 0.5) high += maxOf(0.0, to - from)
        }
        val share = (high / n).coerceIn(0.0, 1.0)
        val loud = m[loudAt]
        val level = if (loud < -1.0 && loud > -60.0) ((loud + 14.0) / 7.0).coerceIn(0.0, 1.0) else 0.5
        return 0.6 * level + 0.4 * share
    }

    /**
     * 0.0.103: what the analysis found about where the song can be mixed in and out. Null for a map made before this version.
     * [cueConfidence] 0 to 1, [phraseBeats] 8, 16 or 32, [introEnd] and [outroStart] in beats, [usableIntroBeats] and
     * [usableOutroBeats] in beats, [energyStart] and [energyEnd] the relative energy of the first and the last 16 beats,
     * [swing] the position of the off-beat eighth (0.5 straight).
     */
    class Cues(
        val cueConfidence: Double, val phraseBeats: Int, val introEnd: Double, val outroStart: Double,
        val usableIntroBeats: Int, val usableOutroBeats: Int, val energyStart: Double, val energyEnd: Double, val swing: Double
    )

    fun cues(m: DoubleArray): Cues? {
        val at = vocalIndex(m)
        if (at < 0) return null
        val i = at + 1 + m[at].toInt()
        if (i >= m.size) return null
        val j = i + 1 + 2 * m[i].toInt()
        if (j + 10 > m.size || m[j] < 9.0) return null
        return Cues(m[j + 1], m[j + 2].toInt(), m[j + 3], m[j + 4], m[j + 5].toInt(), m[j + 6].toInt(), m[j + 7], m[j + 8], m[j + 9])
    }

    /** True when the map carries the cue fields. */
    fun hasCues(m: DoubleArray): Boolean = cues(m) != null

    /** True when the map carries singing values. */
    fun hasVocal(m: DoubleArray): Boolean {
        val at = vocalIndex(m)
        return at >= 0 && m[at] > 0.0
    }
}

/** Plan kinds as the planner returns them. */
object MixPlan {
    const val LONG_BLEND = 0
    const val BASS_SWAP_SHORT = 1
    const val FALLBACK = 2
    const val ECHO_OUT = 3
    const val FILTER_OUT = 4
    const val CUT = 5
    const val REVERB_OUT = 6
    const val BRAKE_OUT = 7
    const val SPIN_OUT = 8
    const val ROLL_OUT = 9
    const val FADE_OUT = 10
    /** B comes in on a drop (exit techniques) */
    fun drop(p: DoubleArray) = p.size > 19 && p[19].toInt() and 1 != 0
    /** A rising noise over the last four beats of A */
    fun riser(p: DoubleArray) = p.size > 19 && p[19].toInt() and 8 != 0
    /** A low hit on the first beat of B */
    fun impact(p: DoubleArray) = p.size > 19 && p[19].toInt() and 16 != 0
    /** The tempo change is shared by both songs */
    fun split(p: DoubleArray) = p.size > 19 && p[19].toInt() and 2 != 0
    fun tempoRatioB(p: DoubleArray) = if (p.size > 16) p[16] else 1.0
    /** How the plan was made: 0 phrase based, 1 relaxed, 2 rolling, 3 double drop, 4 exit */
    fun style(p: DoubleArray) = if (p.size > 20) p[20].toInt() else 0
    fun vocalA(p: DoubleArray) = if (p.size > 21) p[21] else -1.0
    fun vocalB(p: DoubleArray) = if (p.size > 22) p[22] else -1.0
    /** Share of the overlapped beats where both songs sing (-1 unknown); the planner charges 4 times this */
    fun vocalOverlap(p: DoubleArray) = if (p.size > 24) p[24] else -1.0
    /** A long blend in which drums, bass, melody and singing of both songs get their own volume curve (needs the stems of both). */
    /** A long blend in which the last bars of A repeat after [loopBeat] (the app makes that audio, see MixLoops). */
    fun loop(p: DoubleArray) = p.size > 26 && (p[19].toInt() and 128) != 0 && p[26] > 0.0
    fun loopBeat(p: DoubleArray) = if (p.size > 26) p[25] else 0.0
    fun loopBeats(p: DoubleArray) = if (p.size > 26) p[26].toInt() else 0
    fun stemMix(p: DoubleArray) = p.size > 19 && (p[19].toInt() and 32) != 0
    /** Singing on both sides: A's mids go out at the start of the overlap */
    fun vocalCut(p: DoubleArray) = p.size > 19 && p[19].toInt() and 4 != 0
    fun genreClass(p: DoubleArray) = if (p.size > 23) p[23].toInt() else 0
    /** Beat of the overlap (from its start) where the bass of A hands over to B; -1: the default */
    fun bassSwapBeat(p: DoubleArray) = if (p.size > 27) p[27] else -1.0
    /** A hard bass swap on one beat (hardstyle and other kick based genres) */
    fun hardSwap(p: DoubleArray) = p.size > 19 && (p[19].toInt() and 256) != 0

    /** The choice in plain words, for the log: what was chosen and why. */
    fun reasons(p: DoubleArray, bpmA: Double, bpmB: Double): String {
        val r = ArrayList<String>()
        val genre = when (genreClass(p)) {
            1 -> "family D (hip-hop, pop, dancehall, rock: short and vocal)"
            2 -> "family A (house, techno, trance, disco: long blends)"
            3 -> "family B (drum and bass, UKG, dubstep: 16 to 32 beats, drop swaps)"
            4 -> "family C (hardstyle, hard techno, psytrance: kick swap)"
            else -> null
        }
        if (genre != null) r.add("genre counts as $genre")
        when (style(p)) {
            1 -> r.add("A is going down (outro or breakdown) and B starts at its intro, so the intro plays over the outro for ${lengthBeats(p)} beats")
            2 -> r.add("B drops as A leaves its loud part, the overlap ends on that boundary (${lengthBeats(p)} beats)")
            3 -> r.add("both songs drop at the same moment (double drop, ${lengthBeats(p)} beats), allowed because the tempos are within 5%")
            6 -> r.add("A is in a breakdown before its own drop; the overlap ends there, A stops and B drops (breakdown swap, ${lengthBeats(p)} beats)")
            5 -> r.add("A has no outro to blend over: its last ${loopBeats(p)} beats repeat until the blend of ${lengthBeats(p)} beats is over (beat loop)")
            4 -> when (kind(p)) {
                CUT -> r.add(if (drop(p)) "cut: a loud A leaves on the bar and B comes in on its drop" else "cut: B comes in strong on the bar")
                ECHO_OUT, FILTER_OUT, REVERB_OUT, BRAKE_OUT, SPIN_OUT, ROLL_OUT, FADE_OUT ->
                    r.add("${exitName(p)}: no blend fits well enough (tempo gap, no outro or intro that fits, clashing keys or both singing), the effect is picked from the genre, the energy and the singing at the end of A")
            }
            else -> when (kind(p)) {
                CUT -> r.add("cut on a bar line (the songs show little structure)")
                ECHO_OUT, FILTER_OUT, REVERB_OUT, BRAKE_OUT, SPIN_OUT, ROLL_OUT, FADE_OUT -> r.add("${exitName(p)} (the songs show little structure)")
                else -> r.add("phrase based blend of ${lengthBeats(p)} beats (the songs show little structure)")
            }
        }
        if (style(p) in 1..3 || style(p) == 6) {
            val ratio = tempoRatio(p)
            val rb = tempoRatioB(p)
            if (Math.abs(ratio - 1.0) > 0.002 || Math.abs(rb - 1.0) > 0.002) {
                r.add("tempo: A x${"%.3f".format(ratio)}, B x${"%.3f".format(rb)} during the overlap (${"%.1f".format(bpmA)} and ${"%.1f".format(bpmB)} bpm)" + if (split(p)) ", shared by both songs" else "")
            } else {
                r.add("tempos are equal enough to need no change")
            }
        }
        val va = vocalA(p)
        val vb = vocalB(p)
        if (va >= 0.0 || vb >= 0.0) {
            r.add("singing in the overlap: A ${if (va >= 0.0) "%.0f%%".format(va * 100) else "unknown"}, B ${if (vb >= 0.0) "%.0f%%".format(vb * 100) else "unknown"}")
            if (vocalCut(p)) r.add("both sing at some point, so A's mids go out at the start of the overlap")
        } else {
            r.add("singing not measured yet")
        }
        if (keyClash(p) > 0.2) r.add("the keys clash somewhat (${"%.2f".format(keyClash(p))}), so the overlap stays short")
        return r.joinToString("; ")
    }

    fun styleName(p: DoubleArray) = when (style(p)) {
        1 -> "relaxed"
        2 -> "rolling"
        3 -> "double drop"
        6 -> "breakdown swap"
        4 -> "exit"
        else -> "phrase"
    }
    fun kind(p: DoubleArray) = p[0].toInt()
    fun lengthBeats(p: DoubleArray) = p[3].toInt()
    fun tempoRatio(p: DoubleArray) = p[5]
    /** Relative loudness of the stretch of A and of B that overlap (1 = the median of the song). */
    fun energyA(p: DoubleArray) = p[10]
    fun energyB(p: DoubleArray) = p[11]
    /** How much the keys clash, 0 to 1, weighted by how sure they are. */
    fun keyClash(p: DoubleArray) = p[12]
    fun cost(p: DoubleArray) = p[13]
    /** "A minor", "C major" or "unknown" for a key code of the plan (root + 12 when minor, -1 unknown). */
    fun keyName(code: Double): String {
        val c = code.toInt()
        if (c < 0) return "unknown"
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return names[c % 12] + if (c >= 12) " minor" else " major"
    }
    fun keyA(p: DoubleArray) = keyName(p[14])
    fun keyB(p: DoubleArray) = keyName(p[15])
    /** The exit in words, with the extras: "roll out + riser + impact onto a drop". */
    fun exitName(p: DoubleArray): String {
        val base = when (kind(p)) {
            ECHO_OUT -> "echo out"
            FILTER_OUT -> "filter out"
            REVERB_OUT -> "reverb out"
            BRAKE_OUT -> "brake out"
            SPIN_OUT -> "spin back out"
            ROLL_OUT -> "beat roll out"
            FADE_OUT -> "fade out and in"
            else -> "cut"
        }
        return base + (if (riser(p)) " + riser" else "") + (if (impact(p)) " + impact" else "") + if (drop(p)) " onto a drop" else ""
    }
    fun describe(p: DoubleArray): String = styleName(p) + ": " + when (kind(p)) {
        LONG_BLEND -> "long blend ${lengthBeats(p)} beats" + (if (stemMix(p)) " (stem mix)" else "") + (if (loop(p)) " (beat loop)" else "")
        BASS_SWAP_SHORT -> "short swap ${lengthBeats(p)} beats"
        ECHO_OUT, FILTER_OUT, REVERB_OUT, BRAKE_OUT, SPIN_OUT, ROLL_OUT, FADE_OUT, CUT -> exitName(p)
        else -> "fade"
    } + if (split(p)) " (tempo shared)" else ""
}
