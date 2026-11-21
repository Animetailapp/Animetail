package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.player.components.HosterPriorityList
import eu.kanade.presentation.player.components.PlayerSheet
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Lets the user reorder the hosters of the current episode and turn some of them off.
 *
 * @param hosterNames the hosters in their current order.
 * @param onSave receives every hoster name, highest priority first, and the disabled ones.
 */
@Composable
fun HosterPrioritySheet(
    hosterNames: List<String>,
    disabledNames: Set<String>,
    onSave: (List<String>, Set<String>) -> Unit,
    onDismissRequest: () -> Unit,
    dismissSheet: Boolean,
    modifier: Modifier = Modifier,
) {
    var order by remember { mutableStateOf(hosterNames) }
    var disabled by remember { mutableStateOf(disabledNames) }

    PlayerSheet(
        onDismissRequest = onDismissRequest,
        dismissEvent = dismissSheet,
        modifier = modifier,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = MaterialTheme.padding.medium,
                        start = MaterialTheme.padding.medium,
                        end = MaterialTheme.padding.small,
                    ),
            ) {
                Text(
                    text = stringResource(AYMR.strings.pref_hosters_priority),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onSave(order, disabled) }) {
                    Text(text = stringResource(MR.strings.action_save))
                }
            }
            Text(
                text = stringResource(AYMR.strings.player_hoster_priority_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            )
            HosterPriorityList(
                order = order,
                disabled = disabled,
                onOrderChange = { order = it },
                onDisabledChange = { disabled = it },
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.padding.small,
                    vertical = MaterialTheme.padding.small,
                ),
            )
        }
    }
}
