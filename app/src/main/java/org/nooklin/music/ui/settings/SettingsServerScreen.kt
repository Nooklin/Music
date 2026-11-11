package org.nooklin.music.ui.settings

import org.nooklin.music.ui.common.NooklinTopBar
import androidx.compose.ui.unit.sp
import org.nooklin.music.ui.common.LocalBottomOverlay
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import org.nooklin.music.ui.tour.TourTargets
import org.nooklin.music.data.server.PingResult
import org.nooklin.music.ui.common.GlassPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsServerScreen(
    state: ServerUiState,
    onNameChange: (String) -> Unit,
    onPrimaryUrlChange: (String) -> Unit,
    onSecondaryUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAllowHttpChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var showHttpWarning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NooklinTopBar("Server", onBack)
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Nothing is shown until the saved values are loaded, so an early keystroke cannot be overwritten
            if (!state.loaded) return@Column

            if (state.hasSavedPassword && state.primaryUrl.isNotBlank()) {
                ConnectionStatus(state.live, onRefresh = onRefresh)
            }

            SettingsGroup(
                "Address",
                listOf(
                    {
                        ServerTextField(
                            label = "Server name (optional)",
                            value = state.name,
                            onValueChange = onNameChange,
                            placeholder = "Shown at the top of Home",
                            keyboardType = KeyboardType.Text
                        )
                    },
                    {
                        ServerTextField(
                            label = "Main address",
                            value = state.primaryUrl,
                            onValueChange = onPrimaryUrlChange,
                            placeholder = "https://music.example.com",
                            keyboardType = KeyboardType.Uri
                        )
                    },
                    {
                        ServerTextField(
                            label = "Second address (optional)",
                            value = state.secondaryUrl,
                            onValueChange = onSecondaryUrlChange,
                            placeholder = "https://192.168.1.10:4533",
                            keyboardType = KeyboardType.Uri
                        )
                    }
                )
            )
            CaptionText(
                "Start with https:// or http://. Test connection checks both addresses."
            )

            SettingsGroup(
                "Account",
                listOf(
                    {
                        ServerTextField(
                            label = "Username",
                            value = state.username,
                            onValueChange = onUsernameChange,
                            keyboardType = KeyboardType.Text
                        )
                    },
                    {
                        ServerTextField(
                            label = "Password",
                            value = state.password,
                            onValueChange = onPasswordChange,
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                            supportingText = if (state.hasSavedPassword) {
                                "A password is saved. Leave this empty to keep it."
                            } else {
                                null
                            }
                        )
                    }
                )
            )
            CaptionText(
                "The password is stored encrypted on this phone and is never sent as it is."
            )

            SettingsGroup(
                null,
                listOf({
                    SettingsToggleRow("Allow http", state.allowHttp, Icons.Filled.Warning, Tile.Orange) { wanted ->
                        if (wanted) showHttpWarning = true else onAllowHttpChange(false)
                    }
                })
            )
            CaptionText("Off by default. Only for a network you trust, such as your home network.")

            PillButton(if (state.testing) "Testing..." else "Test connection", enabled = !state.testing, onClick = onTest)
            PillButton("Save", enabled = !state.testing, onClick = onSave)
            // While the tour card sits over this screen, the end of the form can scroll clear of it
            val covered = with(LocalDensity.current) { TourTargets.coveredBottomPx.toDp() }
            if (covered > 0.dp) Spacer(Modifier.height(covered + 48.dp))

            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            state.info?.let {
                Text(it, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
            state.results.forEach { item ->
                Text(
                    describe(item),
                    color = if (item.result is PingResult.Failure) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }
            if (state.results.isNotEmpty()) {
                val working = state.results.firstOrNull { it.result is PingResult.Success }
                Text(
                    if (working != null) "Works: ${working.label}." else "No address works yet.",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(LocalBottomOverlay.current))
        }
    }

    if (showHttpWarning) {
        AlertDialog(
            onDismissRequest = { showHttpWarning = false },
            shape = GlassPill.shape,
            containerColor = GlassPill.adaptivePopupBackground,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Allow http?") },
            text = {
                Text(
                    "With http nothing is encrypted. Anyone on the same network can read what the app sends and use your login. " +
                        "Only switch this on for a network you trust."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAllowHttpChange(true)
                    showHttpWarning = false
                }) { Text("Allow http") }
            },
            dismissButton = {
                TextButton(onClick = { showHttpWarning = false }) { Text("Cancel") }
            }
        )
    }
}

private fun describe(item: AddressTestResult): String {
    return when (val result = item.result) {
        is PingResult.Success -> {
            val type = result.info.serverType.ifEmpty { "Subsonic server" }.replaceFirstChar { it.uppercase() }
            val version = if (result.info.serverVersion.isNotEmpty()) " ${result.info.serverVersion}" else ""
            "${item.label}: connected to $type$version, Subsonic API ${result.info.apiVersion}."
        }
        is PingResult.Failure -> "${item.label}: ${result.message}"
    }
}

@Composable
private fun CaptionText(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun ServerTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    placeholder: String? = null,
    isPassword: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        shape = GlassPill.shape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
    )
}

@Composable
private fun PillButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(GlassPill.shape)
            .background(GlassPill.adaptiveGlassBackground)
            .border(GlassPill.adaptiveBorder, GlassPill.shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}

/** The state of the saved server: a dot, the answer time, and numbers about the library. */
@Composable
private fun ConnectionStatus(live: ServerLive, onRefresh: () -> Unit) {
    val green = androidx.compose.ui.graphics.Color(0xFF34C759)
    val dot = when {
        live.checking && !live.online -> MaterialTheme.colorScheme.onSurfaceVariant
        live.online -> green
        else -> MaterialTheme.colorScheme.error
    }
    val title = when {
        live.checking && !live.online -> "Checking..."
        live.online -> "Connected"
        else -> "Not reachable"
    }
    val detail = when {
        live.online -> listOfNotNull(
            "${live.responseMs} ms",
            (live.type.ifEmpty { "Subsonic" }.replaceFirstChar { it.uppercase() } + live.version.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()),
            live.apiVersion.takeIf { it.isNotEmpty() }?.let { "API $it" }
        ).joinToString("  |  ")
        live.error != null -> live.error
        else -> null
    }
    SettingsGroup(
        "Connection",
        listOf({
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = "Check again") { onRefresh() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (live.checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = "Check again", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        })
    )
    if (live.online) {
        val rows = listOfNotNull(
            live.songs?.let { "Songs" to it },
            live.albums?.let { "Albums" to it },
            live.artists?.let { "Artists" to it },
            live.playlists?.let { "Playlists" to it }
        )
        if (rows.isNotEmpty()) {
            val items: List<@Composable () -> Unit> = buildList { rows.forEach { (label, n) -> add { SettingsValueRow(label, "%,d".format(n)) } } }
            SettingsGroup("Library", items)
        }
        val hearts = listOfNotNull(
            live.heartSongs?.let { "Songs with a heart" to it },
            live.heartAlbums?.let { "Albums with a heart" to it },
            live.heartArtists?.let { "Artists with a heart" to it }
        )
        if (hearts.isNotEmpty()) {
            val items: List<@Composable () -> Unit> = buildList { hearts.forEach { (label, n) -> add { SettingsValueRow(label, "%,d".format(n)) } } }
            SettingsGroup("Your hearts", items)
        }
    }
}
