package org.nooklin.music.ui.home

import org.nooklin.music.ui.common.LocalBottomOverlay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.music.data.library.Album
import org.nooklin.music.data.library.AlbumListKind
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.library.Artist
import org.nooklin.music.data.local.HomeSection
import org.nooklin.music.data.local.HomeSectionSetting
import org.nooklin.music.ui.library.CoverArt
import org.nooklin.music.data.library.Playlist
import org.nooklin.music.playback.taste.mixOf
import org.nooklin.music.ui.library.CoverImage
import org.nooklin.music.ui.library.LibraryViewModel
import org.nooklin.music.ui.library.Load
import org.nooklin.music.ui.playback.PlayerState

private fun HomeSection.albumKind(): AlbumListKind? = when (this) {
    HomeSection.RECENT -> AlbumListKind.RECENT
    HomeSection.NEWEST -> AlbumListKind.NEWEST
    HomeSection.FREQUENT -> AlbumListKind.FREQUENT
    HomeSection.STARRED -> AlbumListKind.STARRED
    HomeSection.RANDOM -> AlbumListKind.RANDOM
    else -> null
}

/**
 * The start screen: the server name as title, what plays next, and rows of albums. Each header opens the full list.
 */
@Composable
fun HomeScreen(
    library: LibraryViewModel,
    sections: List<HomeSectionSetting>?,
    player: PlayerState,
    serverName: String,
    onJump: (Int) -> Unit,
    onOpenUpNext: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenList: (AlbumListKind) -> Unit,
    onOpenPlayed: (Boolean) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlaySongs: (List<Song>, Int, Boolean) -> Unit,
    onStartRadio: (Artist) -> Unit,
    discoveryOn: Boolean,
    autoMixOn: Boolean,
    autoMixMode: String,
    autoMixBusy: Boolean,
    onStartAutoMix: () -> Unit,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit
) {
    val played by library.played.collectAsStateWithLifecycle()
    val playlistLoad by library.playlists.collectAsStateWithLifecycle()
    // The weekly mixes are playlists on the server; the comment of a playlist tells which mix it is
    val mixLists = (playlistLoad as? Load.Ready<List<Playlist>>)?.data.orEmpty()
        .mapNotNull { playlist -> mixOf(playlist)?.let { it to playlist } }.toMap()
    val lists by library.albumLists.collectAsStateWithLifecycle()
    // Until the saved order is read, nothing is drawn, so the blocks do not jump around
    val shown = sections.orEmpty().filter { it.enabled }.map { it.section }
    LaunchedEffect(shown) {
        if (HomeSection.DISCOVERY in shown || HomeSection.MIXES in shown) {
            library.loadPlaylists()
            library.loadMixes()
        }
        if (HomeSection.RADIO in shown) {
            library.loadArtists()
            library.loadPlayed(true)
        }
        shown.forEach { section ->
            section.albumKind()?.let { library.loadAlbums(it) }
            if (section == HomeSection.TOP_PLAYED) library.loadPlayed(true)
            if (section == HomeSection.HISTORY) library.loadPlayed(false)
        }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                serverName.ifBlank { "Nooklin Music" },
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            AutoMixCard(autoMixOn, autoMixMode, autoMixBusy, onStartAutoMix)

            shown.forEach { section ->
                val kind = section.albumKind()
                if (section == HomeSection.DISCOVERY) {
                    DiscoveryRow(
                        discoveryOn = discoveryOn,
                        playlists = mixLists,
                        onDiscovery = if (discoveryOn) onStopDiscovery else onStartDiscovery,
                        onOpen = onOpenPlaylist
                    )
                } else if (section == HomeSection.MIXES) {
                    MixesRow(mixLists, onOpenPlaylist)
                } else if (section == HomeSection.RADIO) {
                    val artistLoad by library.artists.collectAsStateWithLifecycle()
                    val allArtists = (artistLoad as? Load.Ready<List<Artist>>)?.data.orEmpty()
                    // The artists you play most; without a play history a few artists at random
                    val topSongs = (played[true] as? Load.Ready<List<Song>>)?.data.orEmpty()
                    val byId = allArtists.associateBy { it.id }
                    val mine = topSongs.mapNotNull { it.artistId }.distinct().mapNotNull { byId[it] }.take(10)
                    val radioArtists = mine.ifEmpty { remember(allArtists) { allArtists.shuffled().take(10) } }
                    RadioRow(radioArtists, onStartRadio)
                } else if (section == HomeSection.TOP_PLAYED || section == HomeSection.HISTORY) {
                    val byCount = section == HomeSection.TOP_PLAYED
                    SectionHeader(section.label, onClick = { onOpenPlayed(byCount) })
                    when (val load = played[byCount] ?: Load.Loading) {
                        is Load.Loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                        is Load.Failed -> Column(Modifier.padding(horizontal = 24.dp)) {
                            Text(load.message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { library.loadPlayed(byCount, force = true) }) { Text("Try again") }
                        }
                        is Load.Ready -> if (load.data.isEmpty()) {
                            Text(
                                "Nothing here yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        } else {
                            SongGrid(load.data.take(18)) { index -> onPlaySongs(load.data, index, false) }
                        }
                    }
                } else if (kind == null) {
                    if (player.upcoming.isNotEmpty()) {
                        SectionHeader(section.label, onOpenUpNext)
                        UpNextGrid(player, onJump)
                    }
                } else {
                    // Newest and random albums can start playing from the header, in the order shown
                    val playHere: (() -> Unit)? =
                        if (kind == AlbumListKind.NEWEST || kind == AlbumListKind.RANDOM) {
                            {
                                val shownAlbums = (lists[kind] as? Load.Ready<List<Album>>)?.data.orEmpty()
                                library.songsOfAlbums(shownAlbums.take(20)) { songs -> onPlaySongs(songs, 0, false) }
                            }
                        } else {
                            null
                        }
                    SectionHeader(section.label, { onOpenList(kind) }, playHere)
                    AlbumRow(
                        load = lists[kind] ?: Load.Loading,
                        onRetry = { library.loadAlbums(kind, force = true) },
                        onOpenAlbum = onOpenAlbum
                    )
                }
            }
            Box(Modifier.height(16.dp + LocalBottomOverlay.current))
        }
    }
}

