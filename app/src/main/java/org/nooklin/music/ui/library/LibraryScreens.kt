package org.nooklin.music.ui.library

import org.nooklin.music.ui.common.LocalBottomOverlay
import org.nooklin.music.ui.common.LocalQueueActions
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import java.text.Collator
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.nooklin.music.ui.common.NooklinMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.nooklin.music.data.download.DownloadManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.music.data.library.AlbumDetail
import org.nooklin.music.data.library.AlbumListKind
import org.nooklin.music.data.library.ArtistDetail
import org.nooklin.music.data.library.Favorites
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.library.StarKind

/** Name is the server order (without articles); Albums orders by the number of albums. */
private enum class ArtistSort(val label: String) {
    NAME("Name"), ALBUMS("Number of albums")
}

@Composable
fun ArtistsScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    val load by vm.artists.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val gathering by vm.gathering.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadArtists() }

    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(ArtistSort.NAME) }
    var ascending by rememberSaveable { mutableStateOf(true) }

    var playTarget by remember { mutableStateOf<PlayTarget?>(null) }

    SearchableFrame(
        actions = { PlayShufflePill(playTarget) },
        title = "Artists",
        onBack = onBack,
        onRefresh = { vm.loadArtists(force = true) },
        notice = notice,
        onNoticeShown = vm::noticeShown,
        query = query,
        onQueryChange = { query = it },
        placeholder = "Search Artists",
        sortSummary = sortSummaryText(sort.label, ascending),
        menu = { close ->
            SortMenu(
                options = ArtistSort.entries.map { it.label },
                selected = sort.ordinal,
                ascending = ascending,
                selectedHasDirection = true,
                onSelect = { sort = ArtistSort.entries[it]; ascending = true },
                onAscendingChange = { ascending = it },
                close = close
            )
        }
    ) {
        LoadBody(load, onRetry = { vm.loadArtists(force = true) }) { loaded ->
            // The server list is already ordered by name without articles
            val artists = remember(loaded, query, sort, ascending) {
                val found = loaded.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                val sorted = when (sort) {
                    ArtistSort.NAME -> found
                    ArtistSort.ALBUMS -> found.sortedBy { it.albumCount }
                }
                if (ascending) sorted else sorted.reversed()
            }
            if (loaded.isEmpty() || artists.isEmpty()) {
                LaunchedEffect(Unit) { playTarget = null }
                if (loaded.isNotEmpty()) EmptyText("No artists match.", "Clear search") { query = "" }
                else EmptyText("No artists on this server yet.", "Refresh") { vm.loadArtists(force = true) }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LaunchedEffect(artists, gathering) {
                        playTarget = PlayTarget(
                            busy = gathering,
                            onPlay = {
                                if (artists.sumOf { it.albumCount } > PLAY_ALBUM_LIMIT) {
                                vm.say("Playing the songs of the first $PLAY_ALBUM_LIMIT albums")
                            }
                            vm.songsOfArtists(artists, PLAY_ALBUM_LIMIT) { onPlay(it, 0, false) }
                            },
                            onShuffle = {
                                if (artists.sumOf { it.albumCount } > PLAY_ALBUM_LIMIT) {
                                vm.say("Shuffling the songs of $PLAY_ALBUM_LIMIT albums of random artists")
                            }
                            vm.songsOfArtists(artists.shuffled(), PLAY_ALBUM_LIMIT) { onPlay(it, 0, true) }
                            }
                        )
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)) {
                        items(artists, key = { it.id }) { artist ->
                            MediaRow(
                                title = artist.name,
                                subtitle = if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums",
                                coverId = artist.coverArt,
                                starred = artist.id in starred,
                                onToggleStar = { vm.toggleStar(StarKind.ARTIST, artist.id) },
                                onClick = { onOpenArtist(artist.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How the albums are ordered. SERVER keeps the order the server gave (newest first, most played, random, ...). */
private enum class AlbumSort(val label: String) {
    SERVER("Server order"), NAME("Name"), ARTIST("Artist"), YEAR("Year")
}

@Composable
fun AlbumsScreen(
    kind: AlbumListKind,
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    val gathering by vm.gathering.collectAsStateWithLifecycle()
    val lists by vm.albumLists.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    LaunchedEffect(kind) { vm.loadAlbums(kind) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(AlbumSort.SERVER) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    val collator = remember { Collator.getInstance() }

    var playTarget by remember { mutableStateOf<PlayTarget?>(null) }

    SearchableFrame(
        actions = { PlayShufflePill(playTarget) },
        title = kind.title,
        onBack = onBack,
        onRefresh = { vm.loadAlbums(kind, force = true) },
        notice = notice,
        onNoticeShown = vm::noticeShown,
        query = query,
        onQueryChange = { query = it },
        placeholder = "Search Albums",
        sortSummary = if (sort == AlbumSort.SERVER) null else sortSummaryText(sort.label, ascending),
        menu = { close ->
            SortMenu(
                options = AlbumSort.entries.map { it.label },
                selected = sort.ordinal,
                ascending = ascending,
                selectedHasDirection = sort != AlbumSort.SERVER,
                onSelect = { sort = AlbumSort.entries[it]; ascending = true },
                onAscendingChange = { ascending = it },
                close = close
            )
        }
    ) {
        LoadBody(lists[kind] ?: Load.Loading, onRetry = { vm.loadAlbums(kind, force = true) }) { loaded ->
            val albums = remember(loaded, query, sort, ascending) {
                val found = loaded.filter {
                    query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
                        it.artist.contains(query.trim(), ignoreCase = true)
                }
                val sorted = when (sort) {
                    AlbumSort.SERVER -> found
                    AlbumSort.NAME -> found.sortedWith(compareBy(collator) { it.sortName.ifEmpty { it.name } })
                    AlbumSort.ARTIST -> found.sortedWith(compareBy(collator) { it.artist })
                    AlbumSort.YEAR -> found.sortedBy { it.year }
                }
                if (sort != AlbumSort.SERVER && !ascending) sorted.reversed() else sorted
            }
            if (loaded.isEmpty() || albums.isEmpty()) {
                LaunchedEffect(Unit) { playTarget = null }
                EmptyText(
                    if (loaded.isNotEmpty()) "No albums match." else when (kind) {
                        AlbumListKind.RECENT, AlbumListKind.FREQUENT ->
                            "Nothing here yet. The server fills this list from what has been played."
                        AlbumListKind.STARRED -> "No favourite albums yet. Tap the star on an album to add it."
                        else -> "No albums on this server yet."
                    },
                    if (loaded.isNotEmpty()) "Clear search" else "Refresh"
                ) { if (loaded.isNotEmpty()) query = "" else vm.loadAlbums(kind, force = true) }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LaunchedEffect(albums, gathering) {
                        playTarget = PlayTarget(
                            busy = gathering,
                            onPlay = {
                                if (albums.size > PLAY_ALBUM_LIMIT) vm.say("Playing the songs of the first $PLAY_ALBUM_LIMIT albums")
                                vm.songsOfAlbums(albums, PLAY_ALBUM_LIMIT) { onPlay(it, 0, false) }
                            },
                            onShuffle = {
                                if (albums.size > PLAY_ALBUM_LIMIT) vm.say("Shuffling the songs of $PLAY_ALBUM_LIMIT random albums")
                                vm.songsOfAlbums(albums.shuffled(), PLAY_ALBUM_LIMIT) { onPlay(it, 0, true) }
                            }
                        )
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)) {
                        items(albums, key = { it.id }) { album ->
                            MediaRow(
                                title = album.name,
                                subtitle = albumSubtitle(album.artist, album.year, album.songCount),
                                coverId = album.coverArt,
                                starred = album.id in starred,
                                onToggleStar = { vm.toggleStar(StarKind.ALBUM, album.id) },
                                onClick = { onOpenAlbum(album.id) },
                                menu = { AlbumMenu(vm, album) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    val songTap = rememberSongTap(onPlay)
    val load by vm.favorites.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.openFavorites() }

    val favoriteSongs = (load as? Load.Ready<Favorites>)?.data?.songs.orEmpty()

    LibraryScaffold(
        "Favorites", onBack, { vm.loadFavorites(force = true) }, notice, vm::noticeShown,
        actions = {
            PlayShufflePill(
                if (favoriteSongs.isEmpty()) null else PlayTarget(
                    busy = false,
                    onPlay = { onPlay(favoriteSongs, 0, false) },
                    onShuffle = { onPlay(favoriteSongs, 0, true) }
                )
            )
        }
    ) {
        LoadBody(load, onRetry = { vm.loadFavorites(force = true) }) { favorites ->
            if (favorites.artists.isEmpty() && favorites.albums.isEmpty() && favorites.songs.isEmpty()) {
                EmptyText("No favorites yet. Tap a heart to add one.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)) {
                    if (favorites.artists.isNotEmpty()) {
                        item { SectionLabel("Artists") }
                        items(favorites.artists, key = { "ar" + it.id }) { artist ->
                            MediaRow(
                                title = artist.name,
                                subtitle = if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums",
                                coverId = artist.coverArt,
                                starred = artist.id in starred,
                                onToggleStar = { vm.toggleStar(StarKind.ARTIST, artist.id) },
                                onClick = { onOpenArtist(artist.id) }
                            )
                        }
                    }
                    if (favorites.albums.isNotEmpty()) {
                        item { SectionLabel("Albums") }
                        items(favorites.albums, key = { "al" + it.id }) { album ->
                            MediaRow(
                                title = album.name,
                                subtitle = albumSubtitle(album.artist, album.year, album.songCount),
                                coverId = album.coverArt,
                                starred = album.id in starred,
                                onToggleStar = { vm.toggleStar(StarKind.ALBUM, album.id) },
                                onClick = { onOpenAlbum(album.id) },
                                menu = { AlbumMenu(vm, album) }
                            )
                        }
                    }
                    if (favorites.songs.isNotEmpty()) {
                        item { SectionLabel("Songs") }
                        itemsIndexed(favorites.songs, key = { _, song -> "so" + song.id }) { index, song ->
                            MediaRow(
                                title = song.title,
                                subtitle = song.artist,
                                coverId = song.coverArt,
                                starred = song.id in starred,
                                onToggleStar = { vm.toggleStar(StarKind.SONG, song.id) },
                                // Plays the favorite songs from this one
                                onClick = { songTap(favorites.songs, index) },
                                menu = { SongMenu(vm, song.id, song = song) }
                            )
                        }
                    }
                }
            }
        }
    }
}
