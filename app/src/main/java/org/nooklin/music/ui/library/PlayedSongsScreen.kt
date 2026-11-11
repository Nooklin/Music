package org.nooklin.music.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.music.data.library.Song
import org.nooklin.music.ui.common.LocalBottomOverlay

/** The songs played most ([byCount]) or most recently, read from Navidrome's own song list. A tap plays from there. */
@Composable
fun PlayedSongsScreen(
    byCount: Boolean,
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    val lists by vm.played.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    LaunchedEffect(byCount) { vm.loadPlayed(byCount) }

    LibraryScaffold(
        if (byCount) "Top played" else "Song history",
        onBack,
        { vm.loadPlayed(byCount, force = true) },
        notice,
        vm::noticeShown
    ) {
        LoadBody(lists[byCount] ?: Load.Loading, onRetry = { vm.loadPlayed(byCount, force = true) }) { songs ->
            if (songs.isEmpty()) {
                EmptyText("Nothing here yet. Navidrome fills this list from what has been played.", "Refresh") { vm.loadPlayed(byCount, force = true) }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)) {
                    itemsIndexed(songs, key = { index, _ -> index }) { index, song ->
                        MediaRow(
                            title = song.title,
                            subtitle = listOf(song.artist, if (byCount) "${song.playCount} plays" else "").filter { it.isNotEmpty() }
                                .joinToString(" · "),
                            coverId = song.coverArt,
                            starred = null,
                            onToggleStar = {},
                            onClick = { onPlay(songs, index, false) }
                        )
                    }
                }
            }
        }
    }
}
