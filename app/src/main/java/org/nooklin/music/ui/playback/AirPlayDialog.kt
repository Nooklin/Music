package org.nooklin.music.ui.playback

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import android.content.IntentFilter
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.nooklin.music.airplay.AirPlayController
import org.nooklin.music.playback.AudioRoute
import org.nooklin.music.ui.common.GlassPill

/**
 * Pick where the sound goes: the phone speaker, one Bluetooth device, or AirPlay speakers (one or more). The phone is
 * silent while at least one AirPlay speaker is connected.
 */
@Composable
fun AirPlayDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val controller = AirPlayController.get(context)
    DisposableEffect(Unit) {
        controller.startScan()
        onDispose { controller.stopScan() }
    }
    val state by controller.state.collectAsState()

    // The outputs of the phone, kept up to date while the menu is open
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var devices by remember { mutableStateOf(AudioRoute.outputs(context)) }
    DisposableEffect(Unit) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                devices = AudioRoute.outputs(context)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                devices = AudioRoute.outputs(context)
            }
        }
        audio.registerAudioDeviceCallback(callback, null)
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    val chosen by AudioRoute.selectedId.collectAsState()

    // The names of Bluetooth devices need this permission; it is asked once when the menu opens
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        devices = AudioRoute.outputs(context)
    }
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    val prompt = state.pin
    if (prompt != null) {
        var code by remember(prompt.attempt) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { controller.cancelPin() },
            title = { Text("Pair with ${prompt.name}") },
            text = {
                Column {
                    Text("Type the code shown on ${prompt.name}.")
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(8) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { controller.submitPin(code) }, enabled = code.length >= 4) { Text("Pair") }
            },
            dismissButton = { TextButton(onClick = { controller.cancelPin() }) { Text("Cancel") } }
        )
        return
    }

    val speaker = AudioRoute.speaker(devices)
    val bluetooth = devices.filter { AudioRoute.isBluetooth(it) }
    // Chosen is read here so the menu follows a change of the choice (chosen has no other use)
    val activeId = remember(devices, chosen) { AudioRoute.activeId(context, devices) }

    // The volume of the phone or the Bluetooth device is the music volume of Android
    val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var systemVolume by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    // 0.0.141: the bars follow the volume keys of the phone: the music volume of Android is read again when it changes
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) == AudioManager.STREAM_MUSIC) {
                    systemVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), Context.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val setSystemVolume = { v: Float ->
        systemVolume = v
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVolume).roundToInt(), 0)
    }

    // 0.0.160: wider than the standard dialog width: 24 dp from the edge of the screen on both sides, the width of the cover on the Now playing screen
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = GlassPill.shape,
            color = GlassPill.adaptivePopupBackground,
            contentColor = MaterialTheme.colorScheme.onSurface,  // 0.0.135: without it the text took the default (black in black mode)
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Text(
                    "Play on",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp)
                )
                Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val phoneSelected = !state.active && bluetooth.none { it.id == activeId }
                    DevicePill(
                        icon = Icons.Rounded.PhoneAndroid,
                        name = "Phone Speaker",
                        subtitle = null,
                        selected = phoneSelected,
                        busy = false,
                        volume = if (phoneSelected) systemVolume else null,
                        onVolume = setSystemVolume,
                        onClick = {
                            if (state.active) controller.stopAll()
                            AudioRoute.select(speaker?.id)
                        }
                    )

                    SectionLabel("Bluetooth")
                    if (bluetooth.isEmpty()) {
                        Text(
                            "No Bluetooth audio device is connected.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    bluetooth.forEach { device ->
                        val selected = !state.active && activeId == device.id
                        DevicePill(
                            icon = Icons.Rounded.Bluetooth,
                            name = device.productName.toString().ifBlank { "Bluetooth device" },
                            subtitle = null,
                            selected = selected,
                            busy = false,
                            volume = if (selected) systemVolume else null,
                            onVolume = setSystemVolume,
                            onClick = {
                                if (state.active) controller.stopAll()
                                AudioRoute.select(device.id)
                            }
                        )
                    }

                    SectionLabel("AirPlay")
                    if (state.rows.isEmpty()) {
                        Text(
                            if (state.scanning) "Looking for speakers on this Wi-Fi network..." else "No speakers found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    state.rows.forEach { row ->
                        // Starts again from the speaker whenever its volume changes (volume keys), the drag still moves it at once
                        var volume by remember(row.key, row.volume) { mutableStateOf(row.volume / 100f) }
                        DevicePill(
                            icon = Icons.Rounded.Airplay,
                            name = row.name,
                            subtitle = row.subtitle,
                            selected = row.connected,
                            busy = row.connecting,
                            volume = if (row.connected) volume else null,
                            onVolume = {
                                volume = it
                                controller.setVolume(row.key, (it * 100).toInt())
                            },
                            onClick = { if (!row.connecting) controller.toggle(row.key) }
                        )
                    }
                    state.message?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (state.active) {
                        Text(
                            "Playback is delayed by about two seconds on the speakers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
    )
}

/**
 * One output as a pill. A selected pill is filled from the left with its volume ([volume], 0 to 1; null fills it
 * completely): drag over it to set the volume. A tap selects the output, or for AirPlay switches it off again.
 */
@Composable
private fun DevicePill(
    icon: ImageVector,
    name: String,
    subtitle: String?,
    selected: Boolean,
    busy: Boolean,
    volume: Float?,
    onVolume: (Float) -> Unit,
    onClick: () -> Unit
) {
    var width by remember { mutableStateOf(1) }
    val adjustable = selected && volume != null
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)  // 0.0.160: was 60 dp
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
            .border(GlassPill.adaptiveBorder, RoundedCornerShape(50))
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .clickable(onClick = onClick)
            .pointerInput(adjustable) {
                if (adjustable) {
                    detectHorizontalDragGestures { change, _ -> onVolume((change.position.x / width).coerceIn(0f, 1f)) }
                }
            }
    ) {
        if (selected) {
            Box(
                Modifier
                    .fillMaxWidth((volume ?: 1f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            )
        }
        Row(
            Modifier.fillMaxSize().padding(start = 20.dp, end = if (adjustable) 8.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                adjustable -> {
                    // Buttons next to the drag: the volume can be set without a gesture
                    val level = volume ?: 0f
                    IconButton(onClick = { onVolume((level - 0.1f).coerceIn(0f, 1f)) }) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Volume down", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { onVolume((level + 0.1f).coerceIn(0f, 1f)) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Volume up", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                selected -> Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
