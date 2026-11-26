package eu.kanade.tachiyomi.ui.storage.anime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.storage.CumulativeStorage
import eu.kanade.presentation.more.storage.SelectStorageCategory
import eu.kanade.presentation.more.storage.StorageCleanupDialog
import eu.kanade.presentation.more.storage.StorageItem
import eu.kanade.tachiyomi.util.toSize
import kotlinx.collections.immutable.persistentListOf
import mihon.core.navigation.AnimeStorageDetailRoute
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun animeStorageTab(): TabContent {
    val navigator = LocalNavigator.current

    val viewModel = metroViewModel<AnimeStorageViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cleanup by viewModel.cleanup.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = AYMR.strings.label_anime,
        actions = persistentListOf(
            AppBar.OverflowAction(
                title = stringResource(TLMR.strings.action_clean_up),
                onClick = viewModel::openCleanup,
            ),
            AppBar.OverflowAction(
                title = stringResource(TLMR.strings.action_recalculate_sizes),
                onClick = viewModel::recalculate,
            ),
        ),
        content = { contentPadding, _ ->
            AnimeStorageContent(
                state = state,
                contentPadding = contentPadding,
                onCategorySelected = viewModel::setSelectedCategory,
                onClickRow = { row ->
                    navigator.push(
                        AnimeStorageDetailRoute(
                            sourceIds = row.show.sourceIds.toList(),
                            dirName = row.show.dirName,
                        ),
                    )
                },
                onDeleteRow = viewModel::deleteShow,
                onReviewCleanup = viewModel::openCleanup,
            )

            cleanup?.let { cleanupState ->
                StorageCleanupDialog(
                    title = stringResource(TLMR.strings.storage_cleanup_title),
                    preview = (cleanupState as? AnimeStorageViewModel.CleanupState.Ready)?.preview,
                    info = stringResource(TLMR.strings.storage_cleanup_policies_info),
                    onDismissRequest = viewModel::dismissCleanup,
                    onApply = viewModel::applyCleanup,
                )
            }
        },
        navigateUp = navigator::pop,
    )
}

@Composable
private fun AnimeStorageContent(
    state: AnimeStorageViewModel.State,
    contentPadding: PaddingValues,
    onCategorySelected: (tachiyomi.domain.category.model.Category) -> Unit,
    onClickRow: (AnimeStorageViewModel.Row) -> Unit,
    onDeleteRow: (AnimeStorageViewModel.Row) -> Unit,
    onReviewCleanup: () -> Unit,
) {
    if (state.loading) {
        LoadingScreen()
        return
    }

    val items = state.rows.map { it.item }
    LazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.padding(horizontal = MaterialTheme.padding.small),
    ) {
        item {
            Spacer(Modifier.height(MaterialTheme.padding.small))
            StorageSummary(state, onReviewCleanup)
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectStorageCategory(
                    selectedCategory = state.selectedCategory,
                    categories = state.categories,
                    onCategorySelected = onCategorySelected,
                )
                CumulativeStorage(
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.padding.small,
                        vertical = MaterialTheme.padding.medium,
                    ),
                    items = items,
                )
            }
        }
        items(state.rows, key = { it.id }) { row ->
            StorageItem(
                item = row.item,
                isManga = false,
                onClick = { onClickRow(row) },
                onDelete = { onDeleteRow(row) },
            )
            Spacer(Modifier.height(MaterialTheme.padding.medium))
        }
    }
}

/** Used and free space, measuring progress and the storage limit. */
@Composable
private fun StorageSummary(state: AnimeStorageViewModel.State, onReviewCleanup: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        modifier = Modifier.padding(bottom = MaterialTheme.padding.medium),
    ) {
        Text(
            text = stringResource(TLMR.strings.storage_used_by_downloads) + ": " + state.usedBytes.toSize(),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.freeBytes >= 0 && state.volumeBytes > 0) {
            LinearProgressIndicator(
                progress = { 1 - state.freeBytes / state.volumeBytes.toFloat() },
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .fillMaxWidth()
                    .height(8.dp),
            )
            Text(
                text = stringResource(
                    TLMR.strings.storage_free_space,
                    state.freeBytes.toSize(),
                    state.volumeBytes.toSize(),
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.secondaryItemAlpha(),
            )
        } else {
            Text(
                text = stringResource(TLMR.strings.storage_free_space_unknown),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.secondaryItemAlpha(),
            )
        }
        if (state.measuring) {
            if (state.toMeasure == 0) {
                // Still listing the downloads
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(TLMR.strings.storage_size_unknown),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LinearProgressIndicator(
                    progress = { state.measured / state.toMeasure.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(TLMR.strings.storage_measuring, state.measured, state.toMeasure),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (state.limitBytes > 0) {
            Text(
                text = stringResource(TLMR.strings.storage_limit_summary, state.limitBytes.toSize()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.needsLimitReview) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(MaterialTheme.padding.medium)) {
                    Text(
                        text = stringResource(
                            TLMR.strings.storage_limit_over_review,
                            (state.usedBytes - state.limitBytes).toSize(),
                            state.limitBytes.toSize(),
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = onReviewCleanup, modifier = Modifier.align(Alignment.End)) {
                        Text(text = stringResource(TLMR.strings.action_review_cleanup))
                    }
                }
            }
        }
    }
}
