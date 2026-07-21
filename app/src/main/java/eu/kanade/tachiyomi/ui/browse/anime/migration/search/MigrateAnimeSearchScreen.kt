package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.MigrateAnimeSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen

class MigrateAnimeSearchScreen(private val animeId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = viewModel<MigrateAnimeSearchViewModel>(
            factory = viewModelFactory {
                initializer {
                    MigrateAnimeSearchViewModel(animeId = animeId)
                }
            },
        )
        val state by viewModel.state.collectAsState()

        val dialogScreenModel = rememberScreenModel {
            AnimeMigrateSearchScreenDialogScreenModel(
                animeId = animeId,
            )
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
                    AnimeSourceSearchScreen(dialogState.anime!!, it.id, state.searchQuery),
                )
            },
            onClickItem = { targetAnime ->
                val migrationListScreen = navigator.items
                    .filterIsInstance<mihon.feature.migration.list.AnimeMigrationListScreen>()
                    .firstOrNull()
                if (migrationListScreen != null) {
                    migrationListScreen.addMatchOverride(animeId, targetAnime.id)
                    navigator.pop()
                } else {
                    dialogScreenModel.setDialog(
                        (AnimeMigrateSearchScreenDialogScreenModel.Dialog.Migrate(targetAnime)),
                    )
                }
            },
            onLongClickItem = { navigator.push(AnimeScreen(it.id, true)) },
        )

        when (val dialog = dialogState.dialog) {
            is AnimeMigrateSearchScreenDialogScreenModel.Dialog.Migrate -> {
                MigrateAnimeDialog(
                    oldAnime = dialogState.anime!!,
                    newAnime = dialog.anime,
                    screenModel = rememberScreenModel { MigrateAnimeDialogScreenModel() },
                    onDismissRequest = { dialogScreenModel.setDialog(null) },
                    onClickTitle = {
                        navigator.push(AnimeScreen(dialog.anime.id, true))
                    },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(dialogState.anime!!, dialog.anime)) },
                    onPopScreen = {
                        if (navigator.lastItem is AnimeScreen) {
                            val lastItem = navigator.lastItem
                            navigator.popUntil { navigator.items.contains(lastItem) }
                            navigator.push(AnimeScreen(dialog.anime.id))
                        } else {
                            navigator.replace(AnimeScreen(dialog.anime.id))
                        }
                    },
                )
            }

            else -> {}
        }
    }
}
