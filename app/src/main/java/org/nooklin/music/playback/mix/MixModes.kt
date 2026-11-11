package org.nooklin.music.playback.mix

import android.content.Context
import android.util.Log
import kotlin.math.abs
import kotlin.math.ln
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.playback.QueueEntry
import org.nooklin.music.playback.beat.BeatMixStore
import org.nooklin.music.playback.beat.MixOrder

/** The modes of the AutoMix menu (long press on the AutoMix button). */
enum class MixMode(val id: String, val label: String) {
    MIXED("mixed", "Mixed"),
    ENERGY("energy", "Energy"),
    BUILD_UP("build_up", "Build-Up"),
    CHILL("chill", "Chill"),
    COOLDOWN("cooldown", "Cooldown"),
    TEMPOZONE("tempozone", "Tempozone"),
    HARMONIC("harmonic", "Harmonic"),
    GENRES("genres", "Genres");

    companion object {
        fun of(id: String): MixMode? = values().firstOrNull { it.id == id }
    }
}

/**
 * Makes the list of songs for a mode of the AutoMix menu. Only downloaded songs that have been analysed (tempo known, see
 * [MixFacts]) are used, so that every change can really be mixed. The songs are put in a DJ order ([MixOrder]).
 *
 * - Mixed: any of them. Energy: a tempo of at least [ENERGY_MIN_BPM]. Chill: a tempo under [CHILL_MAX_BPM] and an energy
 *   under [CHILL_MAX_ENERGY]. Tempozone: a tempo in the zone picked. Genres: the genres picked.
 * - Build-Up / Cooldown: the energy of the songs goes up / down over the list, in steps of [CHUNK] songs, each step in DJ order.
 * - Harmonic: the key counts [HARMONIC_KEY_WEIGHT] times as much in the order.
 */
object MixModes {
    private const val TAG = "NooklinMix"
    const val ENERGY_MIN_BPM = 120.0
    const val CHILL_MAX_BPM = 100.0
    const val CHILL_MAX_ENERGY = 0.45
    const val LIST_SIZE = 40
    const val REFILL_BELOW = 10
    const val REFILL_SIZE = 30
    private const val POOL_FACTOR = 3
    private const val CHUNK = 5
    private const val HARMONIC_KEY_WEIGHT = 4.0
    private const val TEMPO_AGREE = 0.04

    /** Tempo zones in bpm: from (included) to (not included). */
    val ZONES = listOf(0.0 to 90.0, 90.0 to 110.0, 110.0 to 130.0, 130.0 to 150.0, 150.0 to 1000.0)

    fun zoneLabel(zone: Int): String {
        val z = ZONES.getOrNull(zone) ?: return ""
        return when {
            z.first <= 0.0 -> "Under ${z.second.toInt()} bpm"
            z.second >= 1000.0 -> "Over ${z.first.toInt()} bpm"
            else -> "${z.first.toInt()} to ${z.second.toInt()} bpm"
        }
    }

    /** Reads what the analysis noted per song (and gives older songs their energy). Call off the main thread. */
    fun prepare(context: Context) {
        val app = context.applicationContext
        BeatMixStore.preload(app)
        MixFacts.preload(app)
        val filled = MixFacts.backfillEnergy(app)
    }

    private fun agrees(a: Double, b: Double): Boolean {
        val r = a / b
        return doubleArrayOf(0.5, 1.0, 2.0).any { abs(ln(r / it)) < TEMPO_AGREE }
    }

    /**
     * The tempo of a song for the modes: the grid of the analysis when it agrees with the older analysis of the whole song
     * (up to a factor 2), the older analysis when they disagree, and whichever is there when only one is. 0 when not known.
     * A song that is gridded at double its felt tempo counts at the grid tempo.
     */
    fun tempoOf(id: String): Double {
        val grid = MixFacts.bpm(id)
        val old = BeatMixStore.entries(id)?.firstOrNull()?.bpm?.takeIf { it > 0.0 }
        return when {
            grid == null -> old ?: 0.0
            old == null -> grid
            agrees(grid, old) -> grid
            else -> old
        }
    }

