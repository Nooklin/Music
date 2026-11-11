package org.nooklin.music.ui.playback

import android.app.Activity
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import org.nooklin.music.playback.mix.MixMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import org.nooklin.music.data.download.DownloadManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import org.nooklin.music.data.library.StarKind
import org.nooklin.music.data.library.Lyrics
import org.nooklin.music.data.playback.PlaybackSettingsValues
import org.nooklin.music.ui.common.GlassPill
import org.nooklin.music.ui.common.NooklinMenu
import org.nooklin.music.ui.library.AddToPlaylistDialog
import org.nooklin.music.ui.library.CoverImage
import org.nooklin.music.ui.library.Load
import org.nooklin.music.ui.library.LibraryViewModel
import org.nooklin.music.ui.library.rememberCoverBitmap
import org.nooklin.music.ui.common.AutoMixNoticeDialog

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private const val COVER_PX = 768

private enum class Sheet { NONE, SPEED, SLEEP }

/** What fills the middle of the player: the cover, the song text or the queue. */
private enum class Panel { COVER, LYRICS, QUEUE }

/**
 * The player screen. The background takes a color from the cover, so text and icons are white on it. Without a cover
 * color the theme colors are used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    vm: PlaybackViewModel,
    library: LibraryViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val starred by library.starred.collectAsStateWithLifecycle()
    val ratings by library.ratings.collectAsStateWithLifecycle()
    val notice by library.notice.collectAsStateWithLifecycle()

    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var showAirPlay by remember { mutableStateOf(false) }
    val airPlayOn = org.nooklin.music.airplay.AirPlayController.get(LocalContext.current).state
        .collectAsState().value.active
    var addToPlaylist by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.COVER) }
    val showLyrics = panel == Panel.LYRICS
    val lyrics by library.lyrics.collectAsStateWithLifecycle()

    // Favorite state and rating of the song that plays now
    LaunchedEffect(state.mediaId) {
        if (state.mediaId.isNotEmpty()) library.loadSong(state.mediaId)
    }

    LaunchedEffect(showLyrics, state.mediaId) {
        if (showLyrics && state.mediaId.isNotEmpty()) library.loadLyrics(state.mediaId, state.artist, state.title)
    }

    val bitmap = rememberCoverBitmap(state.coverId, COVER_PX)
    val coverColor by produceState<Color?>(null, bitmap) {
        value = bitmap?.let { coverBackground(it) }
    }
    val background by animateColorAsState(
        coverColor ?: MaterialTheme.colorScheme.surface,
        animationSpec = tween(400),
        label = "playerBackground"
    )
    val fg = if (coverColor != null) Color.White else MaterialTheme.colorScheme.onSurface
    val fgSoft = fg.copy(alpha = 0.7f)

    // Status bar and navigation bar icons follow the background while this screen is open
    val lightIcons = (coverColor ?: MaterialTheme.colorScheme.surface).luminance() > 0.5f
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(lightIcons) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = lightIcons
        controller?.isAppearanceLightNavigationBars = lightIcons
        onDispose {
            if (previousStatus != null) controller?.isAppearanceLightStatusBars = previousStatus
            if (previousNav != null) controller?.isAppearanceLightNavigationBars = previousNav
        }
    }

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbarHost.showSnackbar(notice!!)
            library.noticeShown()
        }
    }

    val appContext = LocalContext.current
    val downloaded by remember { DownloadManager.get(appContext) }.downloaded.collectAsStateWithLifecycle()
    val isFavorite = state.mediaId in starred
    val rating = ratings[state.mediaId] ?: 0
    val duration = state.durationMs

    Scaffold(
        containerColor = background,
        contentColor = fg,
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dragging down on the handle or on the cover closes the screen. Not on the lists and the slider,
            // where a drag already means something else.
            val closeBySwipe = Modifier.pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            // Handle
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .then(closeBySwipe),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(fg.copy(alpha = 0.4f))
                )
            }

            val actions: @Composable () -> Unit = {
                // Heart, five stars, menu
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HeartButton(isFavorite, fg, round = false) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (state.mediaId.isNotEmpty()) library.toggleStar(StarKind.SONG, state.mediaId)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                        for (star in 1..5) {
                            IconButton(onClick = { library.setRating(state.mediaId, star) }) {
                                Icon(
                                    if (star <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "Rate $star of 5",
                                    tint = fg
                                )
                            }
                        }
                    }
                    MoreButton(state, fg, round = false, onOpenAlbum, onOpenArtist, { sheet = Sheet.SLEEP }, { sheet = Sheet.SPEED }) { addToPlaylist = true }
                }
            }

            if (panel == Panel.COVER) {
                // The cover takes the room that is left, as a square
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(closeBySwipe), contentAlignment = Alignment.Center) {
                    val side = min(maxWidth.value, maxHeight.value).dp
                    CoverImage(
                        coverId = state.coverId,
                        px = COVER_PX,
                        modifier = Modifier
                            .size(side)
                            .shadow(12.dp, RoundedCornerShape(16.dp)),
                        corner = 16.dp
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    state.title,
                    color = fg,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.artist,
                    color = fgSoft,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                autoMixLine(state, settings.autoMix, settings.autoMixMode)?.let { AutoMixBadge(it, Modifier.fillMaxWidth(), color = fg, softColor = fgSoft, center = true) }
                Spacer(Modifier.height(12.dp))
                actions()
            } else {
                // Small cover, title and artist on top; the song text or the queue gets the room below
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(
                        coverId = state.coverId,
                        px = 256,
                        modifier = Modifier.size(64.dp),
                        corner = 8.dp
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            state.title,
                            color = fg,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            state.artist,
                            color = fgSoft,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        autoMixLine(state, settings.autoMix, settings.autoMixMode)?.let { AutoMixBadge(it, color = fg, softColor = fgSoft) }
                    }
                    if (panel == Panel.LYRICS) {
                        HeartButton(isFavorite, fg, round = true) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state.mediaId.isNotEmpty()) library.toggleStar(StarKind.SONG, state.mediaId)
                        }
                        Spacer(Modifier.width(8.dp))
                        MoreButton(state, fg, round = true, onOpenAlbum, onOpenArtist, { sheet = Sheet.SLEEP }, { sheet = Sheet.SPEED }) { addToPlaylist = true }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (panel == Panel.QUEUE) actions()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (panel == Panel.LYRICS) {
                        val load = if (lyrics.first == state.mediaId) lyrics.second else Load.Loading
                        LyricsPanel(
                            load = load,
                            position = vm.positionMs,
                            fg = fg,
                            onRetry = { library.loadLyrics(state.mediaId, state.artist, state.title, force = true) },
                            onSeek = vm::seekTo
                        )
                    } else {
                        QueuePanel(state, settings, vm, library, fg, fgSoft, background, onJump = vm::jumpTo, onClear = vm::clearUpcoming, onMove = vm::moveQueueItem, onRemove = vm::removeQueueItem, onOpenAlbum = onOpenAlbum)
                    }
                }
            }

            SeekBar(vm, duration, infoLine(state, state.mediaId in downloaded), fg, fgSoft)

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = fg, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = vm::togglePlay, modifier = Modifier.size(80.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = fg,
                        modifier = Modifier.size(64.dp)
                    )
                }
                IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = fg, modifier = Modifier.size(40.dp))
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToggleIcon(Icons.Rounded.FormatQuote, "Lyrics", showLyrics, fg, background) {
                    panel = if (showLyrics) Panel.COVER else Panel.LYRICS
                }
                ToggleIcon(Icons.Rounded.Airplay, "AirPlay", airPlayOn, fg, background) { showAirPlay = true }
                ToggleIcon(Icons.AutoMirrored.Rounded.QueueMusic, "Up next", panel == Panel.QUEUE, fg, background) {
                    panel = if (panel == Panel.QUEUE) Panel.COVER else Panel.QUEUE
                }
            }
        }
    }

    if (showAirPlay) AirPlayDialog(onDismiss = { showAirPlay = false })

    if (sheet == Sheet.SPEED) {
        ModalBottomSheet(
            onDismissRequest = { sheet = Sheet.NONE },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = GlassPill.adaptivePopupBackground,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            SpeedSheet(settings, vm)
        }
    }
    if (addToPlaylist) {
        AddToPlaylistDialog(library, listOf(state.mediaId), onDismiss = { addToPlaylist = false })
    }
    if (sheet == Sheet.SLEEP) {
        ModalBottomSheet(
            onDismissRequest = { sheet = Sheet.NONE },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = GlassPill.adaptivePopupBackground,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            SleepSheet(settings, vm, onDone = { sheet = Sheet.NONE })
        }
    }
}

/** A round icon button. An active one gets a filled circle, like the repeat button in the layout example. */
@Composable
private fun ToggleIcon(
    icon: ImageVector,
    description: String,
    active: Boolean,
    fg: Color,
    background: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (active) Modifier.background(fg.copy(alpha = 0.85f)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (active) background else fg)
    }
}

