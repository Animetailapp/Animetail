package eu.kanade.tachiyomi.ui.browse.manga.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.result.LocalResultEventBus
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.manga.MigrateMangaSearchScreen
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaMigrationListRoute
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.MangaSourceSearchRoute
import mihon.feature.migration.list.MangaMatchOverrideEvent

@Composable
fun MigrateMangaSearchScreen(mangaId: Long) {
    val navigator = LocalNavigator.current
    val resultEventBus = LocalResultEventBus.current

    val screenModel = assistedMetroViewModel<MigrateMangaSearchViewModel, MigrateMangaSearchViewModel.Factory> {
        create(mangaId = mangaId, initialExtensionFilter = null)
    }
    val state by screenModel.state.collectAsState()

    val dialogScreenModel = assistedMetroViewModel<
        MangaMigrateSearchScreenDialogScreenModel,
        MangaMigrateSearchScreenDialogScreenModel.Factory,
        > {
        create(mangaId = mangaId)
    }
    val dialogState by dialogScreenModel.state.collectAsState()

    MigrateMangaSearchScreen(
        state = state,
        fromSourceId = dialogState.manga?.source,
        navigateUp = navigator::pop,
        onChangeSearchQuery = screenModel::updateSearchQuery,
        onSearch = { screenModel.search() },
        getManga = { screenModel.getManga(it) },
        onChangeSearchFilter = screenModel::setSourceFilter,
        onToggleResults = screenModel::toggleFilterResults,
        onClickSource = {
            navigator.push(
                MangaSourceSearchRoute(dialogState.manga!!.id, it.id, state.searchQuery),
            )
        },
        onClickItem = { targetManga ->
            val migrationListRoute = navigator.routes
                .filterIsInstance<MangaMigrationListRoute>()
                .lastOrNull()
            if (migrationListRoute == null) {
                dialogScreenModel.setDialog(
                    MangaMigrateSearchScreenDialogScreenModel.Dialog.Migrate(targetManga),
                )
            } else {
                resultEventBus.sendResult(
                    result = MangaMatchOverrideEvent(current = mangaId, target = targetManga.id),
                )
                navigator.popTo { route -> route is MangaMigrationListRoute }
            }
        },
        onLongClickItem = { navigator.push(MangaRoute(it.id, true)) },
    )

    when (val dialog = dialogState.dialog) {
        is MangaMigrateSearchScreenDialogScreenModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                oldManga = dialogState.manga!!,
                newManga = dialog.manga,
                screenModel = metroViewModel<MigrateMangaDialogScreenModel>(),
                onDismissRequest = { dialogScreenModel.setDialog(null) },
                onClickTitle = {
                    navigator.push(MangaRoute(dialog.manga.id, true))
                },
                onPopScreen = { navigator.replace(MangaRoute(dialog.manga.id)) },
            )
        }

        else -> {}
    }
}
