package org.nooklin.music.playback.beat

import android.util.Log
import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random
import org.nooklin.music.data.library.Song
import org.nooklin.music.playback.mix.MixFacts
import org.nooklin.music.playback.mix.MixFacts.Edge

/**
 * Puts songs in an order in which each song suits the one before it, as a DJ picks the next record: a tempo that can
 * be matched (half and double count too), a key that does not clash, a song of about the same loudness and, a little,
 * the same genre. What is known comes from the analysis ([BeatMixStore]) and from the tempo tag of the server. A song
 * of which nothing is known is neither liked nor disliked, so a library that was little analysed stays close to random.
 * The next song is one of the best few, not always the best, so that the order is not the same every time. Three guards
 * keep it from playing only the same music: the same artist waits [ARTIST_COOLDOWN] songs, a genre is not picked more than
 * [GENRE_RUN] times in a row while something else is left, and after [PLATEAU_RUN] songs of one tempo a song of another tempo
 * is preferred, so the order moves up and down in tempo as a set does instead of staying at one speed.
 */
object MixOrder {
    private const val CHOICES = 4

    // The same artist is not picked again within this many songs, unless nothing else is left
    private const val ARTIST_COOLDOWN = 3

    // 0.0.135: the same for the album (an album with several artists, a compilation, played through as an album)
    private const val ALBUM_COOLDOWN = 3

    // The same genre (when it is known) is not picked more than this many times in a row, unless nothing else is left
    private const val GENRE_RUN = 3

    // This many songs in a row at about one tempo count as a plateau; the next song of that tempo then costs PLATEAU_PENALTY more
    private const val PLATEAU_RUN = 4
    private const val PLATEAU_SAME_TEMPO = 0.3
    private const val PLATEAU_PENALTY = 1.0

    // What a missing tempo or key costs: more than a good match, less than a bad one
    private const val UNKNOWN_TEMPO = 0.6
    private const val UNKNOWN_KEY = 0.3
    private const val SAME_GENRE = 0.15

    // A tempo difference of this much counts as 1 (it is about what a song can be sped up or slowed down by)
    private const val TEMPO_UNIT = 0.1
    private const val MAX_TEMPO_COST = 3.0

    // A difference of this much in the loudness of the analysis counts as 1
    private const val LOUDNESS_UNIT = 1.5

    /** What the ordering needs to know about a song, so a queue can be ordered as well as a list of songs. */
    class Item(val id: String, val artist: String, val tagBpm: Int, val genre: String, val album: String = "")

    // 0.0.103: the order looks WINDOW songs ahead (the best few continuations of each candidate are priced), not one: a song that is
    // a good fit now but leaves only bad fits after it loses against one that keeps the next ones open
    private const val WINDOW = 3
    private const val BREADTH = 4

    // Edge cost: the end of A against the start of B in relative energy (down costs more than up, the same weights as the planner's
    // energyJumpCost), the room for a blend (the shorter of A's usable outro and B's usable intro, 16 beats and more is enough), the feel
    private const val EDGE_DOWN = 2.4  // 0.0.104: was 0.8; the log of 0.0.103 showed 9 of 13 changes as exits because loud ends met quiet starts
    private const val LOUD_END = 0.8
    private const val LOUD_END_SHARE = 0.6
    private const val LOUD_END_PENALTY = 1.0
    private const val EDGE_UP = 0.3
    private const val ROOM_BEATS = 16.0
    private const val ROOM_WEIGHT = 0.6
    private const val FEEL_TOLERANCE = 0.06
    private const val FEEL_WEIGHT = 3.0
    private const val FEEL_MAX = 0.6

    // 0.0.110: an arc over several songs. After ARC_PEAK_RUN songs that each got more energetic than the one before, a song that is less
    // energetic is welcome (release), after ARC_VALLEY_RUN calmer ones a more energetic one is (build up); a step smaller than ARC_STEP is flat.
    // The weight is small next to tempo, key and edge costs: it only decides between songs that fit about equally well.
    private const val ARC_WEIGHT = 0.5
    private const val ARC_PEAK_RUN = 3
    private const val ARC_VALLEY_RUN = 2
    private const val ARC_STEP = 0.05
    private const val ARC_HISTORY = 5

    private class Facts(val bpm: Double, val key: Int, val loudness: Double, val genre: String, val edge: Edge? = null, val energy: Double = -1.0)