    private fun suitable(mode: MixMode, song: Song, zone: Int, genres: Set<String>): Boolean {
        if (!MixFacts.known(song.id)) return false
        val bpm = tempoOf(song.id)
        if (bpm <= 0.0) return false
        val energy = MixFacts.energy(song.id)
        return when (mode) {
            MixMode.MIXED, MixMode.HARMONIC -> true
            MixMode.ENERGY -> bpm >= ENERGY_MIN_BPM
            MixMode.CHILL -> bpm < CHILL_MAX_BPM && energy != null && energy < CHILL_MAX_ENERGY
            MixMode.BUILD_UP, MixMode.COOLDOWN -> energy != null
            MixMode.TEMPOZONE -> ZONES.getOrNull(zone)?.let { bpm >= it.first && bpm < it.second } ?: false
            MixMode.GENRES -> song.genre.trim().lowercase() in genres
        }
    }

    private fun dj(
        seedId: String?, songs: List<Song>, keyWeight: Double = 1.0, prior: List<MixOrder.Item> = emptyList(), maxPlaces: Int = Int.MAX_VALUE
    ): List<Song> = when {
        songs.size < 2 -> songs
        seedId == null -> MixOrder.shuffled(songs)
        keyWeight != 1.0 -> MixOrder.after(seedId, songs, keyWeight, prior, maxPlaces)
        else -> MixOrder.after(seedId, songs, prior, maxPlaces)
    }

    /**
     * The songs for [mode], at most [size], none of [exclude] (the songs already in the queue). [seedId] is the song that
     * plays now: the first of the list suits it. Empty when no downloaded, analysed song fits. [prepare] must have run.
     */
    fun build(
        mode: MixMode, zone: Int, genres: Set<String>, downloaded: Collection<Song>, seedId: String?, exclude: Set<String>, size: Int,
        // 0.0.135: the songs right before the new ones (the end of the queue); without them the song [seedId] counts
        recent: List<MixOrder.Item> = emptyList()
    ): List<Song> {
        // 0.0.135: the song playing now counts for the artist and album rule of the ordering
        val before = recent.ifEmpty { listOfNotNull(seedId?.let { id -> downloaded.firstOrNull { it.id == id } }?.let(MixOrder::itemOf)) }
        val fit = downloaded.filter { it.id !in exclude && it.id != seedId && suitable(mode, it, zone, genres) }
        if (fit.isEmpty()) return emptyList()
        val result = when (mode) {
            MixMode.BUILD_UP, MixMode.COOLDOWN -> {
                val pool = fit.shuffled().take(size)
                val sorted = pool.sortedBy { MixFacts.energy(it.id) ?: 0.0 }.let { if (mode == MixMode.COOLDOWN) it.reversed() else it }
                var prev = seedId
                val out = ArrayList<Song>(sorted.size)
                for (chunk in sorted.chunked(CHUNK)) {
                    // 0.0.135: the songs of the chunks before count, so the artist and album rule runs on across the chunks
                    val ordered = dj(prev, chunk, 1.0, (before + out.takeLast(3).map(MixOrder::itemOf)).takeLast(3))
                    out.addAll(ordered)
                    prev = ordered.last().id
                }
                out
            }
            else -> dj(seedId, fit.shuffled().take(size * POOL_FACTOR), if (mode == MixMode.HARMONIC) HARMONIC_KEY_WEIGHT else 1.0, before, size).take(size)
        }
        return result
    }

    /** A queue entry for a song. */
    fun entryOf(song: Song) = QueueEntry(
        id = song.id, title = song.title, artist = song.artist, album = song.album,
        coverArt = song.coverArt, suffix = song.suffix, durationMs = song.durationSeconds * 1000L,
        albumId = song.albumId, artistId = song.artistId, bitRate = song.bitRate, bpm = song.bpm,
        replayGain = song.replayGain
    )
}
