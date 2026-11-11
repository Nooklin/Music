package org.nooklin.music.ui.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import org.nooklin.music.ui.common.GlassPill
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.nooklin.music.playback.mix.MixMode
import org.nooklin.music.playback.mix.MixModes
import kotlin.math.max

/** The panel opens at the top right corner of the button it belongs to, over that button, and grows to the left and down. */
private val topRightOfAnchor = object : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, max(0, windowSize.width - popupContentSize.width))
        val y = anchorBounds.top.coerceIn(0, max(0, windowSize.height - popupContentSize.height))
        return IntOffset(x, y)
    }
}

private class ModeRow(val mode: MixMode, val subtitle: String)

/**
 * The AutoMix panel (long press on the AutoMix button): AutoMix on or off at the top, then one mode to pick. A mode applies at once
 * and closes the panel. Tempozone and Genres open their choices in the panel itself (an arrow shows that); genres are ticked and
 * applied with a button.
 */
@Composable
fun AutoMixPanel(
    autoMix: Boolean,
    activeMode: MixMode?,
    zone: Int,
    pickedGenres: Set<String>,
    genres: List<String>,
    onToggle: () -> Unit,
    onChoose: (MixMode, Int, Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var open by remember { mutableStateOf<MixMode?>(null) }
    var ticked by remember { mutableStateOf(pickedGenres) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    fun subtitle(mode: MixMode) = when (mode) {
        MixMode.MIXED -> "All downloaded songs, in a DJ order"
        MixMode.ENERGY -> "Fast songs, 120 bpm and up"
        MixMode.BUILD_UP -> "Energy rises from song to song"
        MixMode.CHILL -> "Slow, calm songs"
        MixMode.COOLDOWN -> "Energy falls from song to song"
        MixMode.TEMPOZONE -> if (activeMode == MixMode.TEMPOZONE) MixModes.zoneLabel(zone) else "Pick a tempo range"
        MixMode.HARMONIC -> "Songs in keys that fit together"
        MixMode.GENRES -> if (activeMode == MixMode.GENRES && pickedGenres.isNotEmpty()) pickedGenres.sorted().joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } } else "Pick one or more genres"
    }
    val groups = listOf(
        "General" to listOf(MixMode.MIXED),
        "Energy" to listOf(MixMode.ENERGY, MixMode.BUILD_UP),
        "Calm" to listOf(MixMode.CHILL, MixMode.COOLDOWN),
        "Match" to listOf(MixMode.TEMPOZONE, MixMode.HARMONIC),
        "Filter" to listOf(MixMode.GENRES)
    )

    Popup(popupPositionProvider = topRightOfAnchor, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visible = shown,
            enter = scaleIn(transformOrigin = TransformOrigin(1f, 0f), initialScale = 0.3f, animationSpec = tween(180)) + fadeIn(tween(120))
        ) {
            Surface(
                // 0.0.138: the corners of the rows (16 dp), the colour of the other popups (no purple), the text colour of the theme
                shape = RoundedCornerShape(16.dp),
                color = GlassPill.adaptivePopupBackground,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp,
                modifier = Modifier.width(300.dp)
            ) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(24.dp))
                        Text("AutoMix", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Switch(checked = autoMix, onCheckedChange = { onToggle() })
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                        groups.forEachIndexed { gi, (title, modes) ->
                            if (gi > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                            Text(
                                title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 2.dp)
                            )
                            modes.forEach { mode ->
                                val expandable = mode == MixMode.TEMPOZONE || mode == MixMode.GENRES
                                val active = autoMix && activeMode == mode
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            if (expandable) {
                                                if (mode == MixMode.GENRES) ticked = pickedGenres
                                                open = if (open == mode) null else mode
                                            } else {
                                                onChoose(mode, zone, pickedGenres)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(mode.label, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            subtitle(mode),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (active && !expandable) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                    if (expandable) {
                                        if (active) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
                                        Icon(if (open == mode) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (open == mode) "Close" else "Open")
                                    }
                                }
                                if (open == mode && mode == MixMode.TEMPOZONE) {
                                    for (z in MixModes.ZONES.indices) {
                                        val chosen = autoMix && activeMode == MixMode.TEMPOZONE && zone == z
                                        Row(
                                            Modifier.fillMaxWidth().clickable { onChoose(MixMode.TEMPOZONE, z, pickedGenres) }.padding(start = 36.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(MixModes.zoneLabel(z), modifier = Modifier.weight(1f))
                                            if (chosen) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                if (open == mode && mode == MixMode.GENRES) {
                                    if (genres.isEmpty()) {
                                        Text(
                                            "No genres in the downloaded songs.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 36.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
                                        )
                                    } else {
                                        genres.forEach { g ->
                                            Row(
                                                Modifier.fillMaxWidth().clickable { ticked = if (g in ticked) ticked - g else ticked + g }.padding(start = 28.dp, end = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(checked = g in ticked, onCheckedChange = { ticked = if (it) ticked + g else ticked - g })
                                                Text(g.replaceFirstChar { it.uppercase() })
                                            }
                                        }
                                        Button(
                                            onClick = { onChoose(MixMode.GENRES, zone, ticked) },
                                            enabled = ticked.isNotEmpty(),
                                            modifier = Modifier.align(Alignment.End).padding(end = 20.dp, top = 4.dp, bottom = 4.dp)
                                        ) { Text("Apply") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