    private fun factsOf(id: String, tagBpm: Int, genre: String): Facts {
        // The newer analysis first, the older one for songs it has not seen yet
        val analysed = MixFacts.bpm(id) ?: BeatMixStore.entries(id)?.firstOrNull()?.bpm
        val bpm = analysed ?: (if (tagBpm > 0) tagBpm.toDouble() else 0.0)
        val key = MixFacts.key(id).takeIf { it >= 0 } ?: BeatMixStore.songKey(id)
        return Facts(bpm, key, BeatMixStore.loudness(id) ?: -1.0, genre.trim().lowercase(), MixFacts.edge(id), MixFacts.energy(id) ?: -1.0)
    }

    private fun factsOf(item: Item): Facts = factsOf(item.id, item.tagBpm, item.genre)

    // 0.0.135: the album of a song is told by its name (the queue only knows the name, so a name is the one thing that is the same everywhere);
    // two albums with the same name count as one, which only means a little more spacing
    fun itemOf(song: Song) = Item(song.id, song.artist, song.bpm, song.genre, song.album.ifEmpty { song.albumId.orEmpty() })

    /** 0 for a tempo that is the same (or half or double), 1 for a difference of [TEMPO_UNIT]. */
    private fun tempoCost(a: Double, b: Double): Double {
        var best = Double.MAX_VALUE
        for (m in doubleArrayOf(0.5, 1.0, 2.0)) {
            val d = abs(ln(b * m / a))
            if (d < best) best = d
        }
        return minOf(MAX_TEMPO_COST, best / ln(1.0 + TEMPO_UNIT))
    }

    /**
     * 0.0.103: what the edges of the two songs say about the change from [a] to [b]: a loud end into a quiet start (that is where the
     * log of 0.0.102 showed the fade-outs), too little intro or outro to blend over (the planner then has an exit or an 8 beat blend left),
     * and a different feel (straight against shuffled). Zero when either song has no cue fields (an older analysis).
     */
    private fun edgeCost(a: Facts, b: Facts): Double {
        val ea = a.edge ?: return 0.0
        val eb = b.edge ?: return 0.0
        var c = EDGE_DOWN * maxOf(0.0, ea.energyEnd - eb.energyStart) + EDGE_UP * maxOf(0.0, eb.energyStart - ea.energyEnd)
        // A loud end only into a start of at least 60% of that strength (the planner has no good blend or exit for a bigger gap)
        if (ea.energyEnd > LOUD_END && eb.energyStart < LOUD_END_SHARE * ea.energyEnd) c += LOUD_END_PENALTY
        val room = minOf(ea.usableOutroBeats, eb.usableIntroBeats).toDouble()
        val trust = minOf(ea.confidence, eb.confidence).coerceIn(0.0, 1.0)
        if (room < ROOM_BEATS) c += ROOM_WEIGHT * (1.0 - room / ROOM_BEATS) * (0.5 + 0.5 * trust)
        c += minOf(FEEL_MAX, maxOf(0.0, FEEL_WEIGHT * (abs(ea.swing - eb.swing) - FEEL_TOLERANCE)))
        return c
    }

    /** The cost of a song of [next] energy after the songs of [history] (oldest first; -1 for unknown): see ARC_WEIGHT. */
    private fun arcCost(history: List<Double>, next: Double): Double {
        if (next < 0.0 || history.size < 2 || history.any { it < 0.0 }) return 0.0
        var ups = 0
        var downs = 0
        for (i in history.size - 1 downTo 1) {
            val d = history[i] - history[i - 1]
            if (d > ARC_STEP && downs == 0) ups++ else if (d < -ARC_STEP && ups == 0) downs++ else break
        }
        val d = next - history.last()
        val rises = d > ARC_STEP
        val falls = d < -ARC_STEP
        return when {
            ups >= ARC_PEAK_RUN -> if (rises) ARC_WEIGHT else if (falls) 0.0 else 0.5 * ARC_WEIGHT
            downs >= ARC_VALLEY_RUN -> if (falls) ARC_WEIGHT else if (rises) 0.0 else 0.5 * ARC_WEIGHT
            else -> 0.0
        }
    }

