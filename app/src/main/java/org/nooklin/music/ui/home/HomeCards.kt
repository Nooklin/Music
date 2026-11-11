package org.nooklin.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.music.data.library.Playlist
import org.nooklin.music.playback.taste.MixId
import org.nooklin.music.ui.library.PlaylistCover
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.GraphicEq
import org.nooklin.music.playback.mix.MixMode

// 0.0.152: fixed colours (the app accent can be white), white text on both reads on a light and a dark theme
private val AUTOMIX_BRUSH = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF0891B2)))
private val DISCOVERY_BRUSH = Brush.linearGradient(listOf(Color(0xFFF97316), Color(0xFFDB2777)))

private val TOP_ROW = listOf(MixId.FAVOURITES, MixId.FEATURING, MixId.NEW_RELEASE)
private val MADE_FOR_YOU = listOf(MixId.HEAVY, MixId.ESSENTIALS, MixId.GET_UP, MixId.CHILL, MixId.NEW_MUSIC)

/** The top row of Home: the Discovery Station and the weekly playlists that are made for you. */
@Composable
fun DiscoveryRow(
    discoveryOn: Boolean,
    playlists: Map<MixId, Playlist>,
    onDiscovery: () -> Unit,
    onOpen: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        item {
            Card("Discovery Station", if (discoveryOn) "On, tap to stop" else "Songs you have not played", onDiscovery) {
                Box(
                    Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)).background(DISCOVERY_BRUSH),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Explore, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
            }
        }
        for (id in TOP_ROW) {
            val playlist = playlists[id] ?: continue
            item(key = id.name) { PlaylistCard(playlist) { onOpen(playlist.id) } }
        }
    }
}

/** "Playlists made for you": the playlists that are made again every week. */
@Composable
fun MixesRow(playlists: Map<MixId, Playlist>, onOpen: (String) -> Unit) {
    val shown = MADE_FOR_YOU.mapNotNull { playlists[it] }
    if (shown.isEmpty()) return
    Text(
        "Playlists made for you",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (playlist in shown) {
            item(key = playlist.id) { PlaylistCard(playlist) { onOpen(playlist.id) } }
        }
    }
}

// The cover comes from the server, the line under the title from the comment (the artist, the song) or the song count
@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    val count = if (playlist.songCount == 1) "1 song" else "${playlist.songCount} songs"
    Card(playlist.name, playlist.comment.substringAfter('|', "").ifEmpty { count }, onClick) {
        PlaylistCover(playlist, Modifier.size(150.dp), 12.dp)
    }
}

/** A square picture with the title under it and a line of at most two rows, so the text is always whole. */
@Composable
private fun Card(title: String, line: String, onClick: () -> Unit, picture: @Composable () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick)) {
        picture()
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            line,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * 0.0.149: one tap starts AutoMix on your downloaded songs, in the mode you used last (Mixed the first time).
 * The AutoMix button on the Now playing screen stays the place to switch it off and to pick a mode.
 */
@Composable
fun AutoMixCard(autoMixOn: Boolean, modeId: String, busy: Boolean, onStart: () -> Unit) {
    val mode = MixMode.of(modeId)?.label
    Row(
        Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AUTOMIX_BRUSH)
            .clickable(enabled = !busy, onClick = onStart)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text("AutoMix", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
            Text(
                if (busy) "Making your mix..."
                else if (autoMixOn) "On" + (mode?.let { ", $it" } ?: "") + ". Tap for a new mix"
                else "Your downloaded songs, mixed into one flow",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
