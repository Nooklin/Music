package org.nooklin.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Whether the settings icons sit on a coloured tile (Appearance > Colorful display). */
val LocalColorful = staticCompositionLocalOf { true }

// The tile colours of the iOS settings look
object Tile {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF453A)
    val Purple = Color(0xFFBF5AF2)
    val Gray = Color(0xFF8E8E93)
}

/** The icon of a settings row: white on a coloured rounded tile, or the plain icon when the colourful display is off. */
@Composable
fun SettingsIcon(icon: ImageVector, tint: Color, enabled: Boolean = true) {
    val alpha = if (enabled) 1f else 0.38f
    if (LocalColorful.current) {
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .size(30.dp)
                .alpha(alpha)
                .clip(RoundedCornerShape(8.dp))
                .background(tint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    } else {
        Icon(
            icon,
            contentDescription = null,
            tint = LocalContentColor.current.copy(alpha = alpha),
            modifier = Modifier.padding(end = 16.dp)
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
}

// RadioButton keeps a real onClick: with null the dot didn't visually update on a device. Touches go to the innermost node, so no double toggle.
@Composable
fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.RadioButton) { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontFamily = fontFamily, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    icon: ImageVector,
    tint: Color = Tile.Blue,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Switch, enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon, tint, enabled)
            Text(label, color = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** The short explanation under a group: a few short lines, one per line. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        modifier = modifier.padding(horizontal = 28.dp, vertical = 12.dp)
    )
}

/** A label on the left and a value on the right, in a settings group. */
@Composable
fun SettingsValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Text(value, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
