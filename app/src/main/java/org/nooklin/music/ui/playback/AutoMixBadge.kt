package org.nooklin.music.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.music.playback.mix.MixMode

/** 0.0.155: what the AutoMix badge shows: on or off, the mode (when on) and the song it mixes into next (when on). */
data class AutoMixInfo(val on: Boolean, val mode: String?, val next: String?)

/** Null only when nothing is loaded. */
fun autoMixLine(state: PlayerState, autoMix: Boolean, modeId: String): AutoMixInfo? {
    if (!state.hasMedia) return null
    if (!autoMix) return AutoMixInfo(on = false, mode = null, next = null)
    val mode = MixMode.of(modeId)?.label
    val row = state.upcoming.firstOrNull()?.let { state.queue.getOrNull(it) }
    val next = row?.takeIf { it.title.isNotBlank() }?.let { if (it.artist.isBlank()) it.title else "${it.artist} - ${it.title}" }
    return AutoMixInfo(on = true, mode = mode, next = next?.let { "Next: $it" })
}

// A calm green for "on", the same on a light and a dark cover
private val ON_COLOR = Color(0xFF34D399)
private val OFF_COLOR = Color(0xFFEF4444)

@Composable
fun AutoMixBadge(
    info: AutoMixInfo,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    softColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    center: Boolean = false
) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(modifier, horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A filled dot when on, an empty ring when off
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .then(if (info.on) Modifier.background(ON_COLOR) else Modifier.background(OFF_COLOR))
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (info.on) "AutoMix On" + (info.mode?.let { "  ·  Mode: $it" } ?: "") else "AutoMix Off",
                color = if (info.on) color else softColor,
                fontSize = 12.sp,
                fontWeight = if (info.on) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.4.sp,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (info.next != null) {
            Text(
                info.next,
                color = softColor,
                fontSize = 12.sp,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
