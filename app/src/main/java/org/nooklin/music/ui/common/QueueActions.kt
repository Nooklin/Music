package org.nooklin.music.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import org.nooklin.music.data.library.Song

/**
 * Puts songs in the play queue. The app gives one instance to every screen, so the menus behind songs and albums need
 * no extra parameters. Null where nothing was given (previews).
 */
class QueueActions(
    val playNext: (List<Song>) -> Unit,
    val addToQueue: (List<Song>) -> Unit,
    val play: (List<Song>) -> Unit
)

val LocalQueueActions = staticCompositionLocalOf<QueueActions?> { null }
