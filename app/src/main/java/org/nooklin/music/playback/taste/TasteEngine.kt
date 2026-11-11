package org.nooklin.music.playback.taste

import android.util.Log
import kotlin.random.Random
import org.nooklin.music.data.library.LibraryApi
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.server.Reply

/**
 * Chooses songs from your own server using what the server knows (similar songs, hearts, ratings, play counts, genres)
 * and what [TasteStore] knows (played through, skipped). Nothing leaves the phone except the calls to your server.
 */
object TasteEngine {
    private const val TAG = "NooklinTaste"
    private const val PER_ARTIST = 2

    /** How much a song suits you: hearts and ratings from the server, played through and skipped from the store. */
    private fun affinity(song: Song, store: TasteStore): Double {
        var s = 0.0
        if (song.starred) s += 3.0
        if (song.userRating >= 4) s += 2.0 else if (song.userRating in 1..2) s -= 2.0
        val (done, skip) = store.artistStats(song.artistId)
        s += minOf(done, 5) * 0.4 - minOf(skip, 5) * 0.8
        store.entry(song.id)?.let { s += minOf(it.done, 3) * 0.5 - it.skip * 1.0 }
        return s
    }

    /** An artist that was skipped more than played through, at least twice: left out. */
    private fun blocked(song: Song, store: TasteStore): Boolean {
        val (done, skip) = store.artistStats(song.artistId)
        return skip >= 2 && skip > done
    }

    /** The [count] best of [songs], at most two of one artist. */
    private fun pick(songs: List<Pair<Song, Double>>, count: Int): List<Song> {
        val perArtist = HashMap<String, Int>()
        val out = ArrayList<Song>()
        for ((song, _) in songs.sortedByDescending { it.second }) {
            val key = song.artistId ?: song.artist
            if ((perArtist[key] ?: 0) >= PER_ARTIST) continue
            perArtist[key] = (perArtist[key] ?: 0) + 1
            out += song
            if (out.size == count) break
        }
        return out
    }

    /** Songs to follow the one that plays: similar to it and to the last one played through, as you like them. */
    suspend fun forAutoPlay(api: LibraryApi, store: TasteStore, seedId: String, inQueue: Set<String>, count: Int): List<Song> {
        val seeds = listOf(seedId) + store.recentDone(3).filter { it != seedId }.take(1)
        val pool = LinkedHashMap<String, Song>()
        for ((i, id) in seeds.withIndex()) {
            val r = api.similarSongs(id, if (i == 0) 30 else 20)
            if (r is Reply.Ok) r.value.forEach { pool.putIfAbsent(it.id, it) }
        }
        var songs = pool.values.filter { it.id !in inQueue && !blocked(it, store) }
        if (songs.isEmpty()) {
            val r = api.randomSongs(30)
            songs = if (r is Reply.Ok) r.value.filter { it.id !in inQueue && !blocked(it, store) } else emptyList()
        }
        val recent = store.recent(50)
        // What was played lately comes only when nothing else is left
        val fresh = songs.filter { it.id !in recent }.ifEmpty { songs }
        val chosen = pick(fresh.map { it to affinity(it, store) + Random.nextDouble() }, count)
        return chosen
    }

    /**
     * Songs of the Discovery Station: not played before, in the genres you play through most, and in one genre next to
     * them (the genre that the songs similar to your last favourites most often come in). A skip lowers a genre and an
     * artist, playing through and hearts raise them, so the station moves towards what you keep.
     */
    suspend fun forDiscovery(api: LibraryApi, store: TasteStore, inQueue: Set<String>, count: Int): List<Song> {
        val weights = HashMap(store.genreScores())
        val favourites = api.favorites()
        if (favourites is Reply.Ok) {
            favourites.value.songs.forEach { if (it.genre.isNotEmpty()) weights[it.genre] = (weights[it.genre] ?: 0.0) + 2.0 }
        }
        var core = weights.filter { it.value > 0.0 }.entries.sortedByDescending { it.value }.take(3).map { it.key }
        if (core.isEmpty()) {
            // Nothing is known yet: three genres of the server, the larger ones more often
            val g = api.genres()
            if (g is Reply.Ok) core = g.value.filter { it.songCount > 0 }.shuffled().take(3).map { it.name }
        }
        // The genre next to the core: seen most among the songs similar to the last ones played through
        var explore: String? = null
        val seen = HashMap<String, Int>()
        for (seed in store.recentDone(3)) {
            val r = api.similarSongs(seed, 20)
            if (r is Reply.Ok) r.value.forEach { if (it.genre.isNotEmpty() && it.genre !in core && (weights[it.genre] ?: 0.0) > -1.0) seen[it.genre] = (seen[it.genre] ?: 0) + 1 }
        }
        explore = seen.entries.maxByOrNull { it.value }?.key
        val genres = core + listOfNotNull(explore)
        val pool = LinkedHashMap<String, Pair<Song, Double>>()
        for (g in genres) {
            val r = api.randomSongs(60, g)
            if (r !is Reply.Ok) continue
            val bonus = if (g == explore) 0.5 else 0.0
            for (song in r.value) {
                if (song.id in inQueue || blocked(song, store)) continue
                // Not played before: the server says so when it sends play counts, else the store
                val unplayed = if (song.plays >= 0) song.plays == 0 else store.entry(song.id) == null
                if (!unplayed) continue
                pool.putIfAbsent(song.id, song to affinity(song, store) + bonus + Random.nextDouble())
            }
        }
        var chosen = pick(pool.values.toList(), count)
        if (chosen.isEmpty()) {
            val r = api.randomSongs(60)
            if (r is Reply.Ok) {
                chosen = pick(
                    r.value.filter { it.id !in inQueue && !blocked(it, store) && (if (it.plays >= 0) it.plays == 0 else store.entry(it.id) == null) }
                        .map { it to Random.nextDouble() },
                    count
                )
            }
        }
        return chosen
    }
}
