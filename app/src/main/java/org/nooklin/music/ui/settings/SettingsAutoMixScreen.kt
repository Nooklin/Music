package org.nooklin.music.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import org.nooklin.music.playback.mix.MixLibraryScan
import org.nooklin.music.playback.mix.NativeMix
import org.nooklin.music.ui.common.AutoMixNoticeDialog
import org.nooklin.music.ui.common.LocalBottomOverlay

/** The AutoMix options: AutoMix itself, beat matching, and the scan of the downloaded songs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAutoMixScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PlaybackSettings(context.applicationContext) }
    val settings by store.values.collectAsStateWithLifecycle(PlaybackSettingsValues())
    val scope = rememberCoroutineScope()
    val progress by remember { MixLibraryScan.progress(context) }.collectAsStateWithLifecycle(0 to 0)
    val running by MixLibraryScan.running.collectAsStateWithLifecycle()
    var showNotice by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    val (scanned, total) = progress

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoMix", fontSize = 34.sp, fontWeight = FontWeight.Bold) },
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
                listOf(
                    {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("AutoMix", modifier = Modifier.weight(1f))
                            Switch(
                                checked = settings.autoMix,
                                onCheckedChange = { on ->
                                    if (on && !settings.autoMixNoticeHidden) showNotice = true
                                    scope.launch { store.setAutoMix(on) }
                                }
                            )
                        }
                    },
                    {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Beat matching", modifier = Modifier.weight(1f))
                            Switch(
                                checked = settings.beatMix,
                                onCheckedChange = { scope.launch { store.setBeatMix(it) } }
                            )
                        }
                    },
                    {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Separate vocals", modifier = Modifier.weight(1f))
                            Switch(
                                checked = settings.stemMix,
                                onCheckedChange = { scope.launch { store.setStemMix(it) } }
                            )
                        }
                    }
                )
            )
            SettingsNote(
                "AutoMix mixes each song into the next. Hold the AutoMix button in the player to pick a mode.\n" +
                    "Beat matching lines up the tempo of two songs while they overlap.\n" +
                    "Separate vocals keeps two singers from overlapping in a change.\n" +
                    "More under About > How to Use."
            )
            SettingsSectionTitle("Scan library")
            SettingsGroup(
                null,
                listOf {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            when {
                                total == 0 -> "No downloaded songs"
                                else -> "$scanned of $total downloaded songs scanned"
                            }
                        )
                        if (total > 0) {
                            LinearProgressIndicator(
                                progress = { scanned.toFloat() / total },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        }
                        Button(
                            onClick = { if (running) MixLibraryScan.stop() else MixLibraryScan.start(context) },
                            enabled = NativeMix.available && (running || scanned < total),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(if (running) "Stop" else "Scan library")
                        }
                        OutlinedButton(
                            onClick = { confirmWipe = true },
                            enabled = scanned > 0,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Delete scan data")
                        }
                    }
                }
            )
            SettingsNote(
                "Reads the tempo, key and beat of your downloaded songs ahead of time. " +
                    "Songs not scanned yet are scanned when they come up. The scan runs while the app is open."
            )
            Spacer(Modifier.height(LocalBottomOverlay.current))
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete scan data?") },
            text = { Text("The scan data of all songs is deleted. Songs are scanned again when needed.") },
            confirmButton = { TextButton(onClick = { confirmWipe = false; MixLibraryScan.wipe(context) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } }
        )
    }

    if (showNotice) {
        AutoMixNoticeDialog { hide ->
            showNotice = false
            if (hide) scope.launch { store.setAutoMixNoticeHidden(true) }
        }
    }
}