    private fun cost(a: Facts, b: Facts, keyWeight: Double = 1.0): Double {
        var c = if (a.bpm > 0.0 && b.bpm > 0.0) tempoCost(a.bpm, b.bpm) else UNKNOWN_TEMPO
        c += keyWeight * if (a.key >= 0 && b.key >= 0) KeyDetector.distance(a.key, b.key) / 6.0 else UNKNOWN_KEY
        if (a.loudness >= 0.0 && b.loudness >= 0.0) c += abs(a.loudness - b.loudness) / LOUDNESS_UNIT
        if (a.genre.isNotEmpty() && a.genre == b.genre) c -= SAME_GENRE
        c += edgeCost(a, b)
        return c
    }

    /** [songs] in an order in which each suits the one before it, starting with a random one. */
    fun shuffled(songs: List<Song>): List<Song> = order(songs, null, ::itemOf)

    /** [songs] in an order in which the first suits the song [seedId] and each next one suits the one before it. */
    fun after(seedId: String, songs: List<Song>, prior: List<Item> = emptyList(), maxPlaces: Int = Int.MAX_VALUE): List<Song> =
        fill(songs, List(songs.size) { false }, factsOf(seedId, 0, ""), ::itemOf, 1.0, prior, maxPlaces)

    /** Like [after], with the key counting [keyWeight] times as much (the Harmonic mode of the AutoMix menu). */
    fun after(seedId: String, songs: List<Song>, keyWeight: Double, prior: List<Item> = emptyList(), maxPlaces: Int = Int.MAX_VALUE): List<Song> =
        fill(songs, List(songs.size) { false }, factsOf(seedId, 0, ""), ::itemOf, keyWeight, prior, maxPlaces)

    /**
     * 0.0.135: [songs] in the same order, except that the same artist or album is not picked again within the last songs
     * ([prior] are the songs that come right before, oldest first). For lists that are not put in a DJ order (the Discovery
     * Station, AutoPlay without beat mixing, random Home mixes). Soft like the DJ order: when only such songs are left,
     * the album rule goes first, then the artist rule.
     */
    fun spaced(songs: List<Song>, prior: List<Item> = emptyList()): List<Song> {
        if (songs.size < 2) return songs
        val left = songs.toMutableList()
        val result = ArrayList<Song>(songs.size)
        val artists = ArrayList<String>()
        val albums = ArrayList<String>()
        for (p in prior.takeLast(maxOf(ARTIST_COOLDOWN, ALBUM_COOLDOWN))) {
            artists.add(p.artist.trim().lowercase())
            albums.add(p.album.trim().lowercase())
        }
        while (left.isNotEmpty()) {
            val ra = artists.takeLast(ARTIST_COOLDOWN).filter { it.isNotEmpty() }
            val rb = albums.takeLast(ALBUM_COOLDOWN).filter { it.isNotEmpty() }
            fun artist(s: Song) = s.artist.trim().lowercase()
            fun album(s: Song) = itemOf(s).album.trim().lowercase()
            val pick = left.firstOrNull { artist(it) !in ra && album(it) !in rb } ?: left.firstOrNull { artist(it) !in ra } ?: left.first()
            left.remove(pick)
            result.add(pick)
            artists.add(artist(pick))
            albums.add(album(pick))
        }
        return result
    }

    /**
     * The queue [items] that come after the song [seedId] playing now, in a DJ order. Where [fixed] is true the item stays in
     * its place (a song the user put there, or one that is not downloaded); the others are put in the free places, and
     * the song before a fixed one is chosen to suit it as well.
     */
    fun queueAround(seedId: String, items: List<Item>, fixed: List<Boolean>, prior: List<Item> = emptyList()): List<Item> =
        fill(items, fixed, factsOf(seedId, 0, ""), { it }, 1.0, prior)

    /** The cost of the cheapest way to carry on for [depth] more songs from [from], looking only at the best [BREADTH] at each step. */
    private fun <T> continuation(from: Facts, pool: List<T>, depth: Int, facts: (T) -> Facts, keyWeight: Double): Double {
        if (depth <= 0 || pool.isEmpty()) return 0.0
        val best = pool.map { it to cost(from, facts(it), keyWeight) }.sortedBy { it.second }.take(BREADTH)
        return best.minOf { (t, c) -> c + continuation(facts(t), pool.filter { it !== t }, depth - 1, facts, keyWeight) }
    }

    private fun <T> order(things: List<T>, seed: Facts?, toItem: (T) -> Item): List<T> =
        fill(things, List(things.size) { false }, seed, toItem)

