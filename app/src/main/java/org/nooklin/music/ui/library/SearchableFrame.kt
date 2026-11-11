package org.nooklin.music.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.music.ui.common.FitTitle
import org.nooklin.music.ui.common.GlassPill
import org.nooklin.music.ui.common.GlassPillContainer
import org.nooklin.music.ui.common.LocalBottomOverlay
import org.nooklin.music.ui.common.NooklinMenu

/**
 * A list screen with the same top as Playlists: title row (back, refresh, "..." menu), a search pill under it, and the
 * content below. Tapping in the search field slides the title row away so the search has the top of the screen; the
 * X in the circle (or the back gesture) clears the search and brings the title row back.
 */
@Composable
fun SearchableFrame(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    notice: String?,
    onNoticeShown: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    sortSummary: String? = null,
    actions: @Composable () -> Unit = {},
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
    content: @Composable () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val searching = focused || query.isNotEmpty()
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            onNoticeShown()
        }
    }
    val stopSearching = {
        onQueryChange("")
        focusManager.clearFocus()
    }
    BackHandler(enabled = searching) { stopSearching() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomOverlay.current)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(visible = !searching, enter = expandVertically(), exit = shrinkVertically()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    FitTitle(title, Modifier.weight(1f))
                    actions()
                    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreHoriz, contentDescription = "More") }
                        val close = { menuOpen = false }
                        NooklinMenu(expanded = menuOpen, onDismiss = close) { menu(this, close) }
                    }
                }
            }
            SearchPill(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = placeholder,
                searching = searching,
                onCancel = stopSearching,
                onFocusChange = { focused = it },
                onSearch = { focusManager.clearFocus(force = false) }
            )
            if (sortSummary != null) {
                Text(
                    sortSummary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 32.dp, top = 2.dp, bottom = 4.dp)
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

/** The search pill, the same size on every screen: 44 dp high, magnifier, field, and the X in a circle while searching. */
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    searching: Boolean,
    onCancel: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSearch: () -> Unit
) {
    GlassPillContainer(backgroundColor = GlassPill.adaptiveGlassBackground, verticalContentPadding = 0.dp) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.padding(start = 12.dp, end = 8.dp).size(22.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                        inner()
                    }
                }
            )
            if (searching) {
                Box(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel search", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * The sorting part of a "..." menu, the same on every screen: a "Sort by" heading, the options with a check mark on
 * the chosen one and, when that option has a direction, Ascending and Descending below it with a check mark.
 */
@Composable
fun ColumnScope.SortMenu(
    options: List<String>,
    selected: Int,
    ascending: Boolean,
    selectedHasDirection: Boolean,
    onSelect: (Int) -> Unit,
    onAscendingChange: (Boolean) -> Unit,
    close: () -> Unit
) {
    Text(
        "Sort by",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    options.forEachIndexed { index, label ->
        DropdownMenuItem(
            text = { Text(label) },
            trailingIcon = { if (index == selected) Icon(Icons.Filled.Check, contentDescription = "Selected") },
            onClick = { close(); onSelect(index) }
        )
    }
    if (selectedHasDirection) {
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Ascending") },
            trailingIcon = { if (ascending) Icon(Icons.Filled.Check, contentDescription = "Selected") },
            onClick = { close(); onAscendingChange(true) }
        )
        DropdownMenuItem(
            text = { Text("Descending") },
            trailingIcon = { if (!ascending) Icon(Icons.Filled.Check, contentDescription = "Selected") },
            onClick = { close(); onAscendingChange(false) }
        )
    }
}

/** The line under the search pill: "Sorted by Name, ascending", or null for the server order. */
fun sortSummaryText(label: String, ascending: Boolean): String = "Sorted by $label, " + if (ascending) "ascending" else "descending"
