package org.nooklin.music.ui.library

import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nooklin.music.data.library.CoverCache
import org.nooklin.music.data.library.Song
import org.nooklin.music.ui.common.LocalBottomOverlay
import org.nooklin.music.ui.common.LocalQueueActions
import org.nooklin.music.ui.common.NooklinTopBar
import org.nooklin.music.data.server.ServerSession

/** Title bar with back and refresh, and a place for short messages. Used by every library screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScaffold(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    notice: String?,
    onNoticeShown: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbarHost.showSnackbar(notice)
            onNoticeShown()
        }
    }
    Scaffold(
        topBar = {
            NooklinTopBar(title, onBack) {
                actions()
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost, Modifier.padding(bottom = LocalBottomOverlay.current)) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

/** Grey placeholder rows in the shape of the list that is coming; they pulse slowly. */
@Composable
private fun LoadingRows() {
    val alpha by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0.35f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "loadingAlpha"
    )
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f * alpha)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        repeat(8) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(color))
                Column(Modifier.padding(start = 12.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(color))
                    Box(Modifier.padding(top = 8.dp).fillMaxWidth(0.35f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(color))
                }
            }
        }
    }
}

/** Shows placeholder rows, the error with a retry button, or the loaded content. */
@Composable
fun <T> LoadBody(load: Load<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (load) {
        is Load.Loading -> LoadingRows()
        is Load.Failed -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(load.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Try again") }
        }
        is Load.Ready -> content(load.data)
    }
}

@Composable
fun EmptyText(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The one next step, when there is one
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(actionLabel) }
            }
        }
    }
}

/** The cover as a bitmap, kept in memory. Null while loading or when the song has none. */
@Composable
fun rememberCoverBitmap(coverId: String?, px: Int): Bitmap? {
    val context = LocalContext.current.applicationContext
    val key = coverId?.let { CoverCache.key(it, px) }
    val bitmap: Bitmap? by produceState(initialValue = key?.let { CoverCache.get(it) }, key) {
        if (coverId == null || key == null) return@produceState
        value = CoverCache.get(key) ?: ServerSession.get(context).cover(coverId, px)?.also { CoverCache.put(key, it) }
    }
    return bitmap
}

/** Cover from the server. Shows a note icon while loading or when there is no cover. */
@Composable
fun CoverArt(coverId: String?, size: Dp, modifier: Modifier = Modifier) {
    // Ask for a bit more than the screen needs, rounded to a step so the cache is reused
    val px = with(LocalDensity.current) { ((size.toPx() / 128f).toInt() + 1) * 128 }
    CoverImage(coverId, px, modifier.size(size), 10.dp)
}

/** The cover filling whatever size the modifier gives it. */
@Composable
fun CoverImage(coverId: String?, px: Int, modifier: Modifier, corner: Dp) {
    val bitmap = rememberCoverBitmap(coverId, px)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
    }
}

/** One line item: cover (or the [cover] given), two lines of text, optional heart. [onClick] null means the row is not tappable. */
@Composable
fun MediaRow(
    title: String,
    subtitle: String,
    coverId: String?,
    starred: Boolean?,
    onToggleStar: () -> Unit,
    onClick: (() -> Unit)?,
    showCover: Boolean = true,
    emphasized: Boolean = false,
    coverSize: Dp = 52.dp,
    cover: (@Composable () -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCover) {
            if (cover != null) cover() else CoverArt(coverId, coverSize)
            Box(Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (emphasized) FontWeight.Bold else null
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (starred != null) {
            IconButton(onClick = onToggleStar) {
                Icon(
                    if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (starred) "Remove from favorites" else "Add to favorites"
                )
            }
        }
        menu?.invoke()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

fun albumSubtitle(artist: String, year: Int, songCount: Int): String {
    val parts = mutableListOf<String>()
    if (artist.isNotEmpty()) parts += artist
    if (year > 0) parts += year.toString()
    if (songCount > 0) parts += if (songCount == 1) "1 song" else "$songCount songs"
    return parts.joinToString(" · ")
}

/** What the Play and Shuffle pill in the header does. [busy] shows a spinner while the songs are being collected. */
class PlayTarget(val busy: Boolean, val onPlay: () -> Unit, val onShuffle: () -> Unit)

/** Play and Shuffle as a pill in the header of a list screen, left of Refresh. Dimmed while there is nothing to play. */
@Composable
fun PlayShufflePill(target: PlayTarget?) {
    val enabled = target != null && !target.busy
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (target?.busy == true) {
                CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
            }
            IconButton(enabled = enabled, onClick = { target?.onPlay?.invoke() }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
            }
            IconButton(enabled = enabled, onClick = { target?.onShuffle?.invoke() }) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle")
            }
        }
    }
}

/** The most albums that Play and Shuffle collect songs from in the album and artist lists. */
const val PLAY_ALBUM_LIMIT = 50

/** The number of random songs that Shuffle asks the server for when it is not limited to a list. */
const val SHUFFLE_SONG_COUNT = 50

/** The tap on a song row: one function for every list, see [QueueActions.playSong]. */
@Composable
fun rememberSongTap(onPlay: (List<Song>, Int, Boolean) -> Unit): (List<Song>, Int) -> Unit {
    val queue = LocalQueueActions.current
    return { songs, index -> if (queue != null) queue.playSong(songs, index) else onPlay(songs, index, false) }
}