    private fun <T> fill(
        things: List<T>, fixed: List<Boolean>, seed: Facts?, toItem: (T) -> Item, keyWeight: Double = 1.0, prior: List<Item> = emptyList(),
        // 0.0.144: stop after this many places; the choice for a place does not depend on the later places, so the first ones are the same
        maxPlaces: Int = Int.MAX_VALUE
    ): List<T> {
        if (things.size < 2 || fixed.size != things.size || things.indices.count { !fixed[it] } < 2) return things
        val cache = HashMap<String, Facts>()
        fun facts(t: T): Facts {
            val item = toItem(t)
            return cache.getOrPut(item.id) { factsOf(item) }
        }
        fun artist(t: T) = toItem(t).artist.trim().lowercase()
        fun genre(t: T) = toItem(t).genre.trim().lowercase()
        fun album(t: T) = toItem(t).album.trim().lowercase()
        // 0.0.135: the artists and albums of the last songs, with the song playing now (and the songs before this list) in front
        val recentArtists = ArrayList<String>()
        val recentAlbums = ArrayList<String>()
        for (p in prior.takeLast(ARTIST_COOLDOWN)) {
            recentArtists.add(p.artist.trim().lowercase())
            recentAlbums.add(p.album.trim().lowercase())
        }
        fun note(t: T) {
            recentArtists.add(artist(t))
            recentAlbums.add(album(t))
        }
        val left = things.filterIndexed { i, _ -> !fixed[i] }.toMutableList()
        val result = ArrayList<T>(things.size)
        var from: Facts? = seed
        var genreStops = 0
        var plateauStops = 0
        for (slot in things.indices) {
            if (result.size >= maxPlaces) break
            if (fixed[slot]) {
                result.add(things[slot])
                note(things[slot])
                from = facts(things[slot])
                continue
            }
            val origin = from
            val ahead = if (slot + 1 < things.size && fixed[slot + 1]) facts(things[slot + 1]) else null
            val recent = recentArtists.takeLast(ARTIST_COOLDOWN).filter { it.isNotEmpty() }
            val recentAlbum = recentAlbums.takeLast(ALBUM_COOLDOWN).filter { it.isNotEmpty() }
            // Not the same artist and not the same album within the last songs; if nothing is left, the album rule is dropped
            // first, then the artist rule
            var allowed = left.filter { artist(it) !in recent && album(it) !in recentAlbum }
                .ifEmpty { left.filter { artist(it) !in recent } }.ifEmpty { left }
            // The same genre too often in a row: leave it out while there is something else
            val lastGenres = result.takeLast(GENRE_RUN).map { genre(it) }
            if (lastGenres.size == GENRE_RUN && lastGenres[0].isNotEmpty() && lastGenres.all { it == lastGenres[0] }) {
                val others = allowed.filter { genre(it) != lastGenres[0] }
                if (others.isNotEmpty()) {
                    allowed = others
                    genreStops++
                }
            }
            val pick = if (origin == null) {
                allowed[Random.nextInt(allowed.size)]
            } else {
                // A plateau: the last songs were all about one tempo, so a song of another tempo is preferred
                val tail = result.takeLast(PLATEAU_RUN).map { facts(it).bpm }
                val plateau = origin.bpm > 0.0 && tail.size == PLATEAU_RUN && tail.all { it > 0.0 && tempoCost(origin.bpm, it) < PLATEAU_SAME_TEMPO }
                if (plateau) plateauStops++
                val arcHistory = (if (result.isEmpty()) listOf(origin.energy) else result.takeLast(ARC_HISTORY).map { facts(it).energy })
                // The best few candidates by the cost of the next change alone, then each priced with its best continuation
                fun first(t: T): Double {
                    val f = facts(t)
                    var c = cost(origin, f, keyWeight)
                    if (ahead != null) c += cost(f, ahead, keyWeight)
                    if (plateau && f.bpm > 0.0 && tempoCost(origin.bpm, f.bpm) < PLATEAU_SAME_TEMPO) c += PLATEAU_PENALTY
                    c += arcCost(arcHistory, f.energy)
                    return c
                }
                val ranked = allowed.sortedBy { first(it) }.take(BREADTH + CHOICES).sortedBy { t ->
                    first(t) + continuation(facts(t), left.filter { it !== t }, WINDOW - 1, ::facts, keyWeight)
                }
                ranked[Random.nextInt(minOf(CHOICES, ranked.size))]
            }
            left.remove(pick)
            result.add(pick)
            note(pick)
            from = facts(pick)
        }
        // How much was known: the order is only as good as that
        val known = cache.values
        return result
    }
}
