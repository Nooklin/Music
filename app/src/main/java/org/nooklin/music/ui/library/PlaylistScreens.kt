package org.nooklin.music.ui.library

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import org.nooklin.music.ui.common.GlassPillContainer
import org.nooklin.music.ui.common.LocalQueueActions
import org.nooklin.music.ui.common.NooklinMenu
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.OffsetDateTime
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import org.nooklin.music.ui.common.LocalBottomOverlay
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.nooklin.music.data.download.DownloadManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.music.data.library.PlaylistDetail
import org.nooklin.music.data.library.Song
import org.nooklin.music.ui.common.GlassPill

@Composable
fun PlaylistScreen(
    playlistId: String,
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    val state by vm.playlistDetail.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val appContext = LocalContext.current
    val downloads = remember { DownloadManager.get(appContext) }
    val downloaded by downloads.downloaded.collectAsStateWithLifecycle()
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    // Pulling the list down while it is already at the top opens the search field
    val pullToSearch = remember {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (source == NestedScrollSource.UserInput && available.y > 0f && atTop) {
                    pulled += available.y
                    if (pulled > 160f) {
                        searchOpen = true
                        pulled = 0f
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(playlistId) { vm.loadPlaylist(playlistId) }
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice!!)
            vm.noticeShown()
        }
    }

    val load = if (state.first == playlistId) state.second else Load.Loading
    val detail = (load as? Load.Ready<PlaylistDetail>)?.data

    // The backdrop is always dark, so the texts on it are white in every theme
    CompositionLocalProvider(LocalContentColor provides Color.White) {
    Box(Modifier.fillMaxSize()) {
        // The whole screen glows with the colors of the playlist covers
        if (detail != null) PlaylistBackdrop(detail.songs.mapNotNull { it.coverArt }.distinct().take(4))
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomOverlay.current)) }
        ) { padding ->
            LoadBody(load, onRetry = { vm.loadPlaylist(playlistId, force = true) }) { content ->
                val allDownloaded = content.songs.isNotEmpty() && content.songs.all { it.id in downloaded }
                val toggleDownload = {
                    if (allDownloaded) {
                        content.songs.forEach { downloads.remove(it.id) }
                        vm.say("Downloads removed")
                    } else {
                        downloads.enqueue(content.songs)
                        vm.say("Added to downloads")
                    }
                }
                val rows = content.songs.withIndex().filter { (_, song) ->
                    query.isBlank() || listOf(song.title, song.artist, song.album).any { it.contains(query.trim(), ignoreCase = true) }
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).nestedScroll(pullToSearch),
                    state = listState,
                    contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)
                ) {
                    if (searchOpen) {
                        item {
                            PlaylistSearchPill(query, { query = it }) { searchOpen = false; query = "" }
                        }
                    }
                    item { PlaylistHeader(content, onPlay, allDownloaded, toggleDownload, searchOpen) }
                    if (content.songs.isEmpty()) {
                        item {
                            Text(
                                "No songs in this playlist. Add songs from the player, an album or the Songs list.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                    if (content.songs.isNotEmpty() && rows.isEmpty()) {
                        item {
                            Text("No songs match.", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(24.dp))
                        }
                    }
                    // The same song can be in a playlist twice, so the position in the playlist is the key
                    itemsIndexed(rows, key = { _, row -> row.index }) { position, row ->
                        val index = row.index
                        val song = row.value
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp)
                                    .clickable { onPlay(rows.map { it.value }, position, false) }
                                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CoverArt(song.coverArt, 52.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                    Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        song.artist,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                SongMenu(vm, song.id, onRemove = { vm.removeFromPlaylist(playlistId, index) }, song = song)
                            }
                            HorizontalDivider(
                                Modifier.padding(start = 82.dp, end = 16.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }

        // Back and the menu float on top of the content
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RoundButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            if (detail != null) {
                Box {
                    RoundButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreHoriz, contentDescription = "More")
                    }
                    NooklinMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                        if (detail != null && detail.songs.isNotEmpty()) {
                            if (detail.songs.all { it.id in downloaded }) {
                                DropdownMenuItem(
                                    text = { Text("Remove downloads") },
                                    onClick = {
                                        menuOpen = false
                                        detail.songs.forEach { downloads.remove(it.id) }
                                        vm.say("Downloads removed")
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Download") },
                                    onClick = {
                                        menuOpen = false
                                        downloads.enqueue(detail.songs)
                                        vm.say("Added to downloads")
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(text = { Text("Search in playlist") }, onClick = { menuOpen = false; searchOpen = true })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renaming = true })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; deleting = true })
                    }
                }
            }
        }
    }
    }

    if (renaming && detail != null) {
        NameDialog(
            title = "Rename playlist",
            initial = detail.playlist.name,
            confirm = "Rename",
            onConfirm = { vm.renamePlaylist(playlistId, it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
    if (deleting && detail != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            shape = GlassPill.shape,
            containerColor = GlassPill.adaptivePopupBackground,
            title = { Text("Delete playlist?") },
            text = { Text("\"${detail.playlist.name}\" is deleted from the server. The songs stay in your library.") },
            confirmButton = {
                TextButton(onClick = { deleting = false; vm.deletePlaylist(playlistId, onBack) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
    ) { content() }
}

/**
 * The covers of the playlist, enlarged to the whole screen, blurred and darkened. With four covers they sit in a 2 by 2
 * grid like the collage on top; with fewer the first cover fills the screen. The darkening is black in every theme,
 * so the white text on top stays readable.
 */
@Composable
private fun PlaylistBackdrop(covers: List<String>) {
    if (covers.isEmpty()) return
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // Larger than the screen, so the soft edges of the blur fall outside it
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.9f
                    scaleY = 1.9f
                }
                .blur(60.dp)
        ) {
            if (covers.size >= 4) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f)) {
                        CoverImage(covers[0], 256, Modifier.weight(1f).fillMaxSize(), 0.dp)
                        CoverImage(covers[1], 256, Modifier.weight(1f).fillMaxSize(), 0.dp)
                    }
                    Row(Modifier.weight(1f)) {
                        CoverImage(covers[2], 256, Modifier.weight(1f).fillMaxSize(), 0.dp)
                        CoverImage(covers[3], 256, Modifier.weight(1f).fillMaxSize(), 0.dp)
                    }
                }
            } else {
                CoverImage(covers.first(), 256, Modifier.fillMaxSize(), 0.dp)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
    }
}

/** The collage of covers, the name, who owns it and when it changed, and the Shuffle and Play buttons. */
@Composable
private fun PlaylistHeader(
    content: PlaylistDetail,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    allDownloaded: Boolean,
    onDownload: () -> Unit,
    searching: Boolean
) {
    val covers = content.songs.mapNotNull { it.coverArt }.distinct().take(4)
    Column(
        Modifier.fillMaxWidth().padding(top = if (searching) 8.dp else 64.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(260.dp).shadow(12.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))) {
            if (covers.size >= 4) {
                Column {
                    Row { CoverImage(covers[0], 400, Modifier.size(130.dp), 0.dp); CoverImage(covers[1], 400, Modifier.size(130.dp), 0.dp) }
                    Row { CoverImage(covers[2], 400, Modifier.size(130.dp), 0.dp); CoverImage(covers[3], 400, Modifier.size(130.dp), 0.dp) }
                }
            } else {
                CoverImage(covers.firstOrNull(), 600, Modifier.size(260.dp), 0.dp)
            }
        }
        Text(
            content.playlist.name,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)
        )
        if (content.playlist.owner.isNotEmpty()) {
            Text(content.playlist.owner, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp))
        }
        val count = if (content.songs.size == 1) "1 song" else "${content.songs.size} songs"
        Text(
            listOfNotNull(count, updatedText(content.playlist.changed)?.let { "Updated $it" }).joinToString(" · "),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        if (content.songs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onPlay(content.songs, 0, true) },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle")
                }
                Button(
                    onClick = { onPlay(content.songs, 0, false) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text("Play", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Icon(
                        if (allDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                        contentDescription = if (allDownloaded) "Remove downloads" else "Download"
                    )
                }
            }
        }
    }
}

