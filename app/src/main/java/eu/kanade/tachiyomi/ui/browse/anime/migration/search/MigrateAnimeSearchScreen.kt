package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.result.LocalResultEventBus
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.anime.MigrateAnimeSearchScreen
import mihon.core.navigation.AnimeMigrationListRoute
import mihon.core.navigation.AnimeRoute
import mihon.core.navigation.AnimeSourceSearchRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MigrateSeasonSelectRoute
import mihon.feature.migration.list.AnimeMatchOverrideEvent

@Composable
fun MigrateAnimeSearchScreen(animeId: Long) {
    val navigator = LocalNavigator.current
    val resultEventBus = LocalResultEventBus.current

    val viewModel = assistedMetroViewModel<MigrateAnimeSearchViewModel, MigrateAnimeSearchViewModel.Factory> {
        create(animeId = animeId, initialExtensionFilter = null)
    }
    val state by viewModel.state.collectAsState()

    val dialogScreenModel = assistedMetroViewModel<
        AnimeMigrateSearchScreenDialogScreenModel,
        AnimeMigrateSearchScreenDialogScreenModel.Factory,
        > {
        create(animeId = animeId)
    }
    val dialogState by dialogScreenModel.state.collectAsState()

    MigrateAnimeSearchScreen(
        state = state,
        fromSourceId = dialogState.anime?.source,
        navigateUp = navigator::pop,
        onChangeSearchQuery = viewModel::updateSearchQuery,
        onSearch = { viewModel.search() },
        getAnime = viewModel::getAnime,
        onChangeSearchFilter = viewModel::setSourceFilter,
        onToggleResults = viewModel::toggleFilterResults,
        onClickSource = {
            navigator.push(
                AnimeSourceSearchRoute(dialogState.anime!!.id, it.id, state.searchQuery),
            )
        },
        onClickItem = { targetAnime ->
            val migrationListRoute = navigator.routes
                .filterIsInstance<AnimeMigrationListRoute>()
                .lastOrNull()
            if (migrationListRoute == null) {
                dialogScreenModel.setDialog(
                    AnimeMigrateSearchScreenDialogScreenModel.Dialog.Migrate(targetAnime),
                )
            } else {
                resultEventBus.sendResult(
                    result = AnimeMatchOverrideEvent(current = animeId, target = targetAnime.id),
                )
                navigator.popTo { route -> route is AnimeMigrationListRoute }
            }
        },
        onLongClickItem = { navigator.push(AnimeRoute(it.id, true)) },
    )

    when (val dialog = dialogState.dialog) {
        is AnimeMigrateSearchScreenDialogScreenModel.Dialog.Migrate -> {
            MigrateAnimeDialog(
                oldAnime = dialogState.anime!!,
                newAnime = dialog.anime,
                screenModel = metroViewModel<MigrateAnimeDialogScreenModel>(),
                onDismissRequest = { dialogScreenModel.setDialog(null) },
                onClickTitle = {
                    navigator.push(AnimeRoute(dialog.anime.id, true))
                },
                onClickSeasons = {
                    navigator.push(MigrateSeasonSelectRoute(dialogState.anime!!.id, dialog.anime.id))
                },
                onPopScreen = { navigator.replace(AnimeRoute(dialog.anime.id)) },
            )
        }

        else -> {}
    }
}
