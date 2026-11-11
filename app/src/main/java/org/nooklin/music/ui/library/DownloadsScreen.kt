package org.nooklin.music.ui.library

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.music.data.download.DownloadJob
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.data.download.JobState
import org.nooklin.music.data.library.Song
import org.nooklin.music.ui.common.GlassPill
import org.nooklin.music.ui.common.LocalBottomOverlay

/** The queue, the songs on the device, and the Wi-Fi option. A tap on a downloaded song plays it, also without a server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit, onPlay: (List<Song>, Int, Boolean) -> Unit) {
    val context = LocalContext.current
    val downloads = remember { DownloadManager.get(context) }
    val jobs by downloads.jobs.collectAsStateWithLifecycle()
    val done by downloads.downloaded.collectAsStateWithLifecycle()
    val paused by downloads.paused.collectAsStateWithLifecycle()
    val songs = done.values.map { it.song }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloaded Songs", fontSize = 34.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = LocalBottomOverlay.current)
        ) {
            if (jobs.isNotEmpty()) {
                item { SectionLabel("Queue") }
                item {
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        TextButton(onClick = { if (paused) downloads.resume() else downloads.pause() }) {
                            Text(if (paused) "Resume" else "Pause")
                        }
                        TextButton(onClick = downloads::cancelAll) { Text("Cancel all") }
                    }
                }
                items(jobs, key = { it.song.id }) { job ->
                    JobRow(job, paused) { downloads.cancel(job.song.id) }
                }
            }

            item {
                SectionLabel(
                    if (songs.isEmpty()) "Downloaded"
                    else "Downloaded · ${songs.size} songs · ${sizeText(done.values.sumOf { it.bytes })}"
                )
            }
            if (songs.isEmpty()) {
                item {
                    Text(
                        "Nothing downloaded yet. Use Download on an album or a playlist, or in the ... menu of a song.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            } else {
                item {
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        TextButton(onClick = { onPlay(songs, 0, false) }) { Text("Play") }
                        TextButton(onClick = { onPlay(songs, 0, true) }) { Text("Shuffle") }
                        TextButton(onClick = { confirmDeleteAll = true }) { Text("Delete all") }
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    MediaRow(
                        title = song.title,
                        subtitle = song.artist,
                        coverId = song.coverArt,
                        starred = null,
                        onToggleStar = {},
                        onClick = { onPlay(songs, index, false) },
                        menu = {
                            IconButton(onClick = { downloads.remove(song.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove download")
                            }
                        }
                    )
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            shape = GlassPill.shape,
            containerColor = GlassPill.adaptivePopupBackground,
            title = { Text("Delete all downloads?") },
            text = { Text("The songs stay on the server. Only the copies on this device are removed.") },
            confirmButton = {
                TextButton(onClick = { downloads.removeAll(); confirmDeleteAll = false }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun JobRow(job: DownloadJob, paused: Boolean, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(job.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    job.state == JobState.FAILED -> job.message
                    paused -> "Paused"
                    job.state == JobState.ACTIVE ->
                        if (job.total > 0) "Downloading ${sizeText(job.received)} of ${sizeText(job.total)}"
                        else "Downloading ${sizeText(job.received)}"
                    job.state == JobState.WAITING_WIFI -> "Waiting for Wi-Fi"
                    job.state == JobState.WAITING_NETWORK -> "Waiting for a network"
                    else -> "Waiting in the queue"
                },
                color = if (job.state == JobState.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (job.state == JobState.ACTIVE && !paused && job.total > 0) {
                LinearProgressIndicator(
                    progress = { (job.received.toFloat() / job.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel download") }
    }
}

private fun sizeText(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)
