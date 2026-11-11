package org.nooklin.music.ui.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The height of what floats above the bottom of the screen (the mini player and the bottom bar, with the navigation
 * bar). Screens that scroll leave this much room at the end of their content, so the last item can come out from
 * under the floating pills.
 */
val LocalBottomOverlay = compositionLocalOf<Dp> { 0.dp }
