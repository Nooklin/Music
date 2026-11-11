package org.nooklin.music.ui.settings

import org.nooklin.music.ui.common.NooklinTopBar
import androidx.compose.ui.unit.sp
import org.nooklin.music.ui.common.LocalBottomOverlay
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.nooklin.music.data.local.AppFont
import org.nooklin.music.data.local.AppThemeMode
import org.nooklin.music.data.local.ContrastColor
import org.nooklin.music.ui.theme.fontFamilyFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    contrastColor: ContrastColor,
    onContrastColorChange: (ContrastColor) -> Unit,
    appFont: AppFont,
    onAppFontChange: (AppFont) -> Unit,
    colorful: Boolean,
    onColorfulChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            NooklinTopBar("Appearance", onBack)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(
                "Theme",
                listOf(
                    AppThemeMode.SYSTEM to "Follow System",
                    AppThemeMode.LIGHT to "Light",
                    AppThemeMode.DARK to "Dark",
                    AppThemeMode.BLACK to "Black"
                ).map { (mode, label) ->
                    { SettingsRadioRow(label, themeMode == mode) { onThemeModeChange(mode) } }
                }
            )
            Text(
                "Black uses a true black background. It saves battery on OLED screens.",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            )

            SettingsGroup(
                null,
                listOf({ SettingsToggleRow("Colorful display", colorful, Icons.Filled.Palette, Tile.Purple, onCheckedChange = onColorfulChange) })
            )
            Text(
                "Shows the icons in the settings on coloured tiles.",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            )

            SettingsGroup(
                "Font",
                listOf(
                    AppFont.INTER to AppFont.INTER.label,
                    AppFont.SYSTEM_DEFAULT to AppFont.SYSTEM_DEFAULT.label,
                    AppFont.WORK_SANS to AppFont.WORK_SANS.label
                ).map { (font, label) ->
                    { SettingsRadioRow(label, appFont == font, fontFamily = fontFamilyFor(font)) { onAppFontChange(font) } }
                }
            )

            SettingsGroup(
                "Contrast Color",
                listOf({
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(ContrastColor.entries, key = { it.storageKey }) { option ->
                            ContrastColorSwatch(
                                option = option,
                                selected = option == contrastColor,
                                onClick = { onContrastColorChange(option) }
                            )
                        }
                    }
                })
            )
            Text(
                "Colours the buttons, accents and pills of the app.",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            )
            Spacer(Modifier.height(LocalBottomOverlay.current))
        }
    }
}

// DEFAULT is an outlined circle, not a filled swatch: it means "no tint"
@Composable
private fun ContrastColorSwatch(option: ContrastColor, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (option.color != null) {
                    Modifier.background(option.color)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                }
            )
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = if (option.color != null) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
