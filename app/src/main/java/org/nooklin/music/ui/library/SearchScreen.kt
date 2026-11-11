package org.nooklin.music.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.library.StarKind
import org.nooklin.music.ui.common.GlassPill

/** Searches artists, albums and songs on the server. Without a search term it lists what was searched before. */
@Composable
fun SearchScreen(
    vm: LibraryViewModel,
    onClose: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val recent by vm.recent.collectAsStateWithLifecycle()
    val searched by vm.searchState.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice!!)
            vm.noticeShown()
        }
    }
    // Typing waits a moment so the server is not asked after every letter
    LaunchedEffect(query) {
        if (query.isBlank()) {
            vm.search("")
        } else {
            delay(300)
            vm.search(query.trim())
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    shape = GlassPill.shape,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("Search Library") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.rememberSearch(query) }),
                    modifier = Modifier.weight(1f).focusRequester(focus)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close search")
                }
            }

            if (query.isBlank()) {
                if (recent.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = vm::clearRecent) { Text("Clear") }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(recent, key = { it }) { term ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clickable { query = term }
                                    .padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(term, modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            } else {
                val shownQuery = query.trim()
                val load = if (searched.first == shownQuery) searched.second else Load.Loading
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LoadBody(load, onRetry = { vm.search(shownQuery) }) { results ->
                        if (results.artists.isEmpty() && results.albums.isEmpty() && results.songs.isEmpty()) {
                            EmptyText("Nothing found.")
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                if (results.artists.isNotEmpty()) {
                                    item { SectionLabel("Artists") }
                                    items(results.artists, key = { "ar" + it.id }) { artist ->
                                        MediaRow(
                                            title = artist.name,
                                            subtitle = if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums",
                                            coverId = artist.coverArt,
                                            starred = artist.id in starred,
                                            onToggleStar = { vm.toggleStar(StarKind.ARTIST, artist.id) },
                                            onClick = { vm.rememberSearch(shownQuery); onOpenArtist(artist.id) }
                                        )
                                    }
                                }
                                if (results.albums.isNotEmpty()) {
                                    item { SectionLabel("Albums") }
                                    items(results.albums, key = { "al" + it.id }) { album ->
                                        MediaRow(
                                            title = album.name,
                                            subtitle = albumSubtitle(album.artist, album.year, album.songCount),
                                            coverId = album.coverArt,
                                            starred = album.id in starred,
                                            onToggleStar = { vm.toggleStar(StarKind.ALBUM, album.id) },
                                            onClick = { vm.rememberSearch(shownQuery); onOpenAlbum(album.id) }
                                        )
                                    }
                                }
                                if (results.songs.isNotEmpty()) {
                                    item { SectionLabel("Songs") }
                                    itemsIndexed(results.songs, key = { _, song -> "so" + song.id }) { index, song ->
                                        MediaRow(
                                            title = song.title,
                                            subtitle = listOf(song.artist, song.album).filter { it.isNotEmpty() }
                                                .joinToString(" · "),
                                            coverId = song.coverArt,
                                            starred = song.id in starred,
                                            onToggleStar = { vm.toggleStar(StarKind.SONG, song.id) },
                                            // Plays the songs of this search from the tapped one
                                            onClick = { vm.rememberSearch(shownQuery); onPlay(results.songs, index, false) },
                                            menu = { SongMenu(vm, song.id, song = song) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
