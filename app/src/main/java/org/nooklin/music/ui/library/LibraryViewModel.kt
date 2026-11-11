package org.nooklin.music.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.data.library.Album
import org.nooklin.music.data.library.Genre
import org.nooklin.music.data.library.AlbumDetail
import org.nooklin.music.data.library.AlbumListKind
import org.nooklin.music.data.library.Artist
import org.nooklin.music.data.library.ArtistDetail
import org.nooklin.music.data.library.Favorites
import org.nooklin.music.data.library.LibraryApi
import org.nooklin.music.playback.taste.HomeMixes
import org.nooklin.music.playback.taste.MixId
import org.nooklin.music.playback.taste.TasteEngine
import org.nooklin.music.playback.taste.TasteStore
import org.nooklin.music.data.library.Lyrics
import org.nooklin.music.data.library.Playlist
import org.nooklin.music.data.library.PlaylistDetail
import org.nooklin.music.data.library.RecentSearches
import org.nooklin.music.data.library.SearchResults
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.library.StarKind
import org.nooklin.music.data.server.Reply

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/** The song overview: all songs, or the songs that match [query], loaded page by page. */
data class SongsState(
    val query: String = "",
    val items: List<Song> = emptyList(),
    val loading: Boolean = false,
    val end: Boolean = false,
    val error: String? = null,
    val started: Boolean = false
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val api = LibraryApi(application)

    private val _artists = MutableStateFlow<Load<List<Artist>>>(Load.Loading)
    val artists: StateFlow<Load<List<Artist>>> = _artists.asStateFlow()

    private val _albumLists = MutableStateFlow<Map<AlbumListKind, Load<List<Album>>>>(emptyMap())
    val albumLists: StateFlow<Map<AlbumListKind, Load<List<Album>>>> = _albumLists.asStateFlow()

    private val _artistDetail = MutableStateFlow<Pair<String, Load<ArtistDetail>>>("" to Load.Loading)
    val artistDetail: StateFlow<Pair<String, Load<ArtistDetail>>> = _artistDetail.asStateFlow()

    private val _albumDetail = MutableStateFlow<Pair<String, Load<AlbumDetail>>>("" to Load.Loading)
    val albumDetail: StateFlow<Pair<String, Load<AlbumDetail>>> = _albumDetail.asStateFlow()

    private val _favorites = MutableStateFlow<Load<Favorites>>(Load.Loading)
    val favorites: StateFlow<Load<Favorites>> = _favorites.asStateFlow()

    // Ids of everything the server says is a favorite, adjusted at once when the user taps a heart
    private val _starred = MutableStateFlow<Set<String>>(emptySet())
    val starred: StateFlow<Set<String>> = _starred.asStateFlow()

    // Rating (1 to 5) per song id, only for songs that were looked up
    private val _ratings = MutableStateFlow<Map<String, Int>>(emptyMap())
    val ratings: StateFlow<Map<String, Int>> = _ratings.asStateFlow()

    // The text of the song in the player, with the id it belongs to
    private val _lyrics = MutableStateFlow<Pair<String, Load<Lyrics?>>>("" to Load.Loading)
    val lyrics: StateFlow<Pair<String, Load<Lyrics?>>> = _lyrics.asStateFlow()

    private val recentStore = RecentSearches(application)
    val recent: StateFlow<List<String>> = recentStore.terms
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // The results of a search, with the search term they belong to
    private val _search = MutableStateFlow<Pair<String, Load<SearchResults>>>("" to Load.Loading)
    val searchState: StateFlow<Pair<String, Load<SearchResults>>> = _search.asStateFlow()

    private val _songs = MutableStateFlow(SongsState())
    val songs: StateFlow<SongsState> = _songs.asStateFlow()

    private val _playlists = MutableStateFlow<Load<List<Playlist>>>(Load.Loading)
    val playlists: StateFlow<Load<List<Playlist>>> = _playlists.asStateFlow()

    private val _playlistDetail = MutableStateFlow<Pair<String, Load<PlaylistDetail>>>("" to Load.Loading)
    val playlistDetail: StateFlow<Pair<String, Load<PlaylistDetail>>> = _playlistDetail.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Shows a short message in the snackbar of the screen that is open. */
    fun say(text: String) {
        _notice.value = text
    }

    fun noticeShown() {
        _notice.value = null
    }

    /** [force] loads again even when there is data already (the refresh button). */
    fun loadArtists(force: Boolean = false) {
        if (!force && _artists.value is Load.Ready) return
        _artists.value = Load.Loading
        viewModelScope.launch {
            _artists.value = when (val r = api.artists()) {
                is Reply.Ok -> {
                    syncStars(r.value.map { it.id to it.starred })
                    Load.Ready(r.value)
                }
                is Reply.Fail -> Load.Failed(r.message)
            }
        }
    }

    private val _played = MutableStateFlow<Map<Boolean, Load<List<Song>>>>(emptyMap())
    val played: StateFlow<Map<Boolean, Load<List<Song>>>> = _played.asStateFlow()

    /** [byCount] true: most played songs, false: most recently played songs. */
    fun loadPlayed(byCount: Boolean, force: Boolean = false) {
        if (!force && _played.value[byCount] is Load.Ready) return
        _played.update { it + (byCount to Load.Loading) }
        viewModelScope.launch {
            val result: Load<List<Song>> = when (val r = api.playedSongs(byCount)) {
                is Reply.Ok -> {
                    syncStars(r.value.map { it.id to it.starred })
                    Load.Ready(r.value)
                }
                is Reply.Fail -> Load.Failed(r.message)
            }
            _played.update { it + (byCount to result) }
        }
    }

    /**
     * Collects the songs of the first [count] of [albums], in that order, and hands them over when they are all in.
     * Silent when an album cannot be read; a notice says so when nothing could be loaded.
     */
    fun songsOfAlbums(albums: List<Album>, count: Int = 10, onReady: (List<Song>) -> Unit) {
        if (_gathering.value) return
        _gathering.value = true
        viewModelScope.launch {
            val songs = mutableListOf<Song>()
            try {
                for (album in albums.take(count)) {
                    val reply = api.album(album.id)
                    if (reply is Reply.Ok) songs += reply.value.songs
                }
            } finally {
                _gathering.value = false
            }
            if (songs.isEmpty()) _notice.value = "Could not load the songs of these albums." else onReady(songs)
        }
    }

    /** True while the songs for a Play or Shuffle button are being collected. */
    private val _gathering = MutableStateFlow(false)
    val gathering: StateFlow<Boolean> = _gathering.asStateFlow()

    /**
     * Collects the songs of the albums of [artists], artist by artist, until [maxAlbums] albums are in, and hands them
     * over. Silent when something cannot be read; a notice says so when nothing could be loaded.
     */
    fun songsOfArtists(artists: List<Artist>, maxAlbums: Int, onReady: (List<Song>) -> Unit) {
        if (_gathering.value) return
        _gathering.value = true
        viewModelScope.launch {
            val songs = mutableListOf<Song>()
            var albums = 0
            try {
                for (artist in artists) {
                    if (albums >= maxAlbums) break
                    val detail = api.artist(artist.id)
                    if (detail !is Reply.Ok) continue
                    for (album in detail.value.albums) {
                        if (albums >= maxAlbums) break
                        val reply = api.album(album.id)
                        if (reply is Reply.Ok) {
                            songs += reply.value.songs
                            albums++
                        }
                    }
                }
            } finally {
                _gathering.value = false
            }
            if (songs.isEmpty()) _notice.value = "Could not load the songs of these artists." else onReady(songs)
        }
    }

    /**
     * Asks the server for [count] random songs (of [genre] when given) and hands them over. A notice says so when
     * nothing could be loaded.
     */
    fun randomSongs(count: Int, genre: String? = null, onReady: (List<Song>) -> Unit) {
        if (_gathering.value) return
        _gathering.value = true
        viewModelScope.launch {
            val reply = try {
                api.randomSongs(count, genre)
            } finally {
                _gathering.value = false
            }
            val songs = if (reply is Reply.Ok) reply.value else emptyList()
            if (songs.isEmpty()) _notice.value = "Could not load random songs." else onReady(songs)
        }
    }

    /**
     * Collects the songs of the albums of [genres], genre by genre, until [maxAlbums] albums are in, and hands them over.
     * Silent when something cannot be read; a notice says so when nothing could be loaded.
     */
    fun songsOfGenres(genres: List<Genre>, maxAlbums: Int, onReady: (List<Song>) -> Unit) {
        if (_gathering.value) return
        _gathering.value = true
        viewModelScope.launch {
            val songs = mutableListOf<Song>()
            var albums = 0
            try {
                for (genre in genres) {
                    if (albums >= maxAlbums) break
                    val list = api.albumsOfGenre(genre.name)
                    if (list !is Reply.Ok) continue
                    for (album in list.value) {
                        if (albums >= maxAlbums) break
                        val reply = api.album(album.id)
                        if (reply is Reply.Ok) {
                            songs += reply.value.songs
                            albums++
                        }
                    }
                }
            } finally {
                _gathering.value = false
            }
            if (songs.isEmpty()) _notice.value = "Could not load the songs of these genres." else onReady(songs)
        }
    }

    private val _genres = MutableStateFlow<Load<List<Genre>>>(Load.Loading)
    val genres: StateFlow<Load<List<Genre>>> = _genres.asStateFlow()

    fun loadGenres(force: Boolean = false) {
        if (!force && _genres.value is Load.Ready) return
        _genres.value = Load.Loading
        viewModelScope.launch {
            _genres.value = when (val r = api.genres()) {
                is Reply.Ok -> Load.Ready(r.value)
                is Reply.Fail -> Load.Failed(r.message)
            }
        }
    }

    private val _genreAlbums = MutableStateFlow<Map<String, Load<List<Album>>>>(emptyMap())
    val genreAlbums: StateFlow<Map<String, Load<List<Album>>>> = _genreAlbums.asStateFlow()

    fun loadGenreAlbums(genre: String, force: Boolean = false) {
        if (!force && _genreAlbums.value[genre] is Load.Ready) return
        _genreAlbums.update { it + (genre to Load.Loading) }
        viewModelScope.launch {
            val result: Load<List<Album>> = when (val r = api.albumsOfGenre(genre)) {
                is Reply.Ok -> {
                    syncStars(r.value.map { it.id to it.starred })
                    Load.Ready(r.value)
                }
                is Reply.Fail -> Load.Failed(r.message)
            }
            _genreAlbums.update { it + (genre to result) }
        }
    }

    fun loadAlbums(kind: AlbumListKind, force: Boolean = false) {
        if (!force && _albumLists.value[kind] is Load.Ready) return
        _albumLists.update { it + (kind to Load.Loading) }
        viewModelScope.launch {
            val result: Load<List<Album>> = when (val r = api.albums(kind)) {
                is Reply.Ok -> {
                    syncStars(r.value.map { it.id to it.starred })
                    Load.Ready(r.value)
                }
                is Reply.Fail -> Load.Failed(r.message)
            }
            _albumLists.update { it + (kind to result) }
        }
    }

    fun loadArtist(id: String, force: Boolean = false) {
        val current = _artistDetail.value
        if (!force && current.first == id && current.second is Load.Ready) return
        _artistDetail.value = id to Load.Loading
        viewModelScope.launch {
            val result: Load<ArtistDetail> = when (val r = api.artist(id)) {
                is Reply.Ok -> {
                    syncStars(r.value.albums.map { it.id to it.starred } + (r.value.artist.id to r.value.artist.starred))
                    Load.Ready(r.value)
                }
                is Reply.Fail -> Load.Failed(r.message)
            }
            _artistDetail.value = id to result
        }
    }

    /**
     * Radio: songs similar to a song or an artist ([id]). With a [seed] song, that song comes first.
     * Says so when the server has nothing similar.
     */
    fun startRadio(id: String, seed: Song?, onReady: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val r = api.similarSongs(id, 50)
            val similar = if (r is Reply.Ok) r.value.filter { it.id != seed?.id } else emptyList()
            val list = listOfNotNull(seed) + similar
            if (similar.isEmpty()) {
                _notice.value = "The server found no similar songs."
            } else {
                onReady(list)
            }
        }
    }

    private var mixesLoading = false

    /** Builds the mixes whose day has come round and puts them on the server as playlists. */
    fun loadMixes() {
        if (mixesLoading) return
        mixesLoading = true
        viewModelScope.launch {
            try {
                val store = HomeMixes.get(getApplication())
                for (id in MixId.entries) store.ensure(id, api)
                store.publish(api)?.let { _playlists.value = Load.Ready(it) }
            } finally {
                mixesLoading = false
            }
        }
    }

    /** The first songs of the Discovery Station, or a notice when the server has nothing new. */
    fun startDiscovery(onReady: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = TasteEngine.forDiscovery(api, TasteStore.get(getApplication()), emptySet(), 12)
            if (songs.isEmpty()) _notice.value = "Nothing new found on the server." else onReady(songs)
        }
    }

    private val _bio = MutableStateFlow<Pair<String, String>>("" to "")
    val bio: StateFlow<Pair<String, String>> = _bio.asStateFlow()

    /** The biography stays empty when the server has none or cannot be reached. */
    fun loadBio(artistId: String) {
        if (_bio.value.first == artistId) return
        _bio.value = artistId to ""
        viewModelScope.launch {
            val r = api.artistBiography(artistId)
            if (r is Reply.Ok && _bio.value.first == artistId) _bio.value = artistId to r.value
        }
    }

    fun loadAlbum(id: String, force: Boolean = false) {
        val current = _albumDetail.value
        if (!force && current.first == id && current.second is Load.Ready) return
        _albumDetail.value = id to Load.Loading
        viewModelScope.launch {
            val result: Load<AlbumDetail> = when (val r = api.album(id)) {
                is Reply.Ok -> {
                    syncStars(r.value.songs.map { it.id to it.starred } + (r.value.album.id to r.value.album.starred))
                    Load.Ready(r.value)
                }
                is Reply.Fail -> Load.Failed(r.message)
            }
            _albumDetail.value = id to result
        }
    }

    fun loadFavorites(force: Boolean = false) {
        if (!force && _favorites.value is Load.Ready) return
        _favorites.value = Load.Loading
        viewModelScope.launch {
            _favorites.value = when (val r = api.favorites()) {
                is Reply.Ok -> {
                    // Everything in this list is a favorite by definition
                    val f = r.value
                    syncStars((f.artists.map { it.id } + f.albums.map { it.id } + f.songs.map { it.id }).map { it to true })
                    Load.Ready(f)
                }
                is Reply.Fail -> Load.Failed(r.message)
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _search.value = "" to Load.Loading
            return
        }
        if (_search.value.first == query && _search.value.second is Load.Ready) return
        _search.value = query to Load.Loading
        viewModelScope.launch {
            val result: Load<SearchResults> = when (val r = api.search(query)) {
                is Reply.Ok -> {
                    syncStars(
                        r.value.songs.map { it.id to it.starred } +
                            r.value.albums.map { it.id to it.starred } +
                            r.value.artists.map { it.id to it.starred }
                    )
                    Load.Ready(r.value)
                }
                is Reply.Fail -> Load.Failed(r.message)
            }
            // An answer to an older search term is dropped
            if (_search.value.first == query) _search.value = query to result
        }
    }

    fun rememberSearch(term: String) {
        viewModelScope.launch { recentStore.add(term) }
    }

    fun clearRecent() {
        viewModelScope.launch { recentStore.clear() }
    }

    fun searchSongs(query: String, force: Boolean = false) {
        val current = _songs.value
        if (!force && current.started && current.query == query) return
        _songs.value = SongsState(query = query, started = true)
        fetchSongs()
    }

    fun loadMoreSongs() {
        val s = _songs.value
        if (!s.started || s.loading || s.end || s.error != null) return
        fetchSongs()
    }

    private fun fetchSongs() {
        val asked = _songs.value
        _songs.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val reply = api.songs(asked.query, asked.items.size)
            // An answer to an older search or page is dropped
            val now = _songs.value
            if (now.query != asked.query || now.items.size != asked.items.size) return@launch
            when (reply) {
                is Reply.Ok -> {
                    syncStars(reply.value.map { it.id to it.starred })
                    _songs.update { it.copy(items = it.items + reply.value, loading = false, end = reply.value.isEmpty()) }
                }
                is Reply.Fail -> _songs.update { it.copy(loading = false, error = reply.message) }
            }
        }
    }

    /** [quiet] keeps the old list on screen while the new one loads. */
    fun loadPlaylists(force: Boolean = false, quiet: Boolean = false) {
        if (!force && _playlists.value is Load.Ready) return
        if (!quiet) _playlists.value = Load.Loading
        viewModelScope.launch {
            when (val r = api.playlists()) {
                is Reply.Ok -> _playlists.value = Load.Ready(r.value)
                is Reply.Fail -> if (quiet) _notice.value = r.message else _playlists.value = Load.Failed(r.message)
            }
        }
    }

    fun loadPlaylist(id: String, force: Boolean = false, quiet: Boolean = false) {
        val current = _playlistDetail.value
        if (!force && current.first == id && current.second is Load.Ready) return
        if (!quiet || current.first != id) _playlistDetail.value = id to Load.Loading
        viewModelScope.launch {
            when (val r = api.playlist(id)) {
                is Reply.Ok -> {
                    syncStars(r.value.songs.map { it.id to it.starred })
                    _playlistDetail.value = id to Load.Ready(r.value)
                }
                is Reply.Fail -> if (quiet) _notice.value = r.message else _playlistDetail.value = id to Load.Failed(r.message)
            }
        }
    }

    fun createPlaylist(name: String, songIds: List<String> = emptyList()) =
        change("Playlist \"$name\" created.") { api.createPlaylist(name, songIds) }

    fun renamePlaylist(id: String, name: String) = change(null) { api.renamePlaylist(id, name) }

    fun addToPlaylist(playlist: Playlist, songIds: List<String>) =
        change("Added to \"${playlist.name}\".") { api.addToPlaylist(playlist.id, songIds) }

    fun removeFromPlaylist(id: String, index: Int) = change(null) { api.removeFromPlaylist(id, index) }

    fun deletePlaylist(id: String, onDone: () -> Unit) = change("Playlist deleted.", onDone, reloadOpen = false) { api.deletePlaylist(id) }

    /** Runs a change on the server, then reloads the lists that may have changed. */
    private fun change(done: String?, onDone: () -> Unit = {}, reloadOpen: Boolean = true, call: suspend () -> Reply<Unit>) {
        viewModelScope.launch {
            when (val r = call()) {
                is Reply.Ok -> {
                    done?.let { _notice.value = it }
                    loadPlaylists(force = true, quiet = true)
                    val open = _playlistDetail.value.first
                    if (reloadOpen && open.isNotEmpty()) loadPlaylist(open, force = true, quiet = true)
                    onDone()
                }
                is Reply.Fail -> _notice.value = r.message
            }
        }
    }

    fun loadLyrics(id: String, artist: String, title: String, force: Boolean = false) {
        val current = _lyrics.value
        if (!force && current.first == id && current.second is Load.Ready) return
        _lyrics.value = id to Load.Loading
        viewModelScope.launch {
            val result: Load<Lyrics?> = when (val r = api.lyrics(id, artist, title)) {
                is Reply.Ok -> Load.Ready(r.value)
                is Reply.Fail -> Load.Failed(r.message)
            }
            _lyrics.value = id to result
        }
    }

    /** Looks up the current favorite state and rating of one song, for the player. */
    fun loadSong(id: String) {
        viewModelScope.launch {
            val r = api.song(id)
            if (r is Reply.Ok) {
                syncStars(listOf(id to r.value.starred))
                _ratings.update { it + (id to r.value.userRating) }
            }
        }
    }

    /** Tapping the star that is already the rating removes the rating. */
    fun setRating(id: String, rating: Int) {
        val old = _ratings.value[id] ?: 0
        val target = if (rating == old) 0 else rating
        _ratings.update { it + (id to target) }
        viewModelScope.launch {
            val r = api.setRating(id, target)
            if (r is Reply.Fail) {
                _ratings.update { it + (id to old) }
                _notice.value = "Could not change the rating: ${r.message}"
            }
        }
    }

    fun toggleStar(kind: StarKind, id: String) {
        val wanted = id !in _starred.value
        setLocal(id, wanted)
        viewModelScope.launch {
            val r = api.setStarred(kind, id, wanted)
            if (r is Reply.Fail) {
                setLocal(id, !wanted)
                _notice.value = "Could not change the favorite: ${r.message}"
            } else {
                // The favorites list is out of date now
                staleFavorites = true
                // A new heart on a song: the option "download favourites" picks it up
                if (kind == StarKind.SONG && wanted) DownloadManager.get(getApplication()).syncFavorites()
            }
        }
    }

    private var staleFavorites = false

    private var lastRefresh = 0L

    /** Reloads, without a spinner, the lists that were opened before. At most once a minute. */
    fun refreshAll() {
        val now = System.currentTimeMillis()
        if (now - lastRefresh < 60_000L) return
        lastRefresh = now
        viewModelScope.launch { loadPlaylists(force = true, quiet = true) }
        if (_artists.value is Load.Ready) viewModelScope.launch {
            val r = api.artists()
            if (r is Reply.Ok) {
                syncStars(r.value.map { it.id to it.starred })
                _artists.value = Load.Ready(r.value)
            }
        }
        _albumLists.value.filter { it.value is Load.Ready }.keys.forEach { kind ->
            viewModelScope.launch {
                val r = api.albums(kind)
                if (r is Reply.Ok) {
                    syncStars(r.value.map { it.id to it.starred })
                    _albumLists.update { it + (kind to Load.Ready(r.value)) }
                }
            }
        }
        if (_favorites.value is Load.Ready) viewModelScope.launch {
            val r = api.favorites()
            if (r is Reply.Ok) _favorites.value = Load.Ready(r.value)
        }
    }

    /** Asks the server for each song id and queues the full songs for download. */
    fun downloadByIds(ids: List<String>) {
        viewModelScope.launch {
            val songs = mutableListOf<Song>()
            for (id in ids) {
                val r = api.song(id)
                if (r is Reply.Ok) songs += r.value
            }
            if (songs.isEmpty()) {
                _notice.value = "Could not load these songs."
            } else {
                DownloadManager.get(getApplication()).enqueue(songs)
                val skipped = ids.size - songs.size
                _notice.value = if (skipped == 0) "Added to downloads" else "Added ${songs.size} to downloads, $skipped skipped"
            }
        }
    }

    /** Called when the favorites screen opens: reload when a heart was tapped since the last load. */
    fun openFavorites() {
        val reload = staleFavorites
        staleFavorites = false
        loadFavorites(force = reload)
    }

    private fun setLocal(id: String, starred: Boolean) {
        _starred.update { if (starred) it + id else it - id }
    }

    // The server is the truth: items listed as not starred are removed from the local set
    private fun syncStars(items: List<Pair<String, Boolean>>) {
        _starred.update { current ->
            var next = current
            items.forEach { (id, starred) -> next = if (starred) next + id else next - id }
            next
        }
    }
}
