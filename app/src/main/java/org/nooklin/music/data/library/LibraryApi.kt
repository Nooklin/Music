package org.nooklin.music.data.library

import android.content.Context
import java.text.Collator
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.nooklin.music.data.server.Reply
import org.nooklin.music.data.server.ServerSession

/** Reads and writes the Navidrome library through the Subsonic API. */
class LibraryApi(context: Context) {
    private val session = ServerSession.get(context)

    // The server reports which articles to skip when sorting (for example "The"). Learned from getArtists.
    private var ignoredArticles: List<String> = DEFAULT_ARTICLES

    suspend fun artists(): Reply<List<Artist>> {
        val reply = session.call("getArtists")
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val container = reply.value.optJSONObject("artists") ?: return Reply.Ok(emptyList())
        container.optString("ignoredArticles").takeIf { it.isNotBlank() }?.let { text ->
            ignoredArticles = text.split(" ").filter { it.isNotBlank() }.map { it.lowercase(Locale.ROOT) }
        }
        val list = mutableListOf<Artist>()
        val indexes = container.optJSONArray("index")
        for (i in 0 until (indexes?.length() ?: 0)) {
            list += parseArtists(indexes!!.getJSONObject(i).optJSONArray("artist"))
        }
        return Reply.Ok(list.sortedWith(compareBy(COLLATOR) { it.sortName }))
    }

    suspend fun artist(id: String): Reply<ArtistDetail> {
        val reply = session.call("getArtist", listOf("id" to id))
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val json = reply.value.optJSONObject("artist") ?: return Reply.Fail("Artist not found.", false)
        val albums = parseAlbums(json.optJSONArray("album")).sortedWith(
            compareByDescending<Album> { it.year }.thenBy(COLLATOR) { it.sortName }
        )
        return Reply.Ok(ArtistDetail(parseArtist(json), albums))
    }

    /** The biography the server has for an artist, as plain text. Empty when it has none. */
    suspend fun artistBiography(id: String): Reply<String> {
        val reply = session.call("getArtistInfo2", listOf("id" to id))
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val raw = reply.value.optJSONObject("artistInfo2")?.optString("biography").orEmpty()
        val text = android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        return Reply.Ok(text.replace(Regex("\\s*Read more on Last\\.fm.*$", RegexOption.DOT_MATCHES_ALL), "").trim())
    }

    /** Songs the server finds similar to a song or an artist ([id]). Can be empty. */
    suspend fun similarSongs(id: String, count: Int): Reply<List<Song>> {
        val reply = session.call("getSimilarSongs2", listOf("id" to id, "count" to count.toString()))
        if (reply !is Reply.Ok) return reply as Reply.Fail
        return Reply.Ok(parseSongs(reply.value.optJSONObject("similarSongs2")?.optJSONArray("song")))
    }

    suspend fun randomSongs(count: Int, genre: String? = null): Reply<List<Song>> {
        val params = mutableListOf("size" to count.toString())
        if (genre != null) params += "genre" to genre
        val reply = session.call("getRandomSongs", params)
        if (reply !is Reply.Ok) return reply as Reply.Fail
        return Reply.Ok(parseSongs(reply.value.optJSONObject("randomSongs")?.optJSONArray("song")))
    }

    /** Tells the server which song plays now (it shows as playing, it does not count as a play). */
    suspend fun nowPlaying(id: String): Reply<Unit> =
        done(session.call("scrobble", listOf("id" to id, "submission" to "false")))

    /** Tells the server that a song was played. [timeMs] is when it started. */
    suspend fun scrobble(id: String, timeMs: Long): Reply<Unit> =
        done(session.call("scrobble", listOf("id" to id, "time" to timeMs.toString(), "submission" to "true")))

    suspend fun album(id: String): Reply<AlbumDetail> {
        val reply = session.call("getAlbum", listOf("id" to id))
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val json = reply.value.optJSONObject("album") ?: return Reply.Fail("Album not found.", false)
        val songs = parseSongs(json.optJSONArray("song")).sortedWith(compareBy({ it.disc }, { it.track }))
        return Reply.Ok(AlbumDetail(parseAlbum(json), songs))
    }

