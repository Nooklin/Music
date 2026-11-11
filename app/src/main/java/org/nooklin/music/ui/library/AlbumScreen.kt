package org.nooklin.music.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.music.data.library.AlbumDetail
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.library.StarKind
import org.nooklin.music.ui.common.LocalBottomOverlay
import org.nooklin.music.ui.playback.coverBackground

/**
 * An album: the cover, title and artist, a row with Shuffle, Play and the favourite heart, and the songs with their own "...".
 * The background takes its colour from the cover. A tap on a song starts it (see [rememberSongTap]).
 */
@Composable
fun AlbumScreen(
    albumId: String,
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    val songTap = rememberSongTap(onPlay)
    val state by vm.albumDetail.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    LaunchedEffect(albumId) { vm.loadAlbum(albumId) }

    val load = if (state.first == albumId) state.second else Load.Loading
    val detail = (load as? Load.Ready<AlbumDetail>)?.data

    val bitmap = rememberCoverBitmap(detail?.album?.coverArt, 256)
    val coverColor by produceState<Color?>(null, bitmap) { value = bitmap?.let { coverBackground(it) } }
    val background by animateColorAsState(coverColor ?: MaterialTheme.colorScheme.background, tween(400), label = "albumBackground")
    // White on the cover colour (it is made dark enough for that), the theme colour while there is no cover colour yet
    val fg = if (coverColor != null) Color.White else MaterialTheme.colorScheme.onSurface
    val fgSoft = fg.copy(alpha = 0.7f)

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice!!)
            vm.noticeShown()
        }
    }

    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomOverlay.current)) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CompositionLocalProvider(LocalContentColor provides fg) {
                LoadBody(load, onRetry = { vm.loadAlbum(albumId, force = true) }) { loaded ->
                    val album = loaded.album
                    val songs = loaded.songs
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 72.dp, bottom = LocalBottomOverlay.current)
                    ) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                CoverImage(album.coverArt, 1024, Modifier.fillMaxWidth(0.68f).aspectRatio(1f), 12.dp)
                                Spacer(Modifier.height(20.dp))
                                Text(album.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(album.artist, fontSize = 20.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val meta = listOf(
                                    songs.firstOrNull()?.genre.orEmpty(),
                                    if (album.year > 0) album.year.toString() else "",
                                    songs.firstOrNull()?.suffix.orEmpty().uppercase()
                                ).filter { it.isNotEmpty() }.joinToString(" · ")
                                if (meta.isNotEmpty()) Text(meta, color = fgSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                Spacer(Modifier.height(20.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    RoundButton(fg, enabled = songs.isNotEmpty(), onClick = { onPlay(songs, 0, true) }) {
                                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = fg)
                                    }
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(Color.White.copy(alpha = if (songs.isNotEmpty()) 1f else 0.4f))
                                            .clickable(enabled = songs.isNotEmpty()) { onPlay(songs, 0, false) },
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black)
                                        Text("Play", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                    }
                                    val isStarred = album.id in starred
                                    RoundButton(fg, onClick = { vm.toggleStar(StarKind.ALBUM, album.id) }) {
                                        Icon(
                                            if (isStarred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = if (isStarred) "Remove from favorites" else "Add to favorites",
                                            tint = fg
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                        if (songs.isEmpty()) {
                            item { Text("No songs in this album.", color = fgSoft, modifier = Modifier.padding(24.dp)) }
                        }
                        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable { songTap(songs, index) }
                                    .padding(start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (song.track > 0) song.track.toString() else (index + 1).toString(),
                                    color = fgSoft,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(32.dp)
                                )
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                SongMenu(vm, song.id, song = song)
                            }
                        }
                        if (songs.isNotEmpty()) {
                            item {
                                val minutes = (songs.sumOf { it.durationSeconds } + 30) / 60
                                Text(
                                    "${songs.size} ${if (songs.size == 1) "Song" else "Songs"} · $minutes min",
                                    color = fgSoft,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            // The back button and the album menu float over the content
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundButton(fg, onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = fg) }
                detail?.album?.let { album ->
                    AlbumMenu(vm, album) { open ->
                        RoundButton(fg, onClick = open) { Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = fg) }
                    }
                }
            }
        }
    }
}

/** A round 56 dp button with a translucent circle behind it. */
@Composable
private fun RoundButton(fg: Color, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp).clip(CircleShape).background(fg.copy(alpha = if (enabled) 0.2f else 0.1f))
    ) { content() }
}
