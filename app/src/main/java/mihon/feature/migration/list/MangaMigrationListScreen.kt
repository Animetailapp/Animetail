package mihon.feature.migration.list

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.MigrateMangaSearchRoute
import mihon.feature.migration.list.components.MigrationEntryDialog
import mihon.feature.migration.list.components.MigrationExitDialog
import mihon.feature.migration.list.components.MigrationProgressDialog

@Composable
fun MangaMigrationListScreen(
    mangaIds: Collection<Long>,
    extraSearchQuery: String?,
) {
    val navigator = LocalNavigator.current
    val viewModel =
        assistedMetroViewModel<MangaMigrationListViewModel, MangaMigrationListViewModel.Factory> {
            create(mangaIds = mangaIds, extraSearchQuery = extraSearchQuery)
        }
    val state by viewModel.state.collectAsState()

    ResultEffect<MangaMatchOverrideEvent> {
        viewModel.useMangaForMigration(
            mangaId = it.current,
            targetMangaId = it.target,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateBackEvent.collect {
            navigator.pop()
        }
    }
    MangaMigrationListScreenContent(
        items = state.items,
        migrationComplete = state.migrationComplete,
        finishedCount = state.finishedCount,
        onItemClick = {
            navigator.push(MangaRoute(it.id, true))
        },
        onSearchManually = { migrationItem ->
            navigator.push(MigrateMangaSearchRoute(migrationItem.manga.id))
        },
        onSkip = { viewModel.removeManga(it) },
        onMigrate = { viewModel.migrateNow(mangaId = it, replace = true) },
        onCopy = { viewModel.migrateNow(mangaId = it, replace = false) },
        openMigrationDialog = viewModel::showMigrateDialog,
    )

    when (val dialog = state.dialog) {
        is MangaMigrationListViewModel.Dialog.Migrate -> {
            MigrationEntryDialog(
                onDismissRequest = viewModel::dismissDialog,
                copy = dialog.copy,
                totalCount = dialog.totalCount,
                skippedCount = dialog.skippedCount,
                onMigrate = {
                    if (dialog.copy) {
                        viewModel.copyMangas()
                    } else {
                        viewModel.migrateMangas(true)
                    }
                },
            )
        }

        is MangaMigrationListViewModel.Dialog.Progress -> {
            MigrationProgressDialog(
                progress = dialog.progress,
                exitMigration = viewModel::cancelMigrate,
            )
        }

        MangaMigrationListViewModel.Dialog.Exit -> {
            MigrationExitDialog(
                onDismissRequest = viewModel::dismissDialog,
                exitMigration = navigator::pop,
            )
        }

        null -> Unit
    }

    BackHandler(true) {
        viewModel.showExitDialog()
    }
}

data class MangaMatchOverrideEvent(val current: Long, val target: Long)
