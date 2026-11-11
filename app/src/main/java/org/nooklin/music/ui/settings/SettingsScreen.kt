package org.nooklin.music.ui.settings

import org.nooklin.music.ui.common.LocalBottomOverlay
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.music.ui.common.GlassPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(
                null,
                listOf({ SettingsNavRow("Server", Icons.Filled.Dns, Tile.Blue) { onNavigate("server") } })
            )
            Spacer(Modifier.height(16.dp))
            SettingsGroup(
                null,
                listOf(
                    { SettingsNavRow("Appearance", Icons.Filled.Palette, Tile.Purple) { onNavigate("appearance") } },
                    { SettingsNavRow("Home", Icons.Filled.Home, Tile.Orange) { onNavigate("home") } }
                )
            )
            Spacer(Modifier.height(16.dp))
            SettingsGroup(
                null,
                listOf(
                    { SettingsNavRow("Playback", Icons.Filled.PlayArrow, Tile.Red) { onNavigate("playback") } },
                    { SettingsNavRow("AutoMix", Icons.Filled.GraphicEq, Tile.Green) { onNavigate("automix") } },
                    { SettingsNavRow("Downloads", Icons.Filled.Download, Tile.Blue) { onNavigate("downloads") } }
                )
            )
            Spacer(Modifier.height(16.dp))
            SettingsGroup(
                null,
                listOf(
                    { SettingsNavRow("Backup", Icons.Filled.Lock, Tile.Green) { onNavigate("backup") } },
                    { SettingsNavRow("About", Icons.Filled.Info, Tile.Gray) { onNavigate("about") } }
                )
            )
            Spacer(Modifier.height(LocalBottomOverlay.current))
        }
    }
}

// Items instead of a content lambda: a divider goes between items, never after the last
@Composable
fun SettingsGroup(title: String?, items: List<@Composable () -> Unit>) {
    if (title != null) {
        Text(
            title,
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
        )
    }
    Surface(
        color = GlassPill.adaptiveGlassBackground,
        shape = RoundedCornerShape(20.dp),
        border = GlassPill.adaptiveBorder,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        val edgePadding = if (items.size == 1) 8.dp else 14.dp
        Column(Modifier.padding(vertical = edgePadding)) {
            items.forEachIndexed { index, item ->
                // 0.0.140: rows are 44 dp, as in Nooklin Photos; the row itself is the touch target, so a Switch or RadioButton adds no 48 dp
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) { item() }
                if (index != items.lastIndex) {
                    // start = 56dp aligns past the leading icon
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsNavRow(label: String, icon: ImageVector = Icons.Filled.MoreHoriz, tint: Color = Tile.Blue, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon, tint)
            Text(label)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}
