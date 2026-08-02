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
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import mihon.feature.migration.list.components.MigrationEntryDialog
import mihon.feature.migration.list.components.MigrationExitDialog
import mihon.feature.migration.list.components.MigrationProgressDialog

class MangaMigrationListScreen(
    private val mangaIds: Collection<Long>,
    private val extraSearchQuery: String?,
) : Screen() {

    private var matchOverride: Pair<Long, Long>? = null

    fun addMatchOverride(current: Long, target: Long) {
        matchOverride = current to target
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<MangaMigrationListViewModel>(
            factory = MangaMigrationListViewModel.Factory,
            extras = CreationExtras {
                set(MangaMigrationListViewModel.MANGA_IDS_KEY, mangaIds)
                set(MangaMigrationListViewModel.EXTRA_SEARCH_QUERY_KEY, extraSearchQuery)
            },
        )
        val state by viewModel.state.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(matchOverride) {
            val (current, target) = matchOverride ?: return@LaunchedEffect
            viewModel.useMangaForMigration(current, target)
            matchOverride = null
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
                navigator.push(MangaScreen(it.id, true))
            },
            onSearchManually = { migrationItem ->
                navigator.push(MigrateMangaSearchScreen(migrationItem.manga.id))
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
}