    suspend fun albums(kind: AlbumListKind): Reply<List<Album>> {
        // The server hands out at most 500 albums per request
        val all = mutableListOf<Album>()
        var offset = 0
        while (true) {
            val reply = session.call(
                "getAlbumList2",
                listOf("type" to kind.apiType, "size" to PAGE.toString(), "offset" to offset.toString())
            )
            if (reply !is Reply.Ok) return reply as Reply.Fail
            val page = parseAlbums(reply.value.optJSONObject("albumList2")?.optJSONArray("album"))
            all += page
            // A random list is a different draw each time, so one page is the whole list
            if (kind == AlbumListKind.RANDOM || page.size < PAGE) break
            offset += PAGE
        }
        return Reply.Ok(if (kind.sortLocally) all.sortedWith(compareBy(COLLATOR) { it.sortName }) else all)
    }

    /**
     * The songs played most ([byCount]) or most recently, from Navidrome's own song list. Songs never played are left
     * out. Navidrome keeps one last play date and a count per song, not a log of every play.
     */
    suspend fun playedSongs(byCount: Boolean): Reply<List<Song>> {
        val reply = session.nativeSongs(
            listOf(
                "_start" to "0",
                "_end" to "100",
                "_sort" to if (byCount) "playCount" else "playDate",
                "_order" to "DESC"
            )
        )
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val songs = (0 until reply.value.length()).map { reply.value.getJSONObject(it) }
            .filter { it.optInt("playCount") > 0 }
            .map { j ->
                val albumId = j.optString("albumId").ifEmpty { null }
                Song(
                    id = j.optString("id"),
                    title = j.optString("title"),
                    artist = j.optString("artist"),
                    album = j.optString("album"),
                    albumId = albumId,
                    artistId = j.optString("artistId").ifEmpty { null },
                    track = j.optInt("trackNumber"),
                    disc = j.optInt("discNumber", 1),
                    durationSeconds = j.optInt("duration"),
                    suffix = j.optString("suffix"),
                    bitRate = j.optInt("bitRate"),
                    coverArt = albumId?.let { "al-$it" },
                    starred = j.optBoolean("starred"),
                    userRating = j.optInt("rating"),
                    replayGain = ReplayGain(
                        trackGain = number(j, "rgTrackGain"),
                        albumGain = number(j, "rgAlbumGain"),
                        trackPeak = number(j, "rgTrackPeak"),
                        albumPeak = number(j, "rgAlbumPeak"),
                        fallbackGain = null
                    ),
                    playCount = j.optInt("playCount")
                )
            }
        return Reply.Ok(songs)
    }

    /**
     * The songs added to the library last, newest first, from Navidrome's own song list. When that list cannot be
     * sorted by the date added, the songs of the newest albums (Subsonic) are used.
     */
    suspend fun newestSongs(count: Int): Reply<List<Song>> {
        val reply = session.nativeSongs(
            listOf("_start" to "0", "_end" to count.toString(), "_sort" to "createdAt", "_order" to "DESC")
        )
        if (reply is Reply.Ok && reply.value.length() > 0) {
            return Reply.Ok((0 until reply.value.length()).map { nativeSong(reply.value.getJSONObject(it)) })
        }
        val newest = albums(AlbumListKind.NEWEST)
        if (newest !is Reply.Ok) return reply as? Reply.Fail ?: Reply.Fail("No songs.", false)
        val out = mutableListOf<Song>()
        for (item in newest.value.take(10)) {
            val r = album(item.id)
            if (r is Reply.Ok) out += r.value.songs
            if (out.size >= count) break
        }
        return Reply.Ok(out.take(count))
    }

    /** The artists the server finds similar to an artist (needs its Last.fm or Spotify setting). Can be empty. */
    suspend fun similarArtists(id: String): Reply<List<Artist>> {
        val reply = session.call("getArtistInfo2", listOf("id" to id, "count" to "6"))
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val array = reply.value.optJSONObject("artistInfo2")?.optJSONArray("similarArtist")
        return Reply.Ok(parseArtists(array).filter { it.id.isNotEmpty() })
    }

    private fun nativeSong(j: JSONObject): Song {
        val albumId = j.optString("albumId").ifEmpty { null }
        return Song(
            id = j.optString("id"),
            title = j.optString("title"),
            artist = j.optString("artist"),
            album = j.optString("album"),
            albumId = albumId,
            artistId = j.optString("artistId").ifEmpty { null },
            track = j.optInt("trackNumber"),
            disc = j.optInt("discNumber", 1),
            durationSeconds = j.optInt("duration"),
            suffix = j.optString("suffix"),
            bitRate = j.optInt("bitRate"),
            coverArt = albumId?.let { "al-$it" },
            starred = j.optBoolean("starred"),
            userRating = j.optInt("rating"),
            replayGain = ReplayGain(
                trackGain = number(j, "rgTrackGain"),
                albumGain = number(j, "rgAlbumGain"),
                trackPeak = number(j, "rgTrackPeak"),
                albumPeak = number(j, "rgAlbumPeak"),
                fallbackGain = null
            ),
            playCount = j.optInt("playCount"),
            genre = j.optString("genre"),
            plays = j.optInt("playCount"),
            bpm = j.optInt("bpm")
        )
    }

