package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.player.components.HosterPriorityList
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun HosterPriorityDialog(
    hosterNames: List<String>,
    disabledNames: Set<String>,
    onConfirm: (List<String>, Set<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var order by remember { mutableStateOf(hosterNames) }
    var disabled by remember { mutableStateOf(disabledNames) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(AYMR.strings.pref_hosters_priority)) },
        text = {
            Column {
                Text(
                    text = stringResource(AYMR.strings.player_hoster_priority_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
                )
                HosterPriorityList(
                    order = order,
                    disabled = disabled,
                    onOrderChange = { order = it },
                    onDisabledChange = { disabled = it },
                    modifier = Modifier.heightIn(max = 400.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(order, disabled) }) {
                Text(text = stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