@Composable
private fun SpeedSheet(settings: PlaybackSettingsValues, vm: PlaybackViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text("Playback speed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        ChipRow("Speed", SPEEDS.map { speedLabel(it) to it }, settings.speed, vm::setSpeed)
    }
}

@Composable
private fun <T> ChipRow(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Text(label, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { (text, value) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(text) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun SleepSheet(settings: PlaybackSettingsValues, vm: PlaybackViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text("Sleep timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            sleepStatus(settings),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        SleepOption("Off") { vm.clearSleep(); onDone() }
        listOf(15, 30, 45, 60).forEach { minutes ->
            SleepOption("$minutes minutes") { vm.sleepAfterMinutes(minutes); onDone() }
        }
        SleepOption("End of this song") { vm.sleepAtTrackEnd(); onDone() }
    }
}

@Composable
private fun SleepOption(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

private fun speedLabel(speed: Float): String = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

private fun clock(ms: Long): String {
    val seconds = (ms / 1000).toInt().coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

// The source is the file on the device when the song is downloaded, otherwise the stream
private fun infoLine(state: PlayerState, downloaded: Boolean): String {
    return listOfNotNull(
        if (downloaded) "Downloaded" else "Stream",
        state.format.ifEmpty { null },
        if (state.bitRate > 0) "${state.bitRate} kbps" else null
    ).joinToString(" · ")
}

/** The line in the sleep dialog: what is set now, with the time left. */
@Composable
private fun sleepStatus(settings: PlaybackSettingsValues): String {
    if (settings.sleepAtTrackEnd) return "Stops at the end of this song."
    if (settings.sleepEndAt <= 0L) return "No timer is set."
    val remaining by produceState(settings.sleepEndAt - System.currentTimeMillis(), settings.sleepEndAt) {
        while (true) {
            value = settings.sleepEndAt - System.currentTimeMillis()
            delay(1000)
        }
    }
    return "Stops in " + clock(remaining.coerceAtLeast(0L))
}

/**
 * 0.0.147: the slider and the two clocks. The position ticks twice a second and is read only here, so the rest of the screen
 * is not drawn again each time.
 */
@Composable
private fun SeekBar(vm: PlaybackViewModel, duration: Long, info: String, fg: Color, fgSoft: Color) {
    val position by vm.positionMs.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownPosition = dragging?.let { (it * duration).toLong() } ?: position
    Slider(
        value = dragging ?: fraction,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { vm.seekTo((it * duration).toLong()) }
            dragging = null
        },
        enabled = duration > 0,
        colors = SliderDefaults.colors(
            thumbColor = fg,
            activeTrackColor = fg,
            inactiveTrackColor = fg.copy(alpha = 0.3f),
            disabledThumbColor = fg.copy(alpha = 0.3f),
            disabledActiveTrackColor = fg.copy(alpha = 0.3f),
            disabledInactiveTrackColor = fg.copy(alpha = 0.3f)
        )
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(clock(shownPosition), color = fgSoft, fontSize = 13.sp, modifier = Modifier.width(56.dp))
        Text(
            info,
            color = fgSoft,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            "-" + clock((duration - shownPosition).coerceAtLeast(0L)),
            color = fgSoft,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp)
        )
    }
}

/** The song text in place of the cover. Timed text follows the song and a tap on a line jumps to it. */
@Composable
private fun LyricsPanel(
    load: Load<Lyrics?>,
    position: StateFlow<Long>,
    fg: Color,
    onRetry: () -> Unit,
    onSeek: (Long) -> Unit
) {
    when (load) {
        is Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = fg)
        }
        is Load.Failed -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(load.message, color = fg, textAlign = TextAlign.Center)
            TextButton(onClick = onRetry) { Text("Try again", color = fg) }
        }
        is Load.Ready -> {
            val lyrics = load.data
            if (lyrics == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No lyrics for this song.", color = fg.copy(alpha = 0.7f))
                }
            } else {
                LyricsLines(lyrics, position, fg, onSeek)
            }
        }
    }
}

