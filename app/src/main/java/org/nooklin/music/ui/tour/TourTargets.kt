package org.nooklin.music.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** Where the buttons that the tour explains are on the screen, so the tour can make them pulse. */
object TourTargets {
    val bounds = mutableStateMapOf<String, Rect>()

    /** Height in pixels of the tour card while it sits over a screen the person works in, otherwise 0. That screen leaves this much room at its end so it can scroll clear of the card. */
    var coveredBottomPx by mutableIntStateOf(0)
}

/** Marks a button as a place the tour can point at. The place is forgotten when the button leaves the screen. */
@Composable
fun Modifier.tourTarget(key: String): Modifier {
    DisposableEffect(key) { onDispose { TourTargets.bounds.remove(key) } }
    return this.onGloballyPositioned { TourTargets.bounds[key] = it.boundsInRoot() }
}
