package org.nooklin.music.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.music.data.library.Genre
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.library.StarKind
import org.nooklin.music.ui.common.LocalBottomOverlay

/** The genres of the library, from the tags of the songs. A tap shows the albums of that genre. */
@Composable
fun GenresScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onOpenGenre: (String) -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    val load by vm.genres.collectAsStateWithLifecycle()
    val gathering by vm.gathering.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadGenres() }

    val genreList = (load as? Load.Ready<List<Genre>>)?.data.orEmpty()

    LibraryScaffold(
        "Genres", onBack, { vm.loadGenres(force = true) }, notice, vm::noticeShown,
        actions = {
            PlayShufflePill(
                if (genreList.isEmpty()) null else PlayTarget(
                    busy = gathering,
                    onPlay = {
                        if (genreList.sumOf { it.albumCount } > PLAY_ALBUM_LIMIT) {
                            vm.say("Playing the songs of the first $PLAY_ALBUM_LIMIT albums")
                        }
                        vm.songsOfGenres(genreList, PLAY_ALBUM_LIMIT) { onPlay(it, 0, false) }
                    },
                    // Random songs from the whole library: a song without a genre tag can come up too
                    onShuffle = { vm.randomSongs(SHUFFLE_SONG_COUNT) { onPlay(it, 0, true) } }
                )
            )
        }
    ) {
        LoadBody(load, onRetry = { vm.loadGenres(force = true) }) { genres ->
            if (genres.isEmpty()) {
                EmptyText("No genres on this server yet. Genres come from the tags of your songs.", "Refresh") { vm.loadGenres(force = true) }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)) {
                    items(genres, key = { it.name }) { genre ->
                        MediaRow(
                            title = genre.name,
                            subtitle = "${genre.albumCount} albums · ${genre.songCount} songs",
                            coverId = null,
                            starred = null,
                            onToggleStar = {},
                            onClick = { onOpenGenre(genre.name) },
                            showCover = false
                        )
                    }
                }
            }
        }
    }
}

/** The albums of one genre. */
@Composable
fun GenreAlbumsScreen(
    genre: String,
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    val lists by vm.genreAlbums.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    LaunchedEffect(genre) { vm.loadGenreAlbums(genre) }

    LibraryScaffold(genre, onBack, { vm.loadGenreAlbums(genre, force = true) }, notice, vm::noticeShown) {
        LoadBody(lists[genre] ?: Load.Loading, onRetry = { vm.loadGenreAlbums(genre, force = true) }) { albums ->
            if (albums.isEmpty()) {
                EmptyText("No albums in this genre.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)) {
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
