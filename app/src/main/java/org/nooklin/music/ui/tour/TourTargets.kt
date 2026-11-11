package org.nooklin.music.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** Where the buttons that the tour explains are on the screen, so the tour can make them pulse. */
object TourTargets {
    val bounds = mutableStateMapOf<String, Rect>()
}

/** Marks a button as a place the tour can point at. The place is forgotten when the button leaves the screen. */
@Composable
fun Modifier.tourTarget(key: String): Modifier {
    DisposableEffect(key) { onDispose { TourTargets.bounds.remove(key) } }
    return this.onGloballyPositioned { TourTargets.bounds[key] = it.boundsInRoot() }
}
