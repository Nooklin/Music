package org.nooklin.music.ui.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Lays the player over the screen below it. Opening slides it up, closing slides it down. Dragging down moves the whole
 * player with the finger, with rounded top corners, and shows the screen below. Let go past a third of the height and
 * it slides out and closes; before that it springs back.
 *
 * [content] gets the callbacks for a drag: the distance moved since the last call, and the end of the drag.
 */
@Composable
fun PlayerSheet(
    open: Boolean,
    onClosed: () -> Unit,
    content: @Composable (onDrag: (Float) -> Unit, onDragEnd: () -> Unit) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        val offset = remember { Animatable(if (open) 0f else height) }
        val scope = rememberCoroutineScope()

        // Opening and closing from outside (the mini player, the back button) use the same slide
        LaunchedEffect(open) {
            offset.animateTo(if (open) 0f else height)
        }

        // 0.0.148: only these two yes/no values are read in composition, so a drag no longer recomposes the whole sheet each frame
        val visible by remember(height) { androidx.compose.runtime.derivedStateOf { offset.value < height } }
        val moving by remember { androidx.compose.runtime.derivedStateOf { offset.value > 1f } }
        if (visible) {
            val shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
            val rounded = if (moving) shape else RoundedCornerShape(0.dp)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = offset.value }
                    .shadow(if (moving) 16.dp else 0.dp, rounded)
                    .clip(rounded)
            ) {
                content(
                    { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, height)) } },
                    {
                        scope.launch {
                            if (offset.value > height / 3f) {
                                offset.animateTo(height)
                                onClosed()
                            } else {
                                offset.animateTo(0f)
                            }
                        }
                    }
                )
            }
        }
    }
}
