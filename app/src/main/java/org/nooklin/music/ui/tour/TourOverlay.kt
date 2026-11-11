package org.nooklin.music.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.nooklin.music.ui.common.GlassPill

/** What is on the screen right now. The tour moves on when it matches what a step waits for. */
data class TourContext(
    val settingsOpen: Boolean,
    val serverOpen: Boolean,
    val libraryOpen: Boolean,
    val playerOpen: Boolean,
    val serverSaved: Boolean
)

/** What the tour can do to the app: go back to Home, close the player, or end. */
class TourActions(
    val showHome: () -> Unit,
    val finish: () -> Unit
)

private enum class Place { TOP, CENTER, BOTTOM }

private class TourStep(
    val icon: ImageVector,
    val title: String,
    // A short line, or a list of points, or both. [footer] is a last line in smaller text.
    val text: String = "",
    val points: List<String> = emptyList(),
    val footer: String? = null,
    // The button that pulses. The step waits until the person taps it: [advanceWhen] says when the tap has worked.
    val target: String? = null,
    val advanceWhen: ((TourContext) -> Boolean)? = null,
    // Shown under the text for a step that waits for the person
    val hint: String? = null,
    val place: Place = Place.CENTER,
    val scrim: Float = 0.35f,
    // Done when Next is tapped, for a step that has Next
    val onNext: ((TourActions) -> Unit)? = null,
    val needsPlayer: Boolean = false,
    // Only when there is no server yet: the steps that make the person add one
    val needsNoServer: Boolean = false,
    // No Skip and no Back gesture: the tour does not go on without it
    val mandatory: Boolean = false
)

private const val TAP_HINT = "Tap the pulsing button to continue."

private val STEPS = listOf(
    TourStep(
        Icons.Filled.MusicNote, "Welcome to Nooklin Music",
        points = listOf(
            "Plays the music of your own Navidrome server",
            "AutoMix mixes songs into each other on the beat",
            "Plays on AirPlay speakers",
            "Downloads for offline listening",
            "No Google and no tracking"
        ),
        footer = "The app shows nothing without your server, so the tour starts with that. It will take about 1 minute.",
        scrim = 0.6f
    ),
    TourStep(
        Icons.Filled.Settings, "Settings",
        "Start by connecting your server in the Settings.",
        target = "tab_SETTINGS", advanceWhen = { it.settingsOpen }, hint = TAP_HINT,
        needsNoServer = true, mandatory = true
    ),
    TourStep(
        Icons.Filled.Dns, "Your server",
        "Open Server to connect.",
        target = "settings_server", advanceWhen = { it.serverOpen }, hint = TAP_HINT, place = Place.BOTTOM,
        needsNoServer = true, mandatory = true
    ),
    TourStep(
        Icons.Filled.Dns, "Connect",
        points = listOf(
            "The address, username and password of your Navidrome server",
            "Test the connection, then tap Save",
            "Your password stays encrypted on this phone"
        ),
        advanceWhen = { it.serverSaved }, hint = "Tap Save to continue.", place = Place.BOTTOM,
        needsNoServer = true, mandatory = true
    ),
    TourStep(
        Icons.Filled.Dns, "Server saved",
        "Your server is connected. Now a look at the rest of the app.",
        place = Place.BOTTOM, onNext = { it.showHome() }, needsNoServer = true
    ),
    TourStep(
        Icons.Filled.LibraryMusic, "Your library",
        "Everything on your server is in the Library.",
        target = "tab_LIBRARY", advanceWhen = { it.libraryOpen }, hint = TAP_HINT
    ),
    TourStep(
        Icons.Filled.LibraryMusic, "Find your music",
        points = listOf(
            "Artists, albums, songs, favourites, genres and playlists",
            "Tap an album to open it, tap a song to play it",
            "The \"...\" behind an album has more choices",
            "Downloads are here too"
        ),
        place = Place.BOTTOM, onNext = { it.showHome() }
    ),
    TourStep(
        Icons.Filled.GraphicEq, "AutoMix",
        points = listOf(
            "Songs are mixed into each other on the beat",
            "Start a mix from the AutoMix card on Home",
            "With AutoMix on, a tap on a song makes a new mix around it",
            "Radio Stations on Home: a tap on an artist plays only that artist",
            "It uses downloaded songs that are scanned in Settings > AutoMix"
        )
    ),
    TourStep(
        Icons.Filled.PlayCircle, "The player",
        "Tap the player to open it.",
        target = "mini_player", advanceWhen = { it.playerOpen }, hint = TAP_HINT, needsPlayer = true
    ),
    TourStep(
        Icons.Filled.PlayCircle, "Everything in one place",
        points = listOf(
            "The queue, with Shuffle, Repeat, AutoPlay and AutoMix",
            "Lyrics, star ratings and favourites",
            "Speed and sleep timer under the \"...\"",
            "Play on an AirPlay speaker, with the sound about two seconds later"
        ),
        onNext = { it.showHome() }, needsPlayer = true
    ),
    TourStep(
        Icons.Filled.Download, "Offline",
        points = listOf(
            "Download songs, albums or playlists",
            "Set the rules in Settings > Downloads"
        )
    ),
    TourStep(
        Icons.Filled.MusicNote, "That is all",
        points = listOf(
            "Appearance sets the theme, font and colours",
            "Backup keeps your settings safe",
            "Settings > Tour shows this tour again"
        ),
        scrim = 0.6f
    )
)

