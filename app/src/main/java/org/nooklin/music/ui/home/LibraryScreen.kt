package org.nooklin.music.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.music.data.local.LibrarySection
import org.nooklin.music.data.local.LibrarySectionSetting
import org.nooklin.music.ui.common.LocalBottomOverlay
import org.nooklin.music.ui.common.NooklinMenu
import org.nooklin.music.ui.common.GlassPill
import org.nooklin.music.ui.common.GlassPillContainer
import org.nooklin.music.ui.theme.LocalContrastColorState

private fun iconOf(section: LibrarySection): ImageVector = when (section) {
    LibrarySection.ARTISTS -> Icons.Filled.Person
    LibrarySection.ALBUMS -> Icons.Filled.Album
    LibrarySection.SONGS -> Icons.Filled.MusicNote
    LibrarySection.FAVORITES -> Icons.Filled.Favorite
    LibrarySection.GENRES -> Icons.Filled.Category
    LibrarySection.PLAYLISTS -> Icons.Filled.LibraryMusic
    LibrarySection.DOWNLOADS -> Icons.Filled.Download
}

/**
 * The ways into the whole library. "Edit Sections" in the menu shows every row with an eye (show or hide) and three
 * lines (drag to move); the check mark saves, back leaves without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    sections: List<LibrarySectionSetting>?,
    onSave: (List<LibrarySectionSetting>) -> Unit,
    onNavigate: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(emptyList<LibrarySectionSetting>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var rowPx by remember { mutableFloatStateOf(150f) }
    var dragging by remember { mutableStateOf<LibrarySection?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val draftNow by rememberUpdatedState(draft)
    BackHandler(enabled = editing) { editing = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library", fontSize = 34.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    if (editing) {
                        IconButton(onClick = { onSave(draft); editing = false }) {
                            Icon(Icons.Filled.Check, contentDescription = "Save")
                        }
                    } else if (sections != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreHoriz, contentDescription = "More")
                            }
                            NooklinMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Edit Sections") },
                                    onClick = { menuOpen = false; draft = sections; editing = true }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (sections != null && editing) {
            val accent = LocalContrastColorState.current.color ?: MaterialTheme.colorScheme.primary
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)
            ) {
                items(draft, key = { it.section }) { setting ->
                    val isDragged = dragging == setting.section
                    GlassPillContainer(
                        modifier = Modifier
                            .onSizeChanged { rowPx = it.height.toFloat() }
                            // The other rows slide to their new place; the dragged one follows the finger instead
                            .then(if (isDragged) Modifier else Modifier.animateItem())
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragged) dragOffset else 0f },
                        backgroundColor = GlassPill.adaptiveGlassBackground
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(if (setting.visible) 1f else 0.4f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(iconOf(setting.section), contentDescription = null, modifier = Modifier.padding(start = 8.dp, end = 16.dp))
                            Text(setting.section.label, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                // At least one row stays visible, or the screen would be empty
                                if (!setting.visible || draft.count { it.visible } > 1) {
                                    draft = draft.map {
                                        if (it.section == setting.section) it.copy(visible = !it.visible) else it
                                    }
                                }
                            }) {
                                Icon(
                                    if (setting.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (setting.visible) "Hide" else "Show",
                                    tint = accent
                                )
                            }
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = "Move",
                                modifier = Modifier
                                    .padding(12.dp)
                                    .pointerInput(setting.section) {
                                        detectDragGestures(
                                            onDragStart = { dragging = setting.section; dragOffset = 0f },
                                            onDragEnd = { dragging = null; dragOffset = 0f },
                                            onDragCancel = { dragging = null; dragOffset = 0f },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val list = draftNow
                                                val index = list.indexOfFirst { it.section == setting.section }
                                                val target = when {
                                                    dragOffset > rowPx / 2 && index < list.lastIndex -> index + 1
                                                    dragOffset < -rowPx / 2 && index > 0 -> index - 1
                                                    else -> index
                                                }
                                                if (target != index) {
                                                    draft = list.toMutableList().also { it.add(target, it.removeAt(index)) }
                                                    dragOffset += if (target > index) -rowPx else rowPx
                                                }
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                sections?.filter { it.visible }?.forEach { setting ->
                    LibraryRow(setting.section.label, iconOf(setting.section)) { onNavigate(setting.section.key) }
                }
                Spacer(Modifier.height(LocalBottomOverlay.current))
            }
        }
    }
}

@Composable
private fun LibraryRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    GlassPillContainer(backgroundColor = GlassPill.adaptiveGlassBackground) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassPill.shape)
                .clickable { onClick() }
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                Text(label)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}