@Composable
private fun LyricsLines(lyrics: Lyrics, position: StateFlow<Long>, fg: Color, onSeek: (Long) -> Unit) {
    val positionMs by position.collectAsStateWithLifecycle()
    val listState: LazyListState = rememberLazyListState()
    val current = if (lyrics.synced) {
        lyrics.lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= positionMs }
    } else {
        -1
    }
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        itemsIndexed(lyrics.lines) { index, line ->
            val start = line.startMs
            Text(
                line.text.ifEmpty { " " },
                color = if (!lyrics.synced || index == current) fg else fg.copy(alpha = 0.5f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (start != null) Modifier.clickable { onSeek(start) } else Modifier)
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun HeartButton(isFavorite: Boolean, fg: Color, round: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = if (round) Modifier.size(48.dp).clip(CircleShape).background(fg.copy(alpha = 0.2f)) else Modifier
    ) {
        Icon(
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = fg
        )
    }
}

/** The "..." menu with the way to the album and the artist. Nothing is shown when the song has neither. */
@Composable
private fun MoreButton(
    state: PlayerState,
    fg: Color,
    round: Boolean,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onSleep: () -> Unit,
    onSpeed: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    if (state.mediaId.isEmpty()) {
        if (!round) Spacer(Modifier.size(48.dp))
        return
    }
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = if (round) Modifier.size(48.dp).clip(CircleShape).background(fg.copy(alpha = 0.2f)) else Modifier
        ) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = fg)
        }
        NooklinMenu(expanded = open, onDismiss = { open = false }) {
            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { open = false; onAddToPlaylist() })
            DropdownMenuItem(text = { Text("Playback speed") }, onClick = { open = false; onSpeed() })
            DropdownMenuItem(text = { Text("Sleep timer") }, onClick = { open = false; onSleep() })
            state.albumId?.let { id ->
                DropdownMenuItem(text = { Text("Go to album") }, onClick = { open = false; onOpenAlbum(id) })
            }
            state.artistId?.let { id ->
                DropdownMenuItem(text = { Text("Go to artist") }, onClick = { open = false; onOpenArtist(id) })
            }
        }
    }
}

