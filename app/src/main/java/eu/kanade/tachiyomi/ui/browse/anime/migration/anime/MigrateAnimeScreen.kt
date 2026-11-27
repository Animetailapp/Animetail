package eu.kanade.tachiyomi.ui.browse.anime.migration.anime

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.anime.MigrateAnimeScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import mihon.core.navigation.AnimeMigrationConfigRoute
import mihon.core.navigation.AnimeRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MigrateAnimeSearchRoute
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun MigrateAnimeScreen(sourceId: Long) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = assistedMetroViewModel<MigrateAnimeViewModel, MigrateAnimeViewModel.Factory> {
        create(sourceId = sourceId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    val isSelectionMode = state.selectedAnimeIds.isNotEmpty()
    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    if (state.isLoading) {
        LoadingScreen()
        return
    }

    MigrateAnimeScreen(
        navigateUp = {
            if (isSelectionMode) {
                viewModel.clearSelection()
            } else {
                navigator.pop()
            }
        },
        title = state.source!!.name,
        state = state,
        onClickItem = { anime ->
            if (isSelectionMode) {
                viewModel.toggleSelection(anime)
            } else {
                navigator.push(MigrateAnimeSearchRoute(anime.id))
            }
        },
        onClickCover = { anime ->
            if (isSelectionMode) {
                viewModel.toggleSelection(anime)
            } else {
                navigator.push(AnimeRoute(anime.id))
            }
        },
        onLongClickItem = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onClickMigrate = {
            navigator.push(AnimeMigrationConfigRoute(state.selectedAnimeIds.toList()))
        },
    )

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                MigrateAnimeViewModel.MigrationAnimeEvent.FailedFetchingFavorites -> {
                    context.toast(MR.strings.internal_error)
                }
            }
        }
    }
}
