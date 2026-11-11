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
    val play: (List<Song>) -> Unit,
    // A tap on a song: starts it, and with AutoMix on a new mix around that song
    val playSong: (List<Song>, Int) -> Unit
)

val LocalQueueActions = staticCompositionLocalOf<QueueActions?> { null }

/** Opens the screen of an artist. Given by the app, so the menu behind an album can offer "Go to artist". */
val LocalOpenArtist = staticCompositionLocalOf<((String) -> Unit)?> { null }
