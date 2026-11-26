package eu.kanade.presentation.more.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.anime.AnimeStorageCleaner
import eu.kanade.tachiyomi.util.toSize
import tachiyomi.domain.download.anime.service.PlannedDeletion
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * Shows what a cleanup would delete and download before it is applied.
 *
 * @param preview null while it is being worked out.
 * @param info what the cleanup is about, shown above the list.
 */
@Composable
fun StorageCleanupDialog(
    title: String,
    preview: AnimeStorageCleaner.CleanupPreview?,
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
    info: String? = null,
) {
    val plan = preview?.plan
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            if (preview == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    if (info != null) {
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.secondaryItemAlpha(),
                        )
                    }
                    CleanupPlanSummary(preview)
                    CleanupPlanList(preview, Modifier.heightIn(max = 320.dp))
                }
            }
        },
        confirmButton = {
            if (plan != null && !plan.isEmpty) {
                TextButton(onClick = onApply) {
                    Text(text = stringResource(MR.strings.action_apply))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

/** The totals of a cleanup: what it deletes and downloads, and whether it stays over the limit. */
@Composable
fun CleanupPlanSummary(preview: AnimeStorageCleaner.CleanupPreview, modifier: Modifier = Modifier) {
    val plan = preview.plan
    Column(modifier = modifier) {
        if (plan.isEmpty) {
            Text(text = stringResource(TLMR.strings.storage_cleanup_empty))
        }
        if (plan.deletions.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    TLMR.plurals.storage_cleanup_deletes,
                    plan.deletions.size,
                    plan.deletions.size,
                    plan.bytesFreed.toSize(),
                ),
            )
        }
        if (plan.downloads.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    TLMR.plurals.storage_cleanup_downloads,
                    plan.downloads.size,
                    plan.downloads.size,
                ),
            )
        }
        if (plan.bytesOverLimit > 0) {
            Text(
                text = stringResource(TLMR.strings.storage_cleanup_still_over, plan.bytesOverLimit.toSize()),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Every episode a cleanup deletes or downloads, with its show and why. */
@Composable
fun CleanupPlanList(preview: AnimeStorageCleaner.CleanupPreview, modifier: Modifier = Modifier) {
    val seen = stringResource(TLMR.strings.storage_cleanup_reason_seen)
    val limit = stringResource(TLMR.strings.storage_cleanup_reason_limit)
    val download = stringResource(TLMR.strings.storage_cleanup_download)
    val lines = remember(preview) {
        val showTitles = preview.shows.size > 1
        val deletions = preview.plan.deletions.map { deletion ->
            val show = preview.shows[deletion.animeId]
            val reason = when (deletion.reason) {
                PlannedDeletion.Reason.Seen -> seen
                PlannedDeletion.Reason.StorageLimit -> limit
            }
            PlanLine(
                title = show?.anime?.title.takeIf { showTitles },
                episode = show?.episodesById?.get(deletion.episodeId)?.name.orEmpty(),
                detail = "${deletion.size.toSize()} · $reason",
            )
        }
        val downloads = preview.plan.downloads.map { planned ->
            val show = preview.shows[planned.animeId]
            PlanLine(
                title = show?.anime?.title.takeIf { showTitles },
                episode = show?.episodesById?.get(planned.episodeId)?.name.orEmpty(),
                detail = download,
            )
        }
        deletions + downloads
    }
    if (lines.isEmpty()) return
    LazyColumn(modifier = modifier) {
        items(lines) { line ->
            Column(Modifier.padding(vertical = MaterialTheme.padding.extraSmall)) {
                if (line.title != null) {
                    Text(
                        text = line.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = line.episode,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = line.detail,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.secondaryItemAlpha(),
                )
            }
        }
    }
}

private data class PlanLine(
    val title: String?,
    val episode: String,
    val detail: String,
)