/** The search field that opens at the top of the list. It looks in the title, the artist and the album. */
@Composable
private fun PlaylistSearchPill(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(Modifier.padding(top = 64.dp)) {
        GlassPillContainer(backgroundColor = GlassPill.adaptiveFloatingBackground, border = GlassPill.adaptiveBorder, verticalContentPadding = 0.dp) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        placeholder = { Text("Search in playlist") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focus)
                    )
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close search") }
                }
            }
        }
    }
}

// The time the server writes, shown in the local time zone. Nothing is shown when it cannot be read.
private fun updatedText(changed: String?): String? {
    if (changed.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(changed)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm", Locale.getDefault()))
    } catch (e: Exception) {
        null
    }
}

/** The "..." button behind a song, with the way to put it in a playlist. [onRemove] adds "Remove from playlist". */
@Composable
fun SongMenu(vm: LibraryViewModel, songId: String, onRemove: (() -> Unit)? = null, song: Song? = null) {
    var open by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val appContext = LocalContext.current
    val downloads = remember { DownloadManager.get(appContext) }
    val downloaded by downloads.downloaded.collectAsStateWithLifecycle()
    val queue = LocalQueueActions.current
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = "More")
        }
        NooklinMenu(expanded = open, onDismiss = { open = false }) {
            if (song != null && queue != null) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = { open = false; queue.playNext(listOf(song)); vm.say("Playing next") }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = { open = false; queue.addToQueue(listOf(song)); vm.say("Added to queue") }
                )
                DropdownMenuItem(
                    text = { Text("Start radio") },
                    onClick = { open = false; vm.startRadio(song.id, song) { queue.play(it) } }
                )
            }
            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { open = false; adding = true })
            if (song != null) {
                if (song.id in downloaded) {
                    DropdownMenuItem(
                        text = { Text("Remove download") },
                        onClick = { open = false; downloads.remove(song.id); vm.say("Download removed") }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Download") },
                        onClick = { open = false; downloads.enqueue(listOf(song)); vm.say("Added to downloads") }
                    )
                }
            }
            if (onRemove != null) {
                DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { open = false; onRemove() })
            }
        }
    }
    if (adding) AddToPlaylistDialog(vm, listOf(songId), onDismiss = { adding = false })
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Lists the playlists to put [songIds] in, with a way to start a new one. */
@Composable
fun AddToPlaylistDialog(vm: LibraryViewModel, songIds: List<String>, onDismiss: () -> Unit) {
    val load by vm.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadPlaylists() }

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirm = "Create",
            onConfirm = { vm.createPlaylist(it, songIds); onDismiss() },
            onDismiss = { creating = false }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text("Add to playlist") },
        text = {
            Column {
                TextButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("New playlist", modifier = Modifier.fillMaxWidth())
                }
                when (val l = load) {
                    is Load.Loading -> Text("Loading...")
                    is Load.Failed -> Text(l.message, color = MaterialTheme.colorScheme.error)
                    is Load.Ready -> LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(l.data, key = { it.id }) { playlist ->
                            TextButton(
                                onClick = { vm.addToPlaylist(playlist, songIds); onDismiss() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