@Composable
private fun SectionHeader(title: String, onClick: () -> Unit, onPlay: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
        if (onPlay != null) {
            Box(Modifier.weight(1f))
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            ) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play these albums") }
        }
    }
}

/** Up to eighteen coming songs, three rows high, scrolling sideways. A tap plays that song. */
@Composable
private fun UpNextGrid(player: PlayerState, onJump: (Int) -> Unit) {
    val columns = player.upcoming.take(18).chunked(3)
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)) {
        items(columns) { column ->
            Column(Modifier.fillParentMaxWidth(0.85f).padding(end = 16.dp)) {
                column.forEach { index ->
                    val row = player.queue.getOrNull(index)
                    if (row != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onJump(index) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArt(row.coverId, 52.dp)
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    row.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Up to eighteen songs, three rows high, scrolling sideways. A tap plays the list from that song. */
@Composable
private fun SongGrid(songs: List<Song>, onPlay: (Int) -> Unit) {
    val columns = songs.withIndex().toList().chunked(3)
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)) {
        items(columns) { column ->
            Column(Modifier.fillParentMaxWidth(0.85f).padding(end = 16.dp)) {
                column.forEach { (index, song) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPlay(index) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverArt(song.coverArt, 52.dp)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                song.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(load: Load<List<Album>>, onRetry: () -> Unit, onOpenAlbum: (String) -> Unit) {
    when (load) {
        is Load.Loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp))
        }
        is Load.Failed -> Column(Modifier.padding(horizontal = 24.dp)) {
            Text(load.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Try again") }
        }
        is Load.Ready -> if (load.data.isEmpty()) {
            Text(
                "Nothing here yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(load.data.take(20), key = { it.id }) { album ->
                    Column(Modifier.width(150.dp).clickable { onOpenAlbum(album.id) }) {
                        CoverImage(album.coverArt, 400, Modifier.size(150.dp), 12.dp)
                        Text(
                            album.name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            album.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
