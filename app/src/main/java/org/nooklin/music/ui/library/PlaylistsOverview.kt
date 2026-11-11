package org.nooklin.music.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.Collator
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import kotlinx.coroutines.launch
import org.nooklin.music.data.library.Playlist
import org.nooklin.music.data.local.AppPreferences
import org.nooklin.music.playback.taste.mixOf
import org.nooklin.music.ui.common.LocalBottomOverlay

private fun durationText(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 -> "$minutes min"
        else -> "$totalSeconds sec"
    }
}

private fun playlistSubtitle(playlist: Playlist): String {
    val count = if (playlist.songCount == 1) "1 song" else "${playlist.songCount} songs"
    val base = if (playlist.durationSeconds > 0) "$count · ${durationText(playlist.durationSeconds)}" else count
    val day = mixOf(playlist)?.day ?: return base
    return "$base · updates ${day.getDisplayName(DayStyle.FULL, Locale.ENGLISH)}"
}

@Composable
private fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun PlaylistRow(playlist: Playlist, onOpen: (String) -> Unit) {
    MediaRow(
        title = playlist.name,
        subtitle = playlistSubtitle(playlist),
        coverId = playlist.coverArt,
        starred = null,
        onToggleStar = {},
        onClick = { onOpen(playlist.id) },
        coverSize = 72.dp,
        cover = { PlaylistCover(playlist, Modifier.size(72.dp), 10.dp) }
    )
}

@Composable
private fun PlaylistTile(playlist: Playlist, onOpen: (String) -> Unit) {
    Column(Modifier.clickable { onOpen(playlist.id) }) {
        PlaylistCover(playlist, Modifier.fillMaxWidth().aspectRatio(1f), 12.dp)
        Text(
            playlist.name,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            playlistSubtitle(playlist),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * The list of playlists. A search field under the title; tapping it slides the title and the menu away so the search
 * has the top of the screen, and the circle with the X brings them back. The "..." menu has New Playlist, the view
 * (list or grid) and the sort order of the names. View and sort order are remembered.
 */
@Composable
fun PlaylistsScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenPlaylist: (String) -> Unit) {
    val load by vm.playlists.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val grid by preferences.playlistGrid.collectAsStateWithLifecycle(false)
    val ascending by preferences.playlistAscending.collectAsStateWithLifecycle(true)
    var creating by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        vm.loadPlaylists()
        vm.loadMixes()
    }

    SearchableFrame(
        title = "Playlists",
        onBack = onBack,
        onRefresh = { vm.loadPlaylists(force = true) },
        notice = notice,
        onNoticeShown = vm::noticeShown,
        query = query,
        onQueryChange = { query = it },
        placeholder = "Search Playlists",
        sortSummary = sortSummaryText("Name", ascending),
        menu = { close ->
            DropdownMenuItem(text = { Text("New Playlist") }, onClick = { close(); creating = true })
            HorizontalDivider()
            Text(
                "View",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            DropdownMenuItem(
                text = { Text("List") },
                trailingIcon = { if (!grid) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                onClick = { close(); scope.launch { preferences.setPlaylistGrid(false) } }
            )
            DropdownMenuItem(
                text = { Text("Grid") },
                trailingIcon = { if (grid) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                onClick = { close(); scope.launch { preferences.setPlaylistGrid(true) } }
            )
            HorizontalDivider()
            SortMenu(
                options = listOf("Name"),
                selected = 0,
                ascending = ascending,
                selectedHasDirection = true,
                onSelect = {},
                onAscendingChange = { value -> scope.launch { preferences.setPlaylistAscending(value) } },
                close = close
            )
        }
    ) {
        LoadBody(load, onRetry = { vm.loadPlaylists(force = true) }) { all ->
            val collator = remember { Collator.getInstance() }
            val matching = all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            // "Made for you" keeps its own order, the other playlists follow the chosen sort order
            val made = matching.filter { mixOf(it) != null }.sortedBy { mixOf(it)?.ordinal ?: 0 }
            val standard = matching.filter { mixOf(it) == null }
                .sortedWith(compareBy(collator) { it.name })
                .let { if (ascending) it else it.reversed() }
            if (made.isEmpty() && standard.isEmpty()) {
                if (all.isEmpty()) EmptyText("No playlists yet.", "New playlist") { creating = true }
                else EmptyText("No playlists match.", "Clear search") { query = "" }
            } else if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalBottomOverlay.current),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (made.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { GroupTitle("Made for you") }
                        gridItems(made, key = { it.id }) { PlaylistTile(it, onOpenPlaylist) }
                        if (standard.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { GroupTitle("Playlists") }
                    }
                    gridItems(standard, key = { it.id }) { PlaylistTile(it, onOpenPlaylist) }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)) {
                    if (made.isNotEmpty()) {
                        item { GroupTitle("Made for you", Modifier.padding(horizontal = 16.dp)) }
                        items(made, key = { it.id }) { PlaylistRow(it, onOpenPlaylist) }
                        if (standard.isNotEmpty()) item { GroupTitle("Playlists", Modifier.padding(horizontal = 16.dp)) }
                    }
                    items(standard, key = { it.id }) { PlaylistRow(it, onOpenPlaylist) }
                }
            }
        }
    }
    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirm = "Create",
            onConfirm = { vm.createPlaylist(it); creating = false },
            onDismiss = { creating = false }
        )
    }
}