    suspend fun genres(): Reply<List<Genre>> {
        val reply = session.call("getGenres")
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val array = reply.value.optJSONObject("genres")?.optJSONArray("genre")
        val list = (0 until (array?.length() ?: 0)).map { array!!.getJSONObject(it) }.map { j ->
            Genre(
                name = j.optString("value").ifEmpty { j.optString("name") },
                songCount = j.optInt("songCount"),
                albumCount = j.optInt("albumCount")
            )
        }.filter { it.name.isNotEmpty() }
        return Reply.Ok(list.sortedWith(compareBy(COLLATOR) { it.name }))
    }

    suspend fun albumsOfGenre(genre: String): Reply<List<Album>> {
        val all = mutableListOf<Album>()
        var offset = 0
        while (true) {
            val reply = session.call(
                "getAlbumList2",
                listOf("type" to "byGenre", "genre" to genre, "size" to PAGE.toString(), "offset" to offset.toString())
            )
            if (reply !is Reply.Ok) return reply as Reply.Fail
            val page = parseAlbums(reply.value.optJSONObject("albumList2")?.optJSONArray("album"))
            all += page
            if (page.size < PAGE) break
            offset += PAGE
        }
        return Reply.Ok(all.sortedWith(compareBy(COLLATOR) { it.sortName }))
    }

    suspend fun favorites(): Reply<Favorites> {
        val reply = session.call("getStarred2")
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val json = reply.value.optJSONObject("starred2") ?: return Reply.Ok(Favorites(emptyList(), emptyList(), emptyList()))
        return Reply.Ok(
            Favorites(
                artists = parseArtists(json.optJSONArray("artist")).sortedWith(compareBy(COLLATOR) { it.sortName }),
                albums = parseAlbums(json.optJSONArray("album")).sortedWith(compareBy(COLLATOR) { it.sortName }),
                songs = parseSongs(json.optJSONArray("song"))
            )
        )
    }

    /**
     * The text of a song, or null when the server has none. Tries the newer call with timing first (OpenSubsonic
     * `getLyricsBySongId`), and falls back to the older `getLyrics` that finds a song by artist and title.
     */
    suspend fun lyrics(id: String, artist: String, title: String): Reply<Lyrics?> {
        val modern = session.call("getLyricsBySongId", listOf("id" to id))
        if (modern is Reply.Ok) {
            parseStructuredLyrics(modern.value)?.let { return Reply.Ok(it) }
        }
        val old = session.call("getLyrics", listOf("artist" to artist, "title" to title))
        if (old is Reply.Ok) {
            val text = old.value.optJSONObject("lyrics")?.optString("value").orEmpty().trim()
            return Reply.Ok(
                if (text.isEmpty()) null else Lyrics(false, text.lines().map { LyricLine(null, it.trimEnd()) })
            )
        }
        // The newer call answered without text and the older one failed: the song simply has no text
        return if (modern is Reply.Ok) Reply.Ok(null) else old as Reply.Fail
    }

    private fun parseStructuredLyrics(root: JSONObject): Lyrics? {
        val list = root.optJSONObject("lyricsList")?.optJSONArray("structuredLyrics") ?: return null
        val entries = (0 until list.length()).map { list.getJSONObject(it) }
        // Lines with timing are better than plain text
        val chosen = entries.firstOrNull { it.optBoolean("synced") } ?: entries.firstOrNull() ?: return null
        val lines = chosen.optJSONArray("line") ?: return null
        val synced = chosen.optBoolean("synced")
        val parsed = (0 until lines.length()).map { i ->
            val line = lines.getJSONObject(i)
            LyricLine(if (synced && line.has("start")) line.optLong("start") else null, line.optString("value"))
        }
        return if (parsed.all { it.text.isBlank() }) null else Lyrics(synced && parsed.all { it.startMs != null }, parsed)
    }

    suspend fun search(query: String): Reply<SearchResults> {
        val reply = session.call(
            "search3",
            listOf("query" to query, "artistCount" to "15", "albumCount" to "20", "songCount" to "40")
        )
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val json = reply.value.optJSONObject("searchResult3")
        return Reply.Ok(
            SearchResults(
                artists = parseArtists(json?.optJSONArray("artist")),
                albums = parseAlbums(json?.optJSONArray("album")),
                songs = parseSongs(json?.optJSONArray("song"))
            )
        )
    }

