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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.music.data.library.ArtistDetail
import org.nooklin.music.data.library.Favorites
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.library.StarKind
import org.nooklin.music.ui.common.LocalBottomOverlay
import org.nooklin.music.ui.common.NooklinMenu
import org.nooklin.music.ui.playback.coverBackground

/**
 * An artist: a round picture, the name and the numbers, a row with Shuffle, Play and the favourite heart, Radio, the
 * biography, the favourite songs and the albums. The top of the screen takes a soft colour from the picture.
 */
@Composable
fun ArtistScreen(
    artistId: String,
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    val songTap = rememberSongTap(onPlay)
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val bio by vm.bio.collectAsStateWithLifecycle()
    val gathering by vm.gathering.collectAsStateWithLifecycle()
    var bioOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(artistId) { vm.loadBio(artistId) }
    LaunchedEffect(Unit) { vm.openFavorites() }
    val favSongs = (favorites as? Load.Ready<Favorites>)?.data?.songs.orEmpty().filter { it.artistId == artistId }
    val state by vm.artistDetail.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    LaunchedEffect(artistId) { vm.loadArtist(artistId) }

    // A stale answer for another artist must not flash by while this one loads
    val load = if (state.first == artistId) state.second else Load.Loading
    val detail = (load as? Load.Ready<ArtistDetail>)?.data

    val bitmap = rememberCoverBitmap(detail?.artist?.coverArt, 256)
    val tint by produceState<Color?>(null, bitmap) { value = bitmap?.let { coverBackground(it) } }
    val tintAnimated by animateColorAsState((tint ?: Color.Transparent).copy(alpha = if (tint != null) 0.45f else 0f), tween(400), label = "artistTint")
    val screen = MaterialTheme.colorScheme.background

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice!!)
            vm.noticeShown()
        }
    }

    Scaffold(
        containerColor = screen,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomOverlay.current)) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(tintAnimated, Color.Transparent), endY = 1200f))
                .padding(padding)
        ) {
            LoadBody(load, onRetry = { vm.loadArtist(artistId, force = true) }) { loaded ->
                val artist = loaded.artist
                val albums = loaded.albums
                val songCount = albums.sumOf { it.songCount }
                val playAll = { shuffle: Boolean ->
                    if (albums.size > PLAY_ALBUM_LIMIT) vm.say("Playing the songs of the first $PLAY_ALBUM_LIMIT albums")
                    vm.songsOfAlbums(albums, PLAY_ALBUM_LIMIT) { onPlay(it, 0, shuffle) }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 72.dp, bottom = LocalBottomOverlay.current)) {
                    item(key = "artist-head") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CoverImage(artist.coverArt, 512, Modifier.size(200.dp), 100.dp)
                            Spacer(Modifier.height(16.dp))
                            Text(artist.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val numbers = listOf(
                                "${albums.size} ${if (albums.size == 1) "album" else "albums"}",
                                if (songCount > 0) "$songCount ${if (songCount == 1) "song" else "songs"}" else ""
                            ).filter { it.isNotEmpty() }.joinToString(" · ")
                            Text(numbers, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(20.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                ArtistRoundButton(enabled = albums.isNotEmpty() && !gathering, onClick = { playAll(true) }) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle")
                                }
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (albums.isNotEmpty() && !gathering) 1f else 0.4f))
                                        .clickable(enabled = albums.isNotEmpty() && !gathering) { playAll(false) },
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                    Text("Play", color = MaterialTheme.colorScheme.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                }
                                val isStarred = artist.id in starred
                                ArtistRoundButton(onClick = { vm.toggleStar(StarKind.ARTIST, artist.id) }) {
                                    Icon(
                                        if (isStarred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = if (isStarred) "Remove from favorites" else "Add to favorites"
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Start radio",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { vm.startRadio(artistId, null) { onPlay(it, 0, false) } }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            val text = if (bio.first == artistId) bio.second else ""
                            if (text.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        .clickable { bioOpen = !bioOpen }
                                        .padding(16.dp)
                                ) {
                                    Text("About", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text(
                                        text,
                                        maxLines = if (bioOpen) Int.MAX_VALUE else 4,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                    Text(
                                        if (bioOpen) "Less" else "More",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (albums.isEmpty()) {
                        item(key = "no-albums") { EmptyTextInline("No albums for this artist.") }
                    }
                    if (favSongs.isNotEmpty()) {
                        item(key = "fav-title") { ArtistSectionTitle("Favorite songs") }
                        itemsIndexed(favSongs, key = { _, song -> "fav-" + song.id }) { index, song ->
                            MediaRow(
                                title = song.title,
                                subtitle = song.album,
                                coverId = song.coverArt,
                                starred = song.id in starred,
                                onToggleStar = { vm.toggleStar(StarKind.SONG, song.id) },
                                onClick = { songTap(favSongs, index) },
                                menu = { SongMenu(vm, song.id, song = song) }
                            )
                        }
                    }
                    if (albums.isNotEmpty()) {
                        item(key = "albums-title") { ArtistSectionTitle("Albums") }
                        items(albums, key = { it.id }) { album ->
                            MediaRow(
                                title = album.name,
                                subtitle = albumSubtitle("", album.year, album.songCount),
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
            // The back button and the menu float over the content
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtistRoundButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                if (detail != null) {
                    Box {
                        ArtistRoundButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreHoriz, contentDescription = "More") }
                        NooklinMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; vm.loadArtist(artistId, force = true) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistSectionTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun EmptyTextInline(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
}

/** A round 56 dp button with a soft circle behind it. */
@Composable
private fun ArtistRoundButton(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.12f else 0.06f))
    ) { content() }
}
