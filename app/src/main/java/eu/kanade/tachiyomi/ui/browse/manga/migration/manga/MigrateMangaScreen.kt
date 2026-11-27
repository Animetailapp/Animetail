package eu.kanade.tachiyomi.ui.browse.manga.migration.manga

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.manga.MigrateMangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaMigrationConfigRoute
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.MigrateMangaSearchRoute
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun MigrateMangaScreen(sourceId: Long) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = assistedMetroViewModel<MigrateMangaViewModel, MigrateMangaViewModel.Factory> {
        create(sourceId = sourceId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

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
                navigator.push(MigrateMangaSearchRoute(manga.id))
            }
        },
        onClickCover = { manga ->
            if (isSelectionMode) {
                viewModel.toggleSelection(manga)
            } else {
                navigator.push(MangaRoute(manga.id))
            }
        },
        onLongClickItem = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onClickMigrate = {
            navigator.push(MangaMigrationConfigRoute(state.selectedMangaIds.toList()))
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
