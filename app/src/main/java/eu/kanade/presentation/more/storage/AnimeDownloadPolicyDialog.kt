package eu.kanade.presentation.more.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.storage.anime.AnimeStorageDetailViewModel
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * A show's own cleanup policy, with what saving it would delete and download now.
 */
@Composable
fun AnimeDownloadPolicyDialog(
    dialog: AnimeStorageDetailViewModel.PolicyDialog,
    onChange: (tachiyomi.domain.download.anime.model.AnimeDownloadPolicyOverride) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
) {
    val override = dialog.override
    val default = dialog.default
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(TLMR.strings.action_cleanup_policy)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                Text(
                    text = stringResource(TLMR.strings.policy_delete_after_watching),
                    style = MaterialTheme.typography.header,
                )
                val defaultDelete =
                    stringResource(if (default.deleteAfterSeen) TLMR.strings.policy_on else TLMR.strings.policy_off)
                RadioItem(
                    label = stringResource(TLMR.strings.policy_default_value, defaultDelete),
                    selected = override.deleteAfterSeen == null,
                    onClick = { onChange(override.copy(deleteAfterSeen = null)) },
                )
                RadioItem(
                    label = stringResource(TLMR.strings.policy_on),
                    selected = override.deleteAfterSeen == true,
                    onClick = { onChange(override.copy(deleteAfterSeen = true)) },
                )
                RadioItem(
                    label = stringResource(TLMR.strings.policy_never),
                    selected = override.deleteAfterSeen == false,
                    onClick = { onChange(override.copy(deleteAfterSeen = false)) },
                )
                if (default.protected) {
                    Text(
                        text = stringResource(TLMR.strings.policy_excluded_category),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.secondaryItemAlpha(),
                    )
                }

                Text(text = stringResource(TLMR.strings.policy_keep_unseen), style = MaterialTheme.typography.header)
                val defaultKeep = keepUnseenLabel(default.keepUnseen)
                RadioItem(
                    label = stringResource(TLMR.strings.policy_default_value, defaultKeep),
                    selected = override.keepUnseen == null,
                    onClick = { onChange(override.copy(keepUnseen = null)) },
                )
                KEEP_UNSEEN_CHOICES.forEach { count ->
                    RadioItem(
                        label = keepUnseenLabel(count),
                        selected = override.keepUnseen == count,
                        onClick = { onChange(override.copy(keepUnseen = count)) },
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = MaterialTheme.padding.small))
                val preview = dialog.preview
                when {
                    preview == null -> LinearProgressIndicator(Modifier.fillMaxWidth())

                    preview.plan.isEmpty -> Text(text = stringResource(TLMR.strings.policy_preview_none))

                    else -> {
                        CleanupPlanSummary(preview)
                        CleanupPlanList(preview, Modifier.heightIn(max = 200.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = dialog.preview != null) {
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

@Composable
private fun keepUnseenLabel(count: Int): String {
    return if (count <= 0) {
        stringResource(TLMR.strings.policy_off)
    } else {
        pluralStringResource(TLMR.plurals.policy_keep_unseen_count, count, count)
    }
}

/** The choices of "keep the next unseen episodes downloaded", also in Settings > Downloads. */
val KEEP_UNSEEN_CHOICES = listOf(0, 1, 2, 3, 5, 10)
