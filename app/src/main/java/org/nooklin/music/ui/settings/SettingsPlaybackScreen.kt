package org.nooklin.music.ui.settings

import org.nooklin.music.ui.common.NooklinTopBar
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import org.nooklin.music.data.playback.ReplayGainMode
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import org.nooklin.music.playback.taste.TasteStore
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.nooklin.music.data.playback.PlaybackSettings
import org.nooklin.music.data.playback.PlaybackSettingsValues
import org.nooklin.music.ui.common.LocalBottomOverlay

/** The playback options that are set once: skip silence, crossfade, Smart Fades, ReplayGain, volume limit, private mode. AutoMix has its own screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPlaybackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PlaybackSettings(context.applicationContext) }
    val settings by store.values.collectAsStateWithLifecycle(PlaybackSettingsValues())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            NooklinTopBar("Playback", onBack)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(
                null,
                listOf(
                    {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Skip silence", modifier = Modifier.weight(1f))
                            Switch(
                                checked = settings.skipSilence,
                                onCheckedChange = { scope.launch { store.setSkipSilence(it) } }
                            )
                        }
                    },
                    {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ChoiceRow(
                                "Crossfade",
                                listOf(0, 3, 6, 9, 12).map { (if (it == 0) "Off" else "$it s") to it },
                                settings.crossfadeSeconds
                            ) { scope.launch { store.setCrossfadeSeconds(it) } }
                        }
                    },
                    {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Smart Fades", modifier = Modifier.weight(1f))
                            Switch(
                                checked = settings.smartFades,
                                onCheckedChange = { scope.launch { store.setSmartFades(it) } }
                            )
                        }
                    },
                    {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ChoiceRow(
                                "ReplayGain",
                                listOf("Off" to ReplayGainMode.OFF, "Track" to ReplayGainMode.TRACK, "Album" to ReplayGainMode.ALBUM),
                                settings.replayGainMode
                            ) { scope.launch { store.setReplayGainMode(it) } }
                        }
                    }
                )
            )
            SettingsNote(
                "Crossfade overlaps songs for the chosen time.\n" +
                    "Smart Fades skips the fade between songs of the same album.\n" +
                    "ReplayGain evens out loudness (only softer, never louder).\n" +
                    "Speed and sleep timer are in the ... menu of the player."
            )
            SettingsGroup(
                null,
                listOf(
                    {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Volume limit", modifier = Modifier.weight(1f))
                                Text(
                                    if (settings.volumeLimitPercent >= 100) "Off" else "${settings.volumeLimitPercent}%",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // 20 to 100 in steps of 10, 100 means no limit
                            Slider(
                                value = settings.volumeLimitPercent.coerceIn(20, 100).toFloat(),
                                onValueChange = { value ->
                                    val percent = (Math.round(value / 10f) * 10).coerceIn(20, 100)
                                    if (percent != settings.volumeLimitPercent) {
                                        scope.launch { store.setVolumeLimitPercent(percent) }
                                    }
                                },
                                valueRange = 20f..100f,
                                steps = 7
                            )
                        }
                    },
                    {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Private mode", modifier = Modifier.weight(1f))
                            Switch(
                                checked = settings.privateMode,
                                onCheckedChange = { scope.launch { store.setPrivateMode(it) } }
                            )
                        }
                    }
                )
            )
            SettingsNote(
                "Volume limit caps the volume of this phone (not AirPlay).\n" +
                    "Private mode: plays are not reported to your server."
            )
            SettingsSectionTitle("Learned taste")
            SettingsNote("AutoPlay and the Discovery Station learn from what you play and skip. This stays on this phone.", Modifier.padding(bottom = 0.dp))
            TextButton(
                onClick = { TasteStore.get(context).clear() },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) { Text("Forget what was learned") }
            Spacer(Modifier.height(LocalBottomOverlay.current))
        }
    }
}

/** A label with a row of choices, the selected one filled. */
@Composable
private fun <T> ChoiceRow(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Text(label, modifier = Modifier.padding(bottom = 4.dp))
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
