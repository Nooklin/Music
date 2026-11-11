package org.nooklin.music.ui.settings

import org.nooklin.music.ui.common.NooklinTopBar
import android.os.StatFs
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.data.library.CoverCache
import org.nooklin.music.data.library.CoverDisk
import org.nooklin.music.ui.common.LocalBottomOverlay

/** The options for downloads. The queue and the downloaded songs are under Library, Downloads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val downloads = remember { DownloadManager.get(context) }
    val wifiOnly by downloads.wifiOnly.collectAsStateWithLifecycle()
    val autoFavorites by downloads.autoFavorites.collectAsStateWithLifecycle()
    val coverDisk = remember { CoverDisk.get(context) }
    var coverBytes by remember { mutableLongStateOf(coverDisk.size()) }
    val downloaded by downloads.downloaded.collectAsStateWithLifecycle()
    val limitMb by downloads.limitMb.collectAsStateWithLifecycle()
    var choosingLimit by remember { mutableStateOf(false) }
    var choosingAlbum by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingRemoval?>(null) }
    val songs = remember(downloaded) { downloaded.values.toList() }
    val totalBytes = remember(songs) { songs.sumOf { it.bytes } }
    val totalSeconds = remember(songs) { songs.sumOf { it.song.durationSeconds.toLong() } }
    val albumCount = remember(songs) { songs.map { it.song.album.lowercase() to it.song.artist.lowercase() }.distinct().size }
    val artistCount = remember(songs) { songs.map { it.song.artist.lowercase() }.distinct().size }
    val unhearted = remember(songs) { songs.filter { !it.song.starred } }
    // The albums that take the most room, as a group of songs each
    val bigAlbums = remember(songs) {
        songs.groupBy { it.song.album.ifEmpty { "Unknown album" } to it.song.artist }
            .entries.sortedByDescending { e -> e.value.sumOf { it.bytes } }.take(8)
    }
    val freeBytes = remember(songs) { runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L) }

    Scaffold(
        topBar = {
            NooklinTopBar("Downloads", onBack)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(
                "Overview",
                listOf(
                    { SettingsValueRow("Songs", "%,d".format(songs.size)) },
                    { SettingsValueRow("Size", formatSize(totalBytes)) },
                    { SettingsValueRow("Playing time", formatTime(totalSeconds)) },
                    { SettingsValueRow("Albums", "%,d".format(albumCount)) },
                    { SettingsValueRow("Artists", "%,d".format(artistCount)) },
                    { SettingsValueRow("Free on this phone", formatSize(freeBytes)) }
                )
            )
            SettingsGroup(
                "Download",
                listOf(
                    {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Only download on Wi-Fi", modifier = Modifier.weight(1f))
                            Switch(checked = wifiOnly, onCheckedChange = downloads::setWifiOnly)
                        }
                    },
                    {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Download favourite songs", modifier = Modifier.weight(1f))
                            Switch(checked = autoFavorites, onCheckedChange = downloads::setAutoFavorites)
                        }
                    },
                    { SettingsNavRowValue("Storage limit", if (limitMb == 0) "Off" else formatSize(limitMb * 1_048_576L)) { choosingLimit = true } }
                )
            )
            SettingsNote(
                "Favourite songs are downloaded by themselves and come back if you remove them.\n" +
                    "At the storage limit the oldest songs without a heart are removed to make room."
            )
            SettingsGroup(
                "Manage",
                listOf(
                    {
                        SettingsActionRow("Remove songs without a heart", "${unhearted.size} | ${formatSize(unhearted.sumOf { it.bytes })}", enabled = unhearted.isNotEmpty()) {
                            pending = PendingRemoval("Remove ${unhearted.size} songs without a heart?", unhearted.map { it.song.id })
                        }
                    },
                    {
                        SettingsActionRow("Remove an album", "Largest first", enabled = bigAlbums.isNotEmpty()) { choosingAlbum = true }
                    },
                    {
                        SettingsActionRow("Remove all downloads", formatSize(totalBytes), enabled = songs.isNotEmpty(), destructive = true) {
                            pending = PendingRemoval("Remove all ${songs.size} downloads?", songs.map { it.song.id })
                        }
                    }
                )
            )
            SettingsGroup(
                "Covers",
                listOf({
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable {
                                coverDisk.clear()
                                CoverCache.clear()
                                coverBytes = coverDisk.size()
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Clear saved covers", modifier = Modifier.weight(1f))
                        Text(
                            "%.1f MB".format(coverBytes / 1_048_576.0),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                })
            )
            SettingsNote("Covers are saved so they are fetched once (150 MB at most). Clear them if a cover still shows the old image.")
            Spacer(Modifier.height(LocalBottomOverlay.current))
        }
    }
    if (choosingLimit) {
        AlertDialog(
            onDismissRequest = { choosingLimit = false },
            title = { Text("Storage limit") },
            text = {
                Column {
                    LIMITS_MB.forEach { mb ->
                        SettingsRadioRow(if (mb == 0) "Off" else formatSize(mb * 1_048_576L), limitMb == mb) {
                            downloads.setLimitMb(mb)
                            choosingLimit = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingLimit = false }) { Text("Close") } }
        )
    }
    if (choosingAlbum) {
        AlertDialog(
            onDismissRequest = { choosingAlbum = false },
            title = { Text("Remove an album") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    bigAlbums.forEach { (key, list) ->
                        SettingsValueRowClickable(key.first, formatSize(list.sumOf { it.bytes })) {
                            choosingAlbum = false
                            pending = PendingRemoval("Remove ${list.size} songs of ${key.first}?", list.map { it.song.id })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingAlbum = false }) { Text("Close") } }
        )
    }
    pending?.let { removal ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(removal.question) },
            text = { Text("The files are deleted from this phone. The songs stay on your server.") },
            confirmButton = {
                TextButton(onClick = { downloads.removeMany(removal.ids); pending = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}

private val LIMITS_MB = listOf(0, 1024, 2048, 5120, 10240, 20480, 51200)

private class PendingRemoval(val question: String, val ids: List<String>)

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    else -> "%.0f MB".format(bytes / 1_048_576.0)
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h} h ${m} min" else "${m} min"
}

/** A tappable settings row with a value on the right; [destructive] colours the label as a warning. */
@Composable
private fun SettingsActionRow(label: String, value: String, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    val base = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val alpha = if (enabled) 1f else 0.38f
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = enabled) { onClick() }.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = base.copy(alpha = alpha), modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
    }
}

@Composable
private fun SettingsNavRowValue(label: String, value: String, onClick: () -> Unit) = SettingsActionRow(label, value, onClick = onClick)

@Composable
private fun SettingsValueRowClickable(label: String, value: String, onClick: () -> Unit) = SettingsActionRow(label, value, onClick = onClick)
