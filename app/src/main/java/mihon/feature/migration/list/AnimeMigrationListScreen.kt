package mihon.feature.migration.list

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeSearchScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import mihon.feature.migration.list.components.MigrationEntryDialog
import mihon.feature.migration.list.components.MigrationExitDialog
import mihon.feature.migration.list.components.MigrationProgressDialog

class AnimeMigrationListScreen(
    private val animeIds: Collection<Long>,
    private val extraSearchQuery: String?,
) : Screen() {

    private var matchOverride: Pair<Long, Long>? = null

    fun addMatchOverride(current: Long, target: Long) {
        matchOverride = current to target
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<AnimeMigrationListViewModel>(
            factory = AnimeMigrationListViewModel.Factory,
            extras = CreationExtras {
                set(AnimeMigrationListViewModel.ANIME_IDS_KEY, animeIds)
                set(AnimeMigrationListViewModel.EXTRA_SEARCH_QUERY_KEY, extraSearchQuery)
            },
        )
        val state by viewModel.state.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(matchOverride) {
            val (current, target) = matchOverride ?: return@LaunchedEffect
            viewModel.useAnimeForMigration(current, target)
            matchOverride = null
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
                navigator.push(AnimeScreen(it.id, true))
            },
            onSearchManually = { migrationItem ->
                navigator.push(MigrateAnimeSearchScreen(migrationItem.anime.id))
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
}