    /**
     * A page of songs. An empty [query] means all songs (search3 without a search term); the page size is [SONG_PAGE].
     * Some servers want two quotes for "everything", so that is tried once when the first answer is empty.
     */
    suspend fun songs(query: String, offset: Int): Reply<List<Song>> {
        val first = searchSongs(query, offset)
        if (first is Reply.Ok && first.value.isEmpty() && query.isBlank() && offset == 0) {
            val retry = searchSongs("\"\"", 0)
            if (retry is Reply.Ok && retry.value.isNotEmpty()) return retry
        }
        return first
    }

    private suspend fun searchSongs(query: String, offset: Int): Reply<List<Song>> {
        val reply = session.call(
            "search3",
            listOf(
                "query" to query,
                "artistCount" to "0",
                "albumCount" to "0",
                "songCount" to SONG_PAGE.toString(),
                "songOffset" to offset.toString()
            )
        )
        if (reply !is Reply.Ok) return reply as Reply.Fail
        return Reply.Ok(parseSongs(reply.value.optJSONObject("searchResult3")?.optJSONArray("song")))
    }

    suspend fun playlists(): Reply<List<Playlist>> {
        val reply = session.call("getPlaylists")
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val array = reply.value.optJSONObject("playlists")?.optJSONArray("playlist")
        val list = (0 until (array?.length() ?: 0)).map { parsePlaylist(array!!.getJSONObject(it)) }
        return Reply.Ok(list.sortedWith(compareBy(COLLATOR) { it.name }))
    }

    suspend fun playlist(id: String): Reply<PlaylistDetail> {
        val reply = session.call("getPlaylist", listOf("id" to id))
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val json = reply.value.optJSONObject("playlist") ?: return Reply.Fail("Playlist not found.", false)
        // Songs keep the order of the playlist
        return Reply.Ok(PlaylistDetail(parsePlaylist(json), parseSongs(json.optJSONArray("entry"))))
    }

    suspend fun createPlaylist(name: String, songIds: List<String>): Reply<Unit> =
        done(session.call("createPlaylist", listOf("name" to name) + songIds.map { "songId" to it }))

    /**
     * Makes the playlist with [name], or, when [id] is given, puts [songIds] in that playlist instead of what was in it.
     * The [comment] is set as well. Returns the id of the playlist.
     */
    suspend fun savePlaylist(id: String?, name: String, songIds: List<String>, comment: String): Reply<String> {
        val first = if (id == null) "name" to name else "playlistId" to id
        val made = session.call("createPlaylist", listOf(first) + songIds.map { "songId" to it })
        if (made !is Reply.Ok) return made as Reply.Fail
        val playlistId = made.value.optJSONObject("playlist")?.optString("id").orEmpty().ifEmpty { id.orEmpty() }
        if (playlistId.isEmpty()) return Reply.Fail("The playlist was not saved.", false)
        val set = session.call("updatePlaylist", listOf("playlistId" to playlistId, "comment" to comment))
        return if (set is Reply.Ok) Reply.Ok(playlistId) else set as Reply.Fail
    }

    suspend fun renamePlaylist(id: String, name: String): Reply<Unit> =
        done(session.call("updatePlaylist", listOf("playlistId" to id, "name" to name)))

    suspend fun addToPlaylist(id: String, songIds: List<String>): Reply<Unit> =
        done(session.call("updatePlaylist", listOf("playlistId" to id) + songIds.map { "songIdToAdd" to it }))

    /** [index] is the position of the song in the playlist, starting at 0. */
    suspend fun removeFromPlaylist(id: String, index: Int): Reply<Unit> =
        done(session.call("updatePlaylist", listOf("playlistId" to id, "songIndexToRemove" to index.toString())))

    suspend fun deletePlaylist(id: String): Reply<Unit> = done(session.call("deletePlaylist", listOf("id" to id)))

    private fun done(reply: Reply<JSONObject>): Reply<Unit> =
        if (reply is Reply.Ok) Reply.Ok(Unit) else reply as Reply.Fail

    private fun parsePlaylist(j: JSONObject) = Playlist(
        id = j.optString("id"),
        name = j.optString("name"),
        songCount = j.optInt("songCount"),
        coverArt = j.optString("coverArt").ifEmpty { null },
        durationSeconds = j.optInt("duration"),
        owner = j.optString("owner"),
        changed = j.optString("changed").ifEmpty { null },
        comment = j.optString("comment")
    )

