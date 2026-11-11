package org.nooklin.music.ui.settings

import org.nooklin.music.ui.common.NooklinTopBar
import androidx.compose.ui.unit.sp
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nooklin.music.data.local.HomeSectionSetting
import org.nooklin.music.ui.common.LocalBottomOverlay

/** Which blocks the Home screen shows, and in which order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    sections: List<HomeSectionSetting>?,
    onChange: (List<HomeSectionSetting>) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            NooklinTopBar("Home", onBack)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            val list = sections.orEmpty()
            SettingsGroup(
                "Blocks on the Home screen",
                list.mapIndexed { index, item ->
                    {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = item.enabled,
                                onCheckedChange = { on ->
                                    onChange(list.toMutableList().also { it[index] = item.copy(enabled = on) })
                                }
                            )
                            Text(item.section.label, modifier = Modifier.weight(1f).padding(start = 16.dp))
                            IconButton(
                                enabled = index > 0,
                                onClick = { onChange(list.swapped(index, index - 1)) }
                            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                            IconButton(
                                enabled = index < list.lastIndex,
                                onClick = { onChange(list.swapped(index, index + 1)) }
                            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                        }
                    }
                }
            )
            Spacer(Modifier.height(LocalBottomOverlay.current))
        }
    }
}

private fun List<HomeSectionSetting>.swapped(a: Int, b: Int): List<HomeSectionSetting> =
    toMutableList().also { val moved = it[a]; it[a] = it[b]; it[b] = moved }
