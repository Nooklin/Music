package org.nooklin.music.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The notice shown when AutoMix is switched on. [onDone] gets true when "Don't show this again" is ticked. */
@Composable
fun AutoMixNoticeDialog(onDone: (hideNextTime: Boolean) -> Unit) {
    var hide by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDone(hide) },
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text("AutoMix") },
        text = {
            Column {
                Text(
                    "AutoMix works best with downloaded songs. A song that is not downloaded is first loaded in full from your server, which uses data."
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clickable(role = Role.Checkbox) { hide = !hide },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = hide, onCheckedChange = { hide = it })
                    Text("Don't show this again", modifier = Modifier.padding(start = 4.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(hide) }) { Text("OK") } }
    )
}