/**
 * The tour of the first start. A card explains one step at a time. For most steps a button pulses and the person has
 * to tap it: the tour then moves on by itself when the screen it opens is there. [actions] ends the tour.
 */
@Composable
fun TourOverlay(context: TourContext, playerAvailable: Boolean, actions: TourActions) {
    // Whether there was a server when the tour began; the steps that add one are left out when there was
    val hadServer = remember { context.serverSaved }
    val steps = remember(playerAvailable) { STEPS.filter { (playerAvailable || !it.needsPlayer) && (!hadServer || !it.needsNoServer) } }
    var storedIndex by rememberSaveable { mutableStateOf(0) }
    val index = storedIndex.coerceIn(0, steps.lastIndex)
    val step = steps[index]
    val last = index == steps.lastIndex
    val waiting = step.advanceWhen != null

    // Back ends the tour; the screens stay as they are
    // Without a server the whole tour is mandatory, the first step too
    val mandatory = step.mandatory || (index == 0 && !hadServer)
    BackHandler { if (!mandatory) actions.finish() }

    // A step that waits for the person moves on as soon as the screen is what it waited for
    LaunchedEffect(index, context) {
        if (step.advanceWhen?.invoke(context) == true && !last) storedIndex = index + 1
    }

    val pulse by rememberInfiniteTransition(label = "tour pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "tour pulse value"
    )
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // The place of the pulsing button, in the coordinates of this layer
    val hole: Rect? = step.target?.let { TourTargets.bounds[it] }
        ?.translate(-origin.x, -origin.y)
        ?.inflate(with(density) { 6.dp.toPx() })

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                size = Offset(it.size.width.toFloat(), it.size.height.toFloat())
            }
    ) {
        // The dark layer with a hole at the button, and a ring that grows out of the hole
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = step.scrim))
            if (hole != null) {
                val progress = pulse
                val corner = CornerRadius(minOf(hole.width, hole.height) / 2f)
                drawRoundRect(Color.Black, hole.topLeft, hole.size, corner, blendMode = BlendMode.Clear)
                val ring = hole.inflate(progress * 16.dp.toPx())
                drawRoundRect(
                    Color.White.copy(alpha = 1f - progress),
                    ring.topLeft,
                    ring.size,
                    CornerRadius(minOf(ring.width, ring.height) / 2f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        // Everything is blocked, except the hole: the person taps the real button there
        if (hole == null) {
            Blocker(0f, 0f, size.x, size.y)
        } else {
            Blocker(0f, 0f, size.x, hole.top)
            Blocker(0f, hole.bottom, size.x, size.y - hole.bottom)
            Blocker(0f, hole.top, hole.left, hole.height)
            Blocker(hole.right, hole.top, size.x - hole.right, hole.height)
        }

        val alignment = when (step.place) {
            Place.TOP -> Alignment.TopCenter
            Place.CENTER -> Alignment.Center
            Place.BOTTOM -> Alignment.BottomCenter
        }
        val edge = when (step.place) {
            Place.TOP -> Modifier.statusBarsPadding().padding(top = 16.dp)
            Place.CENTER -> Modifier
            Place.BOTTOM -> Modifier.navigationBarsPadding().padding(bottom = 24.dp)
        }
        Column(
            Modifier
                .align(alignment)
                .then(edge)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(GlassPill.adaptivePopupBackground)
                .border(GlassPill.adaptiveBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(step.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                step.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            if (step.text.isNotEmpty()) {
                Text(
                    step.text,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (step.points.isNotEmpty()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    step.points.forEach { point ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("\u2022", fontSize = 17.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp))
                            Text(point, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (step.footer != null) {
                Spacer(Modifier.height(14.dp))
                Text(step.footer, fontSize = 14.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (step.hint != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(step.hint, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                steps.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == index) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (i == index) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f))
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (last || mandatory) Spacer(Modifier.size(1.dp)) else TextButton(onClick = actions.finish) { Text("Skip") }
                // A step that waits for a tap has no Next: the tap is the way on
                if (!waiting) {
                    Button(onClick = {
                        step.onNext?.invoke(actions)
                        if (last) actions.finish() else storedIndex = index + 1
                    }) { Text(if (last) "Done" else "Next") }
                }
            }
        }
    }
}

/** A layer that takes every tap, so the screen under it does not react. */
@Composable
private fun Blocker(x: Float, y: Float, width: Float, height: Float) {
    if (width <= 0f || height <= 0f) return
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    )
}
