package org.nooklin.music.ui.settings

import org.nooklin.music.ui.common.NooklinTopBar
import androidx.compose.ui.unit.sp
import org.nooklin.music.ui.common.LocalBottomOverlay
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nooklin.music.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            NooklinTopBar("About", onBack)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(
                "About",
                listOf(
                    { AboutValueRow("Version", BuildConfig.VERSION_NAME) },
                    { AboutValueRow("License", "GPL-3.0") }
                )
            )
            SettingsGroup(
                "Links",
                listOf(
                    { LinkRow("Source code on GitHub", "github.com/Nooklin/music", "https://github.com/Nooklin/music") },
                    { LinkRow("Contact", "info@nooklin.org", "mailto:info@nooklin.org") }
                )
            )
            SettingsGroup(
                "Privacy",
                listOf({
                    Text(
                        "The only network connection is to the Navidrome server you enter. " +
                            "There is no analytics, no crash reporting, no telemetry and no Google Play Services.\n\n" +
                            "Your password and recent searches are stored encrypted on this phone. " +
                            "Permissions: Internet, notifications and a foreground service for playback, and keeping the device awake while streaming.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                })
            )
            SettingsSectionTitle("How to Use")
            HOW_TO.forEach { topic -> HowToTopic(topic) }
            Spacer(Modifier.height(LocalBottomOverlay.current))
        }
    }
}

@Composable
private fun AboutValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class Topic(val title: String, val lines: List<String>)

// Short lines, the names of buttons and screens are the ones in the app
private val HOW_TO = listOf(
    Topic("Get started", listOf(
        "Open Settings > Server. Enter the address of your Navidrome server, your username and password.",
        "Tap Test connection, then Save. A second address (for example the one at home) is optional.",
        "The Server screen shows whether the server answers, how fast, and how much music it holds."
    )),
    Topic("Find your music", listOf(
        "Home shows blocks you can reorder in Settings > Home.",
        "Library holds Artists, Albums, Songs, Favorites, Genres and Downloads. The ... menu has Edit Sections.",
        "Playlists lists your playlists. The round button next to the bottom bar searches."
    )),
    Topic("The player", listOf(
        "Open the player from the mini player. Drag it down to close it.",
        "Above the queue: Shuffle, Repeat, AutoPlay and AutoMix. AutoPlay and the Discovery Station learn from what you play and skip.",
        "Speed and sleep timer are in the ... menu. Press and hold a song in the queue for more options.",
        "The Play on menu switches between this phone, Bluetooth and AirPlay speakers. AirPlay plays about two seconds late."
    )),
    Topic("Downloads", listOf(
        "Use Download on an album or playlist, or in the ... menu of a song.",
        "Settings > Downloads shows size and playing time, sets a storage limit and removes downloads.",
        "At the limit, the oldest songs without a heart are removed. Songs with a heart stay."
    )),
    Topic("AutoMix", listOf(
        "Switch it on in Settings > AutoMix or in the player. Tap the arrow next to the AutoMix button, or press and hold it, to pick a mode. The list is filled with downloaded songs that are scanned.",
        "Beat matching: songs are analysed on this phone. A song that is not downloaded is first loaded in full, which uses data.",
        "The next song comes in on the start of a phrase. The tempo change between two songs is at most 30% in total, shared by both songs, and the bass swaps on a bar line.",
        "When tempos are too far apart or the keys clash, the old song leaves on a bar line with an echo, a filter sweep or a cut. Songs without a clear beat get a short clean fade.",
        "Songs longer than 15 minutes, and everything while Repeat one is on, play without beat matching. AutoMix also works over AirPlay.",
        "Separate vocals: when two songs both sing in an overlap, the voice of the old song goes out first and the voice of the next comes in last. If that is not ready in time, the change is planned again.",
        "With beat matching off, songs overlap for up to 10 seconds.",
        "With AutoMix and beat matching on, Shuffle and AutoPlay put songs in an order where each suits the one before it."
    )),
    Topic("Backup", listOf(
        "Settings > Backup saves your look and player settings in a file locked with a password you choose.",
        "Server details are not in the backup. Without the password the file cannot be opened."
    ))
)

@Composable
private fun HowToTopic(topic: Topic) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(topic.title) { androidx.compose.runtime.mutableStateOf(false) }
    val items: List<@Composable () -> Unit> = buildList {
        add {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = if (open) "Close" else "Open") { open = !open }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(topic.title, fontWeight = FontWeight.SemiBold)
                    Icon(
                        if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
        }
        if (open) add {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                topic.lines.forEach { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
            }
        }
    }
    SettingsGroup(null, items)
}

@Composable
private fun LinkRow(label: String, value: String, url: String) {
    val handler = androidx.compose.ui.platform.LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .clickable(onClickLabel = "Open") { runCatching { handler.openUri(url) } }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
