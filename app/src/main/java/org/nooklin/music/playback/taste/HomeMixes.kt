package org.nooklin.music.playback.taste

import android.content.Context
import android.util.Log
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject
import org.nooklin.music.data.library.LibraryApi
import org.nooklin.music.data.library.Playlist
import org.nooklin.music.data.library.ReplayGain
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.server.Reply
import org.nooklin.music.playback.beat.BeatMixStore

/** The cards on Home. Each one is rebuilt once a week, on its own day. */
enum class MixId(val title: String, val day: DayOfWeek) {
    FAVOURITES("Favourite songs", DayOfWeek.MONDAY),
    FEATURING("Featuring Artist", DayOfWeek.TUESDAY),
    NEW_RELEASE("New Release", DayOfWeek.WEDNESDAY),
    HEAVY("Heavy Rotation", DayOfWeek.THURSDAY),
    ESSENTIALS("Your Essentials", DayOfWeek.SUNDAY),
    GET_UP("Get Up!", DayOfWeek.SATURDAY),
    CHILL("Chill", DayOfWeek.SUNDAY),
    NEW_MUSIC("New Music", DayOfWeek.FRIDAY)
}

// The comment of a playlist made here: "nooklin-mix:HEAVY", and after a "|" a line for under the title on Home
private const val MARKER = "nooklin-mix:"

/** The mix a server playlist was made from, or null for a playlist of your own. */
fun mixOf(playlist: Playlist): MixId? {
    if (!playlist.comment.startsWith(MARKER)) return null
    val name = playlist.comment.removePrefix(MARKER).substringBefore('|')
    return MixId.entries.find { it.name == name }
}

/** A finished mix: the songs, when it was built and a line to show under the title (the artist for Featuring Artist). */
class Mix(val songs: List<Song>, val builtAt: Long, val subtitle: String)

/**
 * Builds the mixes from your server (hearts, ratings, play counts, genres, similar artists) and from [TasteStore]
 * (what you played this week). They are kept in a file, so the cards do not change until their day comes round.
 */
class HomeMixes private constructor(private val context: Context) {
    private val file = File(context.filesDir, "mixes.json")
    private val mixes = HashMap<MixId, Mix>()
    // Per mix: the id of its playlist on the server and the build time of the songs that were put in it
    private val published = HashMap<MixId, Pair<String, Long>>()

    init {
        try {
            if (file.exists()) {
                val root = JSONObject(file.readText())
                for (id in MixId.entries) {
                    val o = root.optJSONObject(id.name) ?: continue
                    val array = o.getJSONArray("songs")
                    mixes[id] = Mix((0 until array.length()).map { songOf(array.getJSONObject(it)) }, o.optLong("at"), o.optString("sub"))
                    root.optJSONObject("published")?.optJSONObject(id.name)?.let { published[id] = it.optString("id") to it.optLong("at") }
                }
            }
        } catch (e: Exception) {
            mixes.clear()
            published.clear()
        }
    }

    @Synchronized
    fun cached(id: MixId): Mix? = mixes[id]

