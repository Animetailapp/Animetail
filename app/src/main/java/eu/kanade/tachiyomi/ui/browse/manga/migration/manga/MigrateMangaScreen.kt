package eu.kanade.tachiyomi.ui.browse.manga.migration.manga

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
import eu.kanade.presentation.browse.manga.MigrateMangaScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

data class MigrateMangaScreen(
    private val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<MigrateMangaViewModel>(
            factory = MigrateMangaViewModel.Factory,
            extras = CreationExtras {
                set(MigrateMangaViewModel.SOURCE_ID_KEY, sourceId)
            },
        )

        val state by viewModel.state.collectAsState()

        val isSelectionMode = state.selectedMangaIds.isNotEmpty()
        BackHandler(enabled = isSelectionMode) {
            viewModel.clearSelection()
        }

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        MigrateMangaScreen(
            navigateUp = {
                if (isSelectionMode) {
                    viewModel.clearSelection()
                } else {
                    navigator.pop()
                }
            },
            title = state.source!!.name,
            state = state,
            onClickItem = { manga ->
                if (isSelectionMode) {
                    viewModel.toggleSelection(manga)
                } else {
                    navigator.push(MigrateMangaSearchScreen(manga.id))
                }
            },
            onClickCover = { manga ->
                if (isSelectionMode) {
                    viewModel.toggleSelection(manga)
                } else {
                    navigator.push(MangaScreen(manga.id))
                }
            },
            onLongClickItem = viewModel::toggleSelection,
            onSelectAll = viewModel::selectAll,
            onClearSelection = viewModel::clearSelection,
            onClickMigrate = {
                navigator.push(mihon.feature.migration.config.MangaMigrationConfigScreen(state.selectedMangaIds))
            },
        )

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                when (event) {
                    MigrationMangaEvent.FailedFetchingFavorites -> {
                        context.toast(MR.strings.internal_error)
                    }
                }
            }
        }
    }
}
