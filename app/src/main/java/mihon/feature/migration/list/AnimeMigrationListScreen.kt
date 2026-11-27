package mihon.feature.migration.list

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import mihon.core.navigation.AnimeRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MigrateAnimeSearchRoute
import mihon.feature.migration.list.components.MigrationEntryDialog
import mihon.feature.migration.list.components.MigrationExitDialog
import mihon.feature.migration.list.components.MigrationProgressDialog

@Composable
fun AnimeMigrationListScreen(
    animeIds: Collection<Long>,
    extraSearchQuery: String?,
) {
    val navigator = LocalNavigator.current
    val viewModel =
        assistedMetroViewModel<AnimeMigrationListViewModel, AnimeMigrationListViewModel.Factory> {
            create(animeIds = animeIds, extraSearchQuery = extraSearchQuery)
        }
    val state by viewModel.state.collectAsState()

    ResultEffect<AnimeMatchOverrideEvent> {
        viewModel.useAnimeForMigration(
            animeId = it.current,
            targetAnimeId = it.target,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateBackEvent.collect {
            navigator.pop()
        }
    }
    AnimeMigrationListScreenContent(
        items = state.items,
        migrationComplete = state.migrationComplete,
        finishedCount = state.finishedCount,
        onItemClick = {
            navigator.push(AnimeRoute(it.id, true))
        },
        onSearchManually = { migrationItem ->
            navigator.push(MigrateAnimeSearchRoute(migrationItem.anime.id))
        },
        onSkip = { viewModel.removeAnime(it) },
        onMigrate = { viewModel.migrateNow(animeId = it, replace = true) },
        onCopy = { viewModel.migrateNow(animeId = it, replace = false) },
        openMigrationDialog = viewModel::showMigrateDialog,
    )

    when (val dialog = state.dialog) {
        is AnimeMigrationListViewModel.Dialog.Migrate -> {
            MigrationEntryDialog(
                onDismissRequest = viewModel::dismissDialog,
                copy = dialog.copy,
                totalCount = dialog.totalCount,
                skippedCount = dialog.skippedCount,
                onMigrate = {
                    if (dialog.copy) {
                        viewModel.copyAnimes()
                    } else {
                        viewModel.migrateAnimes(true)
                    }
                },
            )
        }

        is AnimeMigrationListViewModel.Dialog.Progress -> {
            MigrationProgressDialog(
                progress = dialog.progress,
                exitMigration = viewModel::cancelMigrate,
            )
        }

        AnimeMigrationListViewModel.Dialog.Exit -> {
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

data class AnimeMatchOverrideEvent(val current: Long, val target: Long)