    /** True when the day of this mix has come round since it was built. */
    @Synchronized
    fun due(id: MixId, now: Long = System.currentTimeMillis()): Boolean {
        val built = mixes[id]?.builtAt ?: return true
        val zone = ZoneId.systemDefault()
        var day = LocalDate.now(zone)
        while (day.dayOfWeek != id.day) day = day.minusDays(1)
        return built < day.atStartOfDay(zone).toInstant().toEpochMilli() && now >= day.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Builds the mix when it is due (or [force]), keeps it and returns it; the old one when the server cannot be reached. */
    suspend fun ensure(id: MixId, api: LibraryApi, force: Boolean = false): Mix? {
        if (!force && !due(id)) return cached(id)
        val made = try {
            build(id, api)
        } catch (e: Exception) {
            null
        }
        if (made == null || made.songs.isEmpty()) return cached(id)
        synchronized(this) {
            mixes[id] = made
            save()
        }
        return made
    }

    /**
     * Puts the mixes on the server as playlists, so they stand among your own. A mix is found again by the comment
     * of its playlist, and written again only when it was built after the last time. Returns the playlists as they
     * are now, or null when the server cannot be reached.
     */
    suspend fun publish(api: LibraryApi): List<Playlist>? {
        var playlists = (api.playlists() as? Reply.Ok)?.value ?: return null
        var changed = false
        for (id in MixId.entries) {
            val mix = cached(id)?.takeIf { it.songs.isNotEmpty() } ?: continue
            val known = synchronized(this) { published[id] }
            val found = playlists.firstOrNull { mixOf(it) == id } ?: playlists.firstOrNull { it.id == known?.first }
            if (found != null && (known?.second ?: 0L) >= mix.builtAt) continue
            val detail = when (id) {
                MixId.FEATURING -> mix.subtitle
                MixId.NEW_RELEASE -> mix.songs.first().let { it.title + " - " + it.artist }
                else -> ""
            }
            val saved = api.savePlaylist(found?.id, id.title, mix.songs.map { it.id }, MARKER + id.name + if (detail.isEmpty()) "" else "|$detail")
            if (saved is Reply.Ok) {
                synchronized(this) {
                    published[id] = saved.value to mix.builtAt
                    save()
                }
                changed = true
            } else {
            }
        }
        if (changed) playlists = (api.playlists() as? Reply.Ok)?.value ?: playlists
        return playlists
    }

    private suspend fun build(id: MixId, api: LibraryApi): Mix? {
        val store = TasteStore.get(context)
        val now = System.currentTimeMillis()
        return when (id) {
            MixId.FAVOURITES -> {
                val r = api.favorites()
                val songs = if (r is Reply.Ok) r.value.songs.shuffled().take(25) else return null
                Mix(org.nooklin.music.playback.beat.MixOrder.spaced(songs), now, "")
            }
            MixId.FEATURING -> featuring(api, store, now)
            MixId.NEW_RELEASE -> {
                val r = api.newestSongs(10)
                val songs = if (r is Reply.Ok) r.value.take(10) else return null
                if (songs.isEmpty()) return null
                val one = songs.random()
                Mix(listOf(one), now, one.title)
            }
            MixId.HEAVY -> {
                val r = api.playedSongs(true)
                val songs = if (r is Reply.Ok) r.value.take(30) else return null
                Mix(songs, now, "")
            }
            MixId.ESSENTIALS -> {
                val stars = api.favorites()
                val played = api.playedSongs(true)
                val pool = LinkedHashMap<String, Song>()
                if (stars is Reply.Ok) stars.value.songs.forEach { pool[it.id] = it }
                if (played is Reply.Ok) played.value.filter { it.userRating >= 4 }.forEach { pool.putIfAbsent(it.id, it) }
                if (pool.isEmpty()) return null
                val ranked = pool.values.sortedByDescending {
                    (if (it.starred) 2.0 else 0.0) + it.userRating + minOf(it.playCount, 50) / 25.0 + Random.nextDouble()
                }
                Mix(org.nooklin.music.playback.beat.MixOrder.spaced(ranked.take(40)), now, "")
            }
            MixId.GET_UP -> energy(api, now, up = true)
            MixId.CHILL -> energy(api, now, up = false)
            MixId.NEW_MUSIC -> {
                val r = api.newestSongs(100)
                val all = if (r is Reply.Ok) r.value else return null
                val unplayed = all.filter { it.plays <= 0 && store.entry(it.id) == null }
                Mix((unplayed.ifEmpty { all }).take(30), now, "")
            }
        }
    }

    /**
     * The artist you played through most in the last seven days (without more skips than plays) with some songs of
     * theirs and of artists the server finds similar. Without a week of listening: an artist of your hearts.
     */
    private suspend fun featuring(api: LibraryApi, store: TasteStore, now: Long): Mix? {
        val week = store.artistsSince(now - 7L * 24 * 3600 * 1000)
        var artistId = week.entries.filter { it.value.first > it.value.second }.maxByOrNull { it.value.first - it.value.second }?.key
        if (artistId == null) {
            val stars = api.favorites()
            if (stars is Reply.Ok) artistId = stars.value.songs.mapNotNull { it.artistId }.randomOrNull()
        }
        if (artistId == null) {
            val all = api.artists()
            if (all is Reply.Ok) artistId = all.value.randomOrNull()?.id
        }
        if (artistId == null) return null
        val main = api.artist(artistId)
        if (main !is Reply.Ok) return null
        val songs = ArrayList<Song>()
        songs += songsOf(api, main.value.albums, 10)
        val similar = api.similarArtists(artistId)
        val names = ArrayList<String>()
        if (similar is Reply.Ok) {
            for (artist in similar.value.shuffled().take(3)) {
                val detail = api.artist(artist.id)
                if (detail is Reply.Ok) {
                    val more = songsOf(api, detail.value.albums, 4)
                    if (more.isNotEmpty()) {
                        songs += more
                        names += artist.name
                    }
                }
            }
        }
        if (songs.isEmpty()) return null
        val sub = main.value.artist.name + if (names.isNotEmpty()) " & " + names.joinToString() else ""
        // The artist first, the similar ones mixed in
        val head = songs.take(3)
        return Mix(head + songs.drop(3).shuffled(), now, sub)
    }

    /** Up to [count] random songs from some random albums. */
    private suspend fun songsOf(api: LibraryApi, albums: List<org.nooklin.music.data.library.Album>, count: Int): List<Song> {
        val out = ArrayList<Song>()
        for (album in albums.shuffled().take(3)) {
            val r = api.album(album.id)
            if (r is Reply.Ok) out += r.value.songs
        }
        return out.shuffled().take(count)
    }

    /**
     * Fast and loud songs ([up]) or calm and slow ones. First from the genres of the server that fit; songs that were
     * analysed (tempo and loudness) are sorted on those, so the mix gets better as more songs are analysed.
     */
    private suspend fun energy(api: LibraryApi, now: Long, up: Boolean): Mix? {
        BeatMixStore.preload(context)
        val genres = api.genres()
        if (genres !is Reply.Ok) return null
        val keys = if (up) UP_GENRES else CHILL_GENRES
        val fitting = genres.value.filter { g -> keys.any { g.name.lowercase().contains(it) } && g.songCount > 0 }
        val pool = LinkedHashMap<String, Song>()
        for (g in fitting.shuffled().take(5)) {
            val r = api.randomSongs(40, g.name)
            if (r is Reply.Ok) r.value.forEach { pool.putIfAbsent(it.id, it) }
        }
        if (pool.isEmpty()) return null
        val scored = pool.values.map { song ->
            val loud = BeatMixStore.loudness(song.id)
            val bpm = BeatMixStore.entries(song.id)?.firstOrNull()?.bpm
            // An analysed song counts by its loudness and tempo; one that was not stays in the middle
            val analysed = if (loud != null) (loud - 3.2) + (if (bpm != null) (bpm - 120.0) / 30.0 else 0.0) else 0.0
            song to (if (up) analysed else -analysed) + Random.nextDouble()
        }.sortedByDescending { it.second }
        val known = scored.count { BeatMixStore.loudness(it.first.id) != null }
        // A song that was analysed as the wrong kind (a calm one for Get Up!) is left out
        val kept = scored.filter { (song, score) -> BeatMixStore.loudness(song.id) == null || score > -1.0 }
        return Mix(kept.take(30).map { it.first }, now, "$known analysed")
    }

    private fun save() {
        try {
            val root = JSONObject()
            for ((id, mix) in mixes) {
                root.put(id.name, JSONObject().put("at", mix.builtAt).put("sub", mix.subtitle).put("songs", JSONArray().also { a -> mix.songs.forEach { a.put(jsonOf(it)) } }))
            }
            root.put("published", JSONObject().also { o ->
                for ((id, p) in published) o.put(id.name, JSONObject().put("id", p.first).put("at", p.second))
            })
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
        }
    }

    private fun jsonOf(s: Song): JSONObject {
        val o = JSONObject()
            .put("id", s.id).put("t", s.title).put("ar", s.artist).put("al", s.album)
            .put("alid", s.albumId ?: "").put("arid", s.artistId ?: "").put("tr", s.track).put("di", s.disc)
            .put("du", s.durationSeconds).put("sf", s.suffix).put("br", s.bitRate).put("co", s.coverArt ?: "")
            .put("st", s.starred).put("ra", s.userRating).put("g", s.genre).put("pc", s.playCount)
        s.replayGain?.let { g ->
            g.trackGain?.let { o.put("rt", it) }
            g.albumGain?.let { o.put("ra2", it) }
            g.trackPeak?.let { o.put("pt", it) }
            g.albumPeak?.let { o.put("pa", it) }
            g.fallbackGain?.let { o.put("rf", it) }
        }
        return o
    }

    private fun songOf(o: JSONObject): Song {
        val gain = if (o.has("rt") || o.has("ra2") || o.has("rf")) {
            ReplayGain(
                if (o.has("rt")) o.getDouble("rt") else null,
                if (o.has("ra2")) o.getDouble("ra2") else null,
                if (o.has("pt")) o.getDouble("pt") else null,
                if (o.has("pa")) o.getDouble("pa") else null,
                if (o.has("rf")) o.getDouble("rf") else null
            )
        } else null
        return Song(
            id = o.getString("id"), title = o.optString("t"), artist = o.optString("ar"), album = o.optString("al"),
            albumId = o.optString("alid").ifEmpty { null }, artistId = o.optString("arid").ifEmpty { null },
            track = o.optInt("tr"), disc = o.optInt("di", 1), durationSeconds = o.optInt("du"), suffix = o.optString("sf"),
            bitRate = o.optInt("br"), coverArt = o.optString("co").ifEmpty { null }, starred = o.optBoolean("st"),
            userRating = o.optInt("ra"), replayGain = gain, playCount = o.optInt("pc"), genre = o.optString("g")
        )
    }

    companion object {
        private const val TAG = "NooklinMixes"
        private val UP_GENRES = listOf(
            "dance", "electro", "house", "techno", "trance", "edm", "drum", "bass", "hip", "rap", "rock", "metal", "punk",
            "funk", "disco", "pop", "workout", "hardcore"
        )
        private val CHILL_GENRES = listOf(
            "ambient", "chill", "lounge", "downtempo", "acoustic", "jazz", "classical", "folk", "new age", "soul",
            "easy", "singer", "instrumental", "piano", "blues", "relax", "sleep"
        )

        @Volatile
        private var instance: HomeMixes? = null

        fun get(context: Context): HomeMixes =
            instance ?: synchronized(this) {
                instance ?: HomeMixes(context.applicationContext).also { instance = it }
            }
    }
}