/** The queue inside the player, so the controls and the bottom bar stay in reach. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueuePanel(
    state: PlayerState,
    settings: PlaybackSettingsValues,
    vm: PlaybackViewModel,
    library: LibraryViewModel,
    fg: Color,
    fgSoft: Color,
    background: Color,
    onJump: (Int) -> Unit,
    onClear: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ratings by library.ratings.collectAsStateWithLifecycle()
    val downloaded by DownloadManager.get(context).downloaded.collectAsStateWithLifecycle()
    // The notice about downloaded songs, shown when AutoMix is switched on
    var showAutoMixNotice by remember { mutableStateOf(false) }
    // The AutoMix panel (long press on the button)
    var modeMenu by remember { mutableStateOf(false) }
    val mixBusy by vm.mixBusy.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    // The songs that follow the song that plays (not that song itself), in the order they really play,
    // with the songs before it above (scroll up). The played songs are the songs earlier in the queue (not a real play history), and
    // with shuffle on, the songs that are not in the upcoming list.
    val upcomingNow = remember(state.upcoming, state.queue, state.index) {
        state.upcoming.filter { it in state.queue.indices && it != state.index }
    }
    val upcomingSet = remember(upcomingNow) { upcomingNow.toSet() }
    val history = remember(state.queue, state.index, upcomingSet) {
        state.queue.indices.filter { it != state.index && it !in upcomingSet }
    }
    // The place of the "Next" header in the list: the list rests there, with the history above it
    val nowHeaderAt = if (history.isEmpty()) 0 else history.size + 1
    LaunchedEffect(Unit) {
        if (nowHeaderAt > 0) listState.scrollToItem(nowHeaderAt)
    }
    // A fling stops at the "Next" header, from both sides: one that starts in the upcoming songs stops there on its way up into the
    // history, one that starts in the history stops there on its way down. The next swipe goes on. The place is measured on the
    // first song after the header (the header itself is pinned): [gap] is 0 when the list rests, negative when it is scrolled
    // further into the upcoming songs, positive when it is scrolled into the history.
    val nowHeaderAtNow by rememberUpdatedState(nowHeaderAt)
    var nextHeaderPx by remember { mutableStateOf(0) }
    val headerPxNow by rememberUpdatedState(nextHeaderPx)
    val stopAtNext = remember(listState) {
        object : NestedScrollConnection {
            var toHistory = false
            var toNext = false
            private fun gap(): Int? {
                val song = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == nowHeaderAtNow + 1 } ?: return null
                return song.offset - headerPxNow
            }
            private fun inHistory() = gap() == null && listState.firstVisibleItemIndex <= nowHeaderAtNow
            private fun inUpcoming() = gap() == null && listState.firstVisibleItemIndex > nowHeaderAtNow
            override suspend fun onPreFling(available: Velocity): Velocity {
                val g = gap()
                val active = nowHeaderAtNow > 0 && headerPxNow > 0
                toHistory = active && available.y > 0f && (g != null && g < -2 || inUpcoming())
                toNext = active && available.y < 0f && (g != null && g > 2 || inHistory())
                return Velocity.Zero
            }
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.SideEffect) return Offset.Zero
                val g = gap() ?: return Offset.Zero
                val reached = when {
                    toHistory && available.y > 0f -> available.y >= (-g).coerceAtLeast(0).toFloat()
                    toNext && available.y < 0f -> -available.y >= g.coerceAtLeast(0).toFloat()
                    else -> return Offset.Zero
                }
                if (!reached) return Offset.Zero
                // The header reaches the top: take the rest of the fling and put the header exactly there
                toHistory = false
                toNext = false
                scope.launch { listState.scrollToItem(nowHeaderAtNow) }
                return Offset(0f, available.y)
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                toHistory = false
                toNext = false
                return Velocity.Zero
            }
        }
    }
    // Moving a song: [dragList] holds the upcoming songs in the order shown while a handle is dragged; the move is
    // made in the player when the finger lets go
    var dragList by remember { mutableStateOf<List<Int>?>(null) }
    var rowPx by remember { mutableFloatStateOf(150f) }
    var dragging by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dragListNow by rememberUpdatedState(dragList)
    val upcomingNowSeen by rememberUpdatedState(upcomingNow)
    // The queue position whose menu is open, and the song that is being added to a playlist
    var menuFor by remember { mutableStateOf(-1) }
    var playlistFor by remember { mutableStateOf<String?>(null) }
    // With shuffle on the order of play is not the order of the list, so moving is only offered without shuffle
    val canMove = !state.shuffle
    val shown = dragList ?: upcomingNow

    @Composable
    fun Entry(index: Int, upcoming: Boolean, slide: Modifier) {
        val row = state.queue.getOrNull(index) ?: return
        val isDragged = dragging == index
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { if (upcoming) rowPx = it.height.toFloat() }
                .then(if (isDragged) Modifier else slide)
                .zIndex(if (isDragged) 1f else 0f)
                .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
        ) {
            QueueSongRow(
                row, false, fg, fgSoft,
                onClick = { onJump(index) },
                onLongClick = { menuFor = index; library.loadSong(row.id) },
                trailing = {
                    if (upcoming && canMove) {
                        Icon(
                            Icons.Rounded.Menu,
                            contentDescription = "Move",
                            tint = fgSoft,
                            modifier = Modifier
                                .padding(12.dp)
                                .pointerInput(index) {
                                    detectDragGestures(
                                        onDragStart = { dragging = index; dragOffset = 0f; dragList = upcomingNowSeen },
                                        onDragEnd = {
                                            val list = dragListNow
                                            val place = list?.indexOf(index) ?: -1
                                            val sorted = upcomingNowSeen.sorted()
                                            if (list != null && place in sorted.indices && sorted[place] != index) {
                                                onMove(index, sorted[place])
                                            }
                                            dragging = -1; dragOffset = 0f; dragList = null
                                        },
                                        onDragCancel = { dragging = -1; dragOffset = 0f; dragList = null },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val list = dragListNow ?: return@detectDragGestures
                                            val at = list.indexOf(index)
                                            val target = when {
                                                dragOffset > rowPx / 2 && at < list.lastIndex -> at + 1
                                                dragOffset < -rowPx / 2 && at > 0 -> at - 1
                                                else -> at
                                            }
                                            if (target != at) {
                                                dragList = list.toMutableList().also { it.add(target, it.removeAt(at)) }
                                                dragOffset += if (target > at) -rowPx else rowPx
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            )
            NooklinMenu(expanded = menuFor == index, onDismiss = { menuFor = -1 }) {
                if (row.id !in downloaded) {
                    DropdownMenuItem(text = { Text("Download") }, onClick = { menuFor = -1; library.downloadByIds(listOf(row.id)) })
                }
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menuFor = -1; playlistFor = row.id })
                if (upcoming && canMove) {
                    DropdownMenuItem(text = { Text("Move to top") }, onClick = {
                        menuFor = -1
                        val first = (state.index + 1).coerceAtMost(state.queue.lastIndex)
                        if (index != first) onMove(index, first)
                    })
                }
                row.albumId?.let { id ->
                    DropdownMenuItem(text = { Text("Go to album") }, onClick = { menuFor = -1; onOpenAlbum(id) })
                }
                val rating = ratings[row.id] ?: 0
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            for (star in 1..5) {
                                Box(
                                    Modifier.size(48.dp).clickable { library.setRating(row.id, star) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (star <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "Rate $star of 5",
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    },
                    onClick = {}
                )
                DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { menuFor = -1; onRemove(index) })
                DropdownMenuItem(text = { Text("Share") }, onClick = {
                    menuFor = -1
                    val text = if (row.artist.isEmpty()) row.title else "${row.title} - ${row.artist}"
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Four equal buttons: Shuffle, Repeat, AutoPlay, AutoMix
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QueueOption(
                Icons.Rounded.Shuffle, "Shuffle", state.shuffle, fg, background, Modifier.weight(1f),
                description = if (state.shuffle) "Shuffle is on" else "Shuffle is off"
            ) { vm.toggleShuffle() }
            QueueOption(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                when (state.repeatMode) {
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    else -> "Repeat"
                },
                state.repeatMode != Player.REPEAT_MODE_OFF, fg, background, Modifier.weight(1f)
            ) { vm.cycleRepeat() }
            QueueOption(Icons.Rounded.AllInclusive, "AutoPlay", settings.autoPlay, fg, background, Modifier.weight(1f)) {
                vm.setAutoPlay(!settings.autoPlay)
            }
            val activeMode = MixMode.of(settings.autoMixMode)
            Box(Modifier.weight(1.5f)) {
                QueueOption(
                    Icons.Rounded.GraphicEq,
                    if (mixBusy) "Mixing..." else if (settings.autoMix && activeMode != null) activeMode.label else "AutoMix",
                    settings.autoMix, fg, background, Modifier.fillMaxWidth(),
                    description = "AutoMix",
                    onLongClick = { modeMenu = true },
                    endInset = 48.dp
                ) {
                    if (!settings.autoMix && !settings.autoMixNoticeHidden) showAutoMixNotice = true
                    vm.setAutoMix(!settings.autoMix)
                }
                if (mixBusy) {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.TopStart).padding(6.dp).size(14.dp),
                        strokeWidth = 2.dp,
                        color = if (settings.autoMix) background else fg
                    )
                }
                // A visible way to the modes, next to the long press
                Box(
                    Modifier.align(Alignment.CenterEnd).width(48.dp).height(52.dp)
                        .clickable(onClickLabel = "Choose AutoMix mode") { modeMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.ArrowDropDown, contentDescription = "AutoMix modes",
                        tint = if (settings.autoMix) background else fg, modifier = Modifier.size(24.dp)
                    )
                }
                if (modeMenu) {
                    AutoMixPanel(
                        autoMix = settings.autoMix,
                        activeMode = activeMode,
                        zone = settings.autoMixZone,
                        pickedGenres = settings.autoMixGenres,
                        genres = downloaded.values.map { it.song.genre.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().sorted(),
                        onToggle = {
                            if (!settings.autoMix && !settings.autoMixNoticeHidden) showAutoMixNotice = true
                            vm.setAutoMix(!settings.autoMix)
                        },
                        onChoose = { mode, zone, genres ->
                            modeMenu = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.chooseAutoMixMode(mode, zone, genres)
                        },
                        onDismiss = { modeMenu = false }
                    )
                }
            }
        }
        if (showAutoMixNotice) {
            AutoMixNoticeDialog { hide ->
                showAutoMixNotice = false
                if (hide) vm.setAutoMixNoticeHidden(true)
            }
        }
        // 0.0.151: "Up Next" and Clear sit under the buttons
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Up Next",
                color = fg,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (state.index in 0 until state.queue.size - 1) {
                TextButton(onClick = onClear) { Text("Clear", color = fgSoft, fontSize = 16.sp) }
            }
        }
        playlistFor?.let { id ->
            AddToPlaylistDialog(library, listOf(id), onDismiss = { playlistFor = null })
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().nestedScroll(stopAtNext), state = listState) {
            if (history.isNotEmpty()) {
                // The heading of the list you are in stays pinned at the top; "Next" pushes "History" out
                stickyHeader(key = "header-played") { QueueHeader("History", fgSoft, background = background) }
                items(history, key = { "h$it" }) { index -> Entry(index, false, Modifier.animateItem()) }
                stickyHeader(key = "header-now") {
                    QueueHeader("Next", fgSoft, line = true, fg = fg, background = background,
                        modifier = Modifier.onSizeChanged { nextHeaderPx = it.height })
                }
            }
            items(shown, key = { "u$it" }) { index -> Entry(index, true, Modifier.animateItem()) }
        }
    }
}

/** A small heading in the queue; the one above the current song also has a line over it. */
@Composable
private fun QueueHeader(
    text: String,
    fgSoft: Color,
    line: Boolean = false,
    fg: Color = fgSoft,
    background: Color? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (background != null) Modifier.background(background) else Modifier)
            .padding(top = if (line) 8.dp else 0.dp, bottom = 4.dp)
    ) {
        if (line) Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = 0.25f)))
        Text(text, color = fgSoft, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = if (line) 8.dp else 0.dp))
    }
}

/** One song in the queue list. A long press calls [onLongClick]; [trailing] is the space at the end (the move handle). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueSongRow(
    row: QueueRow,
    current: Boolean,
    fg: Color,
    fgSoft: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    trailing: @Composable () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(coverId = row.coverId, px = 128, modifier = Modifier.size(48.dp), corner = 6.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                row.title,
                color = fg,
                fontSize = 17.sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(row.artist, color = fgSoft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** One of the four options above the queue: a small icon over a short label, filled while on. All of them the same size. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueOption(
    icon: ImageVector,
    label: String,
    active: Boolean,
    fg: Color,
    background: Color,
    modifier: Modifier,
    description: String = label,
    onLongClick: (() -> Unit)? = null,
    endInset: Dp = 0.dp,
    onClick: () -> Unit
) {
    // Selection feedback when a switch is toggled
    val haptic = LocalHapticFeedback.current
    Column(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) fg.copy(alpha = 0.85f) else fg.copy(alpha = 0.12f))
            .combinedClickable(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }, onLongClick = onLongClick)
            .padding(end = endInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (active) background else fg, modifier = Modifier.size(20.dp))
        Text(
            label,
            color = if (active) background else fg,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
