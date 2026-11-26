package eu.kanade.tachiyomi.ui.storage.anime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.storage.AnimeDownloadPolicyDialog
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadSizes
import eu.kanade.tachiyomi.util.toSize
import kotlinx.collections.immutable.toImmutableList
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * The downloaded episodes of a show and their sizes, from the storage screen.
 */
@Composable
fun AnimeStorageDetailScreen(
    sourceIds: List<Long>,
    dirName: String,
) {
    val navigator = LocalNavigator.current
    val viewModel = assistedMetroViewModel<AnimeStorageDetailViewModel, AnimeStorageDetailViewModel.Factory> {
        create(sourceIds = sourceIds.toSet(), dirName = dirName)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val policyDialog by viewModel.policyDialog.collectAsStateWithLifecycle()
    var deleteAllDialog by remember { mutableStateOf(false) }
    var deleteRow by remember { mutableStateOf<AnimeStorageDetailViewModel.EpisodeRow?>(null) }

    LaunchedEffect(state.gone) {
        if (state.gone) navigator.pop()
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = state.anime?.title ?: dirName,
                navigateUp = navigator::pop,
                actions = {
                    AppBarActions(
                        buildList {
                            if (state.anime != null) {
                                add(
                                    AppBar.OverflowAction(
                                        title = stringResource(TLMR.strings.action_cleanup_policy),
                                        onClick = viewModel::openPolicy,
                                    ),
                                )
                            }
                            add(
                                AppBar.OverflowAction(
                                    title = stringResource(TLMR.strings.action_delete_all_downloads),
                                    onClick = { deleteAllDialog = true },
                                ),
                            )
                        }.toImmutableList(),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        val show = state.show
        if (state.loading || show == null) {
            LoadingScreen(Modifier.padding(contentPadding))
            return@Scaffold
        }
        LazyColumn(contentPadding = contentPadding) {
            item {
                Text(
                    text = show.size.toSize() + " · " +
                        pluralStringResource(AYMR.plurals.anime_num_episodes, show.entries.size, show.entries.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(MaterialTheme.padding.medium),
                )
                if (state.anime == null) {
                    Text(
                        text = stringResource(TLMR.strings.storage_not_in_database),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(horizontal = MaterialTheme.padding.medium)
                            .secondaryItemAlpha(),
                    )
                }
            }
            items(state.rows, key = { it.entry.name }) { row ->
                EpisodeStorageRow(row, onDelete = { deleteRow = row })
                HorizontalDivider()
            }
        }
    }

    deleteRow?.let { row ->
        ConfirmDeleteDialog(
            title = stringResource(TLMR.strings.storage_delete_episode_title),
            text = stringResource(AYMR.strings.delete_confirmation, row.episode?.name ?: row.entry.name),
            onDismissRequest = { deleteRow = null },
            onConfirm = { viewModel.deleteEpisode(row) },
        )
    }
    if (deleteAllDialog) {
        ConfirmDeleteDialog(
            title = stringResource(AYMR.strings.delete_downloads_for_anime),
            text = stringResource(AYMR.strings.delete_confirmation, state.anime?.title ?: dirName),
            onDismissRequest = { deleteAllDialog = false },
            onConfirm = viewModel::deleteAll,
        )
    }
    policyDialog?.let { dialog ->
        AnimeDownloadPolicyDialog(
            dialog = dialog,
            onChange = viewModel::changePolicy,
            onDismissRequest = viewModel::dismissPolicy,
            onSave = viewModel::savePolicy,
        )
    }
}

@Composable
private fun EpisodeStorageRow(row: AnimeStorageDetailViewModel.EpisodeRow, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = Modifier.padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = MaterialTheme.padding.small),
        ) {
            Text(
                text = row.episode?.name ?: row.entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val status = when {
                row.episode == null -> stringResource(TLMR.strings.storage_episode_not_listed)
                row.episode.seen -> stringResource(TLMR.strings.storage_episode_watched)
                else -> stringResource(TLMR.strings.storage_episode_unwatched)
            }
            val size = if (row.entry.size == AnimeDownloadSizes.UNKNOWN_SIZE) {
                stringResource(TLMR.strings.storage_size_unknown)
            } else {
                row.entry.size.toSize()
            }
            Text(
                text = "$size · $status",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.secondaryItemAlpha(),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(MR.strings.action_delete))
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