    /** One song with the current favorite state and rating. */
    suspend fun song(id: String): Reply<Song> {
        val reply = session.call("getSong", listOf("id" to id))
        if (reply !is Reply.Ok) return reply as Reply.Fail
        val json = reply.value.optJSONObject("song") ?: return Reply.Fail("Song not found.", false)
        return Reply.Ok(parseSong(json))
    }

    /** [rating] 1 to 5, or 0 to remove the rating. */
    suspend fun setRating(id: String, rating: Int): Reply<Unit> {
        val reply = session.call("setRating", listOf("id" to id, "rating" to rating.toString()))
        return if (reply is Reply.Ok) Reply.Ok(Unit) else reply as Reply.Fail
    }

    suspend fun setStarred(kind: StarKind, id: String, starred: Boolean): Reply<Unit> {
        val reply = session.call(if (starred) "star" else "unstar", listOf(kind.param to id))
        return if (reply is Reply.Ok) Reply.Ok(Unit) else reply as Reply.Fail
    }

    private fun parseArtists(array: JSONArray?): List<Artist> =
        (0 until (array?.length() ?: 0)).map { parseArtist(array!!.getJSONObject(it)) }

    private fun parseAlbums(array: JSONArray?): List<Album> =
        (0 until (array?.length() ?: 0)).map { parseAlbum(array!!.getJSONObject(it)) }

    private fun parseSongs(array: JSONArray?): List<Song> =
        (0 until (array?.length() ?: 0)).map { parseSong(array!!.getJSONObject(it)) }

    private fun parseArtist(j: JSONObject): Artist {
        val name = j.optString("name")
        return Artist(
            id = j.optString("id"),
            name = name,
            sortName = sortKey(j.optString("sortName"), name),
            albumCount = j.optInt("albumCount"),
            coverArt = j.optString("coverArt").ifEmpty { null },
            starred = j.has("starred")
        )
    }

    private fun parseAlbum(j: JSONObject): Album {
        // Albums are called "name" in newer answers and "title" or "album" in older ones
        val name = j.optString("name").ifEmpty { j.optString("title") }.ifEmpty { j.optString("album") }
        return Album(
            id = j.optString("id"),
            name = name,
            sortName = sortKey(j.optString("sortName"), name),
            artist = j.optString("artist"),
            artistId = j.optString("artistId").ifEmpty { null },
            year = j.optInt("year"),
            songCount = j.optInt("songCount"),
            coverArt = j.optString("coverArt").ifEmpty { null },
            starred = j.has("starred")
        )
    }

    private fun parseSong(j: JSONObject) = Song(
        id = j.optString("id"),
        title = j.optString("title"),
        artist = j.optString("artist"),
        album = j.optString("album"),
        albumId = j.optString("albumId").ifEmpty { null },
        artistId = j.optString("artistId").ifEmpty { null },
        track = j.optInt("track"),
        disc = j.optInt("discNumber", 1),
        durationSeconds = j.optInt("duration"),
        suffix = j.optString("suffix"),
        bitRate = j.optInt("bitRate"),
        coverArt = j.optString("coverArt").ifEmpty { null },
        starred = j.has("starred"),
        userRating = j.optInt("userRating"),
        genre = j.optString("genre").ifEmpty { j.optJSONArray("genres")?.optJSONObject(0)?.optString("name").orEmpty() },
        plays = if (j.has("playCount")) j.optInt("playCount") else -1,
        bpm = j.optInt("bpm"),
        replayGain = j.optJSONObject("replayGain")?.let { rg ->
            ReplayGain(
                trackGain = number(rg, "trackGain"),
                albumGain = number(rg, "albumGain"),
                trackPeak = number(rg, "trackPeak"),
                albumPeak = number(rg, "albumPeak"),
                fallbackGain = number(rg, "fallbackGain")
            )
        }
    )

    private fun number(j: JSONObject, name: String): Double? = if (j.has(name) && !j.isNull(name)) j.optDouble(name) else null

    // The server's own sort name when it gives one, otherwise the name without a leading article
    private fun sortKey(serverSortName: String, name: String): String {
        if (serverSortName.isNotBlank()) return serverSortName
        val lower = name.lowercase(Locale.ROOT)
        for (article in ignoredArticles) {
            if (lower.startsWith("$article ")) return name.substring(article.length + 1).trim()
        }
        return name
    }

    companion object {
        private const val PAGE = 500
        private const val SONG_PAGE = 200
        private val DEFAULT_ARTICLES = listOf("the", "el", "la", "los", "las", "le", "les", "os", "as", "o", "a")
        private val COLLATOR: Collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    }
}
