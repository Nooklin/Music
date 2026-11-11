package org.nooklin.music.ui.library

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.data.library.Album
import org.nooklin.music.data.library.Song
import org.nooklin.music.data.library.StarKind
import org.nooklin.music.ui.common.LocalOpenArtist
import org.nooklin.music.ui.common.LocalQueueActions
import org.nooklin.music.ui.common.NooklinMenu

/**
 * The "..." behind an album, in groups: play, playlist and download, favourite, artist. The songs of the album are asked from
 * the server when a choice needs them. [button] draws the button that opens the menu; it gets the click to give.
 */
@Composable
fun AlbumMenu(
    vm: LibraryViewModel,
    album: Album,
    button: @Composable (onClick: () -> Unit) -> Unit = { onClick ->
        IconButton(onClick = onClick) { Icon(Icons.Filled.MoreHoriz, contentDescription = "More") }
    }
) {
    var open by remember { mutableStateOf(false) }
    var playlistIds by remember { mutableStateOf<List<String>?>(null) }
    val context = LocalContext.current
    val queue = LocalQueueActions.current
    val openArtist = LocalOpenArtist.current
    val downloads = remember { DownloadManager.get(context) }
    val starred by vm.starred.collectAsStateWithLifecycle()
    val isStarred = album.id in starred
    fun withSongs(action: (List<Song>) -> Unit) = vm.songsOfAlbums(listOf(album), 1, action)

    Box {
        button { open = true }
        NooklinMenu(expanded = open, onDismiss = { open = false }) {
            if (queue != null) {
                DropdownMenuItem(
                    text = { Text("Play") },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                    onClick = { open = false; withSongs { queue.play(it) } }
                )
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = { open = false; withSongs { queue.playNext(it); vm.say("Playing next") } }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = { open = false; withSongs { queue.addToQueue(it); vm.say("Added to queue") } }
                )
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                onClick = { open = false; withSongs { songs -> playlistIds = songs.map { it.id } } }
            )
            DropdownMenuItem(
                text = { Text("Download") },
                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                onClick = { open = false; withSongs { downloads.enqueue(it); vm.say("Added to downloads") } }
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                onClick = {
                    open = false
                    val text = if (album.artist.isEmpty()) album.name else "${album.name} - ${album.artist}"
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (isStarred) "Remove favourite" else "Favourite") },
                leadingIcon = {
                    Icon(if (isStarred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null)
                },
                onClick = { open = false; vm.toggleStar(StarKind.ALBUM, album.id) }
            )
            val artistId = album.artistId
            if (artistId != null && openArtist != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Go to artist") },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    onClick = { open = false; openArtist(artistId) }
                )
            }
        }
    }
    playlistIds?.let { ids -> AddToPlaylistDialog(vm, ids, onDismiss = { playlistIds = null }) }
}
