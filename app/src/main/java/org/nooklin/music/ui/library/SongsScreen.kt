package org.nooklin.music.ui.library

import org.nooklin.music.ui.common.LocalBottomOverlay
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import java.text.Collator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.nooklin.music.data.library.Song

/** How the loaded songs are ordered. SERVER keeps the order the server gave. */
private enum class SongSort(val label: String) {
    SERVER("Server order"), TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), DURATION("Duration")
}

/** All songs on the server, loaded page by page, with a search field and a way to pick several for a playlist. */
@Composable
fun SongsScreen(vm: LibraryViewModel, onBack: () -> Unit, onPlay: (List<Song>, Int, Boolean) -> Unit) {
    val songTap = rememberSongTap(onPlay)
    val state by vm.songs.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val gathering by vm.gathering.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(state.query) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var adding by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(SongSort.SERVER) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    val collator = remember { Collator.getInstance() }

    // Typing waits a moment so the server is not asked after every letter
    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(300)
        vm.searchSongs(query)
    }

    // The songs are loaded page by page in the server order; sorting orders the songs that are loaded
    val shown = remember(state.items, sort, ascending) {
        val sorted = when (sort) {
            SongSort.SERVER -> state.items
            SongSort.TITLE -> state.items.sortedWith(compareBy(collator) { it.title })
            SongSort.ARTIST -> state.items.sortedWith(compareBy(collator) { it.artist })
            SongSort.ALBUM -> state.items.sortedWith(compareBy(collator) { it.album })
            SongSort.DURATION -> state.items.sortedBy { it.durationSeconds }
        }
        if (sort != SongSort.SERVER && !ascending) sorted.reversed() else sorted
    }

    SearchableFrame(
        title = "Songs",
        onBack = onBack,
        onRefresh = { vm.searchSongs(query, force = true) },
        notice = notice,
        onNoticeShown = vm::noticeShown,
        query = query,
        onQueryChange = { query = it },
        placeholder = "Search Songs",
        actions = {
            PlayShufflePill(
                if (shown.isEmpty()) null else PlayTarget(
                    busy = gathering,
                    onPlay = {
                        if (!state.end) vm.say("Playing the ${shown.size} songs loaded so far")
                        onPlay(shown, 0, false)
                    },
                    onShuffle = {
                        if (state.query.isEmpty()) {
                            // All songs: random songs from the whole library, not the first page in the order of the server
                            vm.randomSongs(SHUFFLE_SONG_COUNT) { onPlay(it, 0, true) }
                        } else {
                            if (!state.end) vm.say("Shuffling the ${shown.size} songs loaded so far")
                            onPlay(shown, 0, true)
                        }
                    }
                )
            )
        },
        sortSummary = if (sort == SongSort.SERVER) null else sortSummaryText(sort.label, ascending),
        menu = { close ->
            DropdownMenuItem(
                text = { Text(if (selecting) "Done selecting" else "Select") },
                onClick = { close(); selecting = !selecting; selected = emptySet() }
            )
            HorizontalDivider()
            SortMenu(
                options = SongSort.entries.map { it.label },
                selected = sort.ordinal,
                ascending = ascending,
                selectedHasDirection = sort != SongSort.SERVER,
                onSelect = { sort = SongSort.entries[it]; ascending = true },
                onAscendingChange = { ascending = it },
                close = close
            )
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                TextButton(
                    onClick = { adding = true },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { Text("Add to playlist (${selected.size})") }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.items.isEmpty() && state.error != null -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { vm.searchSongs(query, force = true) }) { Text("Try again") }
                    }
                    state.items.isEmpty() && (state.loading || !state.end) -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    state.items.isEmpty() -> EmptyText("No songs found.")
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)) {
                        itemsIndexed(shown, key = { index, _ -> index }) { index, song ->
                            // Reaching the last loaded song asks for the next page
                            if (index == shown.lastIndex) {
                                LaunchedEffect(state.items.size) { vm.loadMoreSongs() }
                            }
                            SongRow(
                                vm = vm,
                                song = song,
                                selecting = selecting,
                                checked = song.id in selected,
                                onClick = {
                                    if (selecting) {
                                        selected = if (song.id in selected) selected - song.id else selected + song.id
                                    } else {
                                        songTap(shown, index)
                                    }
                                }
                            )
                        }
                        if (state.loading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        state.error?.let { message ->
                            item {
                                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(message, color = MaterialTheme.colorScheme.error)
                                    TextButton(onClick = vm::loadMoreSongs) { Text("Try again") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        AddToPlaylistDialog(vm, selected.toList(), onDismiss = { adding = false })
    }
}

@Composable
private fun SongRow(vm: LibraryViewModel, song: Song, selecting: Boolean, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) Checkbox(checked = checked, onCheckedChange = { onClick() })
        CoverArt(song.coverArt, 52.dp)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(song.artist, song.album).filter { it.isNotEmpty() }.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (selecting) Box(Modifier.padding(end = 16.dp)) else SongMenu(vm, song.id, song = song)
    }
}
