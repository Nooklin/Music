package org.nooklin.music.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.music.ui.common.GlassPill
import org.nooklin.music.ui.common.GlassPillContainer

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    LIBRARY("Library", Icons.Rounded.LibraryMusic),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Rounded.PlaylistPlay),
    SETTINGS("Settings", Icons.Rounded.Settings)
}

/** The round button next to the bottom bar that opens search. */
@Composable
fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(end = 24.dp, top = 4.dp, bottom = 4.dp)
            .size(56.dp)
            .clip(CircleShape)
            .background(GlassPill.adaptiveFloatingBackground)
            .border(GlassPill.adaptiveBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** The four main places of the app: a floating pill at the bottom of every screen except the player. */
@Composable
fun AppBottomBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    GlassPillContainer(
        modifier = modifier,
        backgroundColor = GlassPill.adaptiveFloatingBackground,
        border = GlassPill.adaptiveBorder,
        verticalContentPadding = 0.dp,
        horizontalContentPadding = 0.dp
    ) {
        // 56 dp, the same height as the round search button next to it. The selection sits 4 dp from the edge of the bar: the bar has radius 24 dp, so the selection has 24 minus 4 = 20 dp and sits concentric, also on the first and last tab
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (active) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            else Modifier
                        )
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint)
                    Text(tab.label, color = tint, fontSize = 11.sp)
                }
            }
        }
    }
}
