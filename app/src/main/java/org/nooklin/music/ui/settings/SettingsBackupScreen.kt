package org.nooklin.music.ui.settings

import org.nooklin.music.ui.common.NooklinTopBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.nooklin.music.data.backup.BackupCrypto
import org.nooklin.music.data.local.AppPreferences
import org.nooklin.music.data.playback.PlaybackSettings
import org.nooklin.music.ui.common.GlassPill
import org.nooklin.music.ui.common.LocalBottomOverlay

private const val MIN_PASSWORD = 8
private const val MAX_BACKUP_BYTES = 1_000_000

/**
 * Backs up the settings to a file, encrypted with a password, and restores them from such a file. The server details
 * and the server password are not in the backup. The file goes where the user picks it (system file picker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }
    val playback = remember { PlaybackSettings(context) }
    var askExportPassword by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = exportPassword
        exportPassword = null
        if (uri != null && password != null) scope.launch {
            busy = true
            val ok = withContext(Dispatchers.Default) {
                try {
                    val json = JSONObject()
                        .put("version", 1)
                        .put("app", preferences.exportBackup())
                        .put("playback", playback.exportBackup())
                    val encrypted = BackupCrypto.encrypt(json.toString().toByteArray(Charsets.UTF_8), password)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(encrypted) } != null
                } catch (e: Exception) {
                    false
                }
            }
            busy = false
            message = if (ok) "Backup saved." else "Could not save the backup."
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri = uri
    }

    Scaffold(
        topBar = {
            NooklinTopBar("Backup", onBack)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(
                null,
                listOf(
                    { SettingsNavRow("Back up settings", Icons.Filled.Lock, Tile.Green) { if (!busy) askExportPassword = true } },
                    { SettingsNavRow("Restore settings", Icons.Filled.LockOpen, Tile.Orange) { if (!busy) openLauncher.launch(arrayOf("*/*")) } }
                )
            )
            Text(
                message ?: "A backup holds your look and player settings, not your server details. It is locked with the password you choose.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
            )
            Spacer(Modifier.height(LocalBottomOverlay.current))
        }
    }

    if (askExportPassword) {
        PasswordDialog(
            title = "Choose a password",
            confirmField = true,
            onDismiss = { askExportPassword = false },
            onDone = { password ->
                askExportPassword = false
                exportPassword = password
                createLauncher.launch("nooklin-music-settings.nkbackup")
            }
        )
    }
    importUri?.let { uri ->
        PasswordDialog(
            title = "Password of the backup",
            confirmField = false,
            onDismiss = { importUri = null },
            onDone = { password ->
                importUri = null
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        try {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            if (bytes == null || bytes.size > MAX_BACKUP_BYTES) return@withContext "Could not read this file."
                            val plain = BackupCrypto.decrypt(bytes, password) ?: return@withContext "Wrong password, or this is not a backup of Nooklin Music."
                            val json = JSONObject(String(plain, Charsets.UTF_8))
                            if (json.optInt("version") != 1) return@withContext "This backup is from a version this app does not know."
                            json.optJSONObject("app")?.let { preferences.importBackup(it) }
                            json.optJSONObject("playback")?.let { playback.importBackup(it) }
                            "Settings restored."
                        } catch (e: Exception) {
                            "Could not restore this backup."
                        }
                    }
                    busy = false
                    message = result
                }
            }
        )
    }
}

@Composable
private fun PasswordDialog(title: String, confirmField: Boolean, onDismiss: () -> Unit, onDone: (CharArray) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val tooShort = confirmField && first.isNotEmpty() && first.length < MIN_PASSWORD
    val mismatch = confirmField && second.isNotEmpty() && first != second
    val valid = if (confirmField) first.length >= MIN_PASSWORD && first == second else first.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it },
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = tooShort,
                    supportingText = if (confirmField) ({ Text("At least $MIN_PASSWORD characters. It cannot be recovered.") }) else null
                )
                if (confirmField) {
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        singleLine = true,
                        label = { Text("Repeat password") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = mismatch,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onDone(first.toCharArray()) }) { Text(if (confirmField) "Continue" else "Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
