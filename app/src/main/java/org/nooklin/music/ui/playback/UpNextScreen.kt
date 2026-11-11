package org.nooklin.music.ui.playback

import androidx.compose.ui.unit.sp
import org.nooklin.music.ui.common.LocalBottomOverlay
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.ui.library.NameDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.music.ui.library.CoverArt
import org.nooklin.music.ui.library.LibraryViewModel
import org.nooklin.music.ui.library.SongMenu

/** The songs that come after the current one, in the order they will play. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpNextScreen(
    player: PlayerState,
    library: LibraryViewModel,
    onBack: () -> Unit,
    onJump: (Int) -> Unit,
    onShuffle: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var onlyDownloaded by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val appContext = LocalContext.current
    val downloads = remember { DownloadManager.get(appContext) }
    val downloaded by downloads.downloaded.collectAsStateWithLifecycle()
    val notice by library.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice!!)
            library.noticeShown()
        }
    }

    val rows = remember(player.upcoming, player.queue) {
        player.upcoming.mapNotNull { index -> player.queue.getOrNull(index)?.let { index to it } }
    }
    val shown = remember(rows, onlyDownloaded, query, downloaded) {
        val byFilter = if (onlyDownloaded) rows.filter { (_, row) -> row.id in downloaded } else rows
        if (query.isBlank()) {
            byFilter
        } else {
            byFilter.filter { (_, row) ->
                row.title.contains(query, ignoreCase = true) || row.artist.contains(query, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Up Next", fontSize = 34.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    val ids = rows.map { it.second.id }.filter { it.isNotEmpty() }
                    val allDownloaded = ids.isNotEmpty() && ids.all { it in downloaded }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                enabled = ids.isNotEmpty(),
                                onClick = {
                                    if (allDownloaded) {
                                        ids.forEach { downloads.remove(it) }
                                        library.say("Downloads removed")
                                    } else {
                                        library.downloadByIds(ids.filter { it !in downloaded })
                                    }
                                }
                            ) {
                                Icon(
                                    if (allDownloaded) Icons.Rounded.Delete else Icons.Rounded.Download,
                                    contentDescription = if (allDownloaded) "Remove downloads" else "Download"
                                )
                            }
                            IconButton(enabled = shown.isNotEmpty(), onClick = { naming = true }) {
                                Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Save as playlist")
                            }
                            IconButton(onClick = { filterOpen = true }) {
                                Icon(Icons.Rounded.FilterList, contentDescription = "Filter")
                            }
                            DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("All Songs") },
                                    leadingIcon = { if (!onlyDownloaded) Icon(Icons.Rounded.Check, contentDescription = null) },
                                    onClick = { onlyDownloaded = false; filterOpen = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Downloaded Songs") },
                                    leadingIcon = { if (onlyDownloaded) Icon(Icons.Rounded.Check, contentDescription = null) },
                                    onClick = { onlyDownloaded = true; filterOpen = false }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomOverlay.current)) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search in Up Next") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = { shown.firstOrNull()?.let { onJump(it.first) } },
                    enabled = shown.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text("Play")
                }
                FilledTonalButton(
                    onClick = onShuffle,
                    enabled = rows.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Text("Shuffle")
                }
            }
            if (shown.isEmpty()) {
                Text(
                    if (rows.isEmpty()) "Nothing is queued." else "No songs match.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)) {
                itemsIndexed(shown, key = { _, item -> item.first }) { _, (index, row) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable { onJump(index) }
                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverArt(row.coverId, 52.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                row.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (row.id.isNotEmpty()) SongMenu(library, row.id)
                    }
                }
            }
        }
    }
    if (naming) {
        NameDialog(
            title = "Save as playlist",
            initial = "",
            confirm = "Save",
            onConfirm = { name ->
                naming = false
                library.createPlaylist(name.trim(), shown.map { it.second.id }.filter { it.isNotEmpty() })
            },
            onDismiss = { naming = false }
        )
    }
}
