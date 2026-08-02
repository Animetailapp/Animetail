package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.browse.anime.GlobalAnimeSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class GlobalAnimeSearchScreen(
    val searchQuery: String = "",
    private val extensionFilter: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        val viewModel = viewModel<GlobalAnimeSearchViewModel>(
            factory = GlobalAnimeSearchViewModel.Factory,
            extras = CreationExtras {
                set(GlobalAnimeSearchViewModel.INITIAL_QUERY_KEY, searchQuery)
                set(GlobalAnimeSearchViewModel.INITIAL_EXTENSION_FILTER_KEY, extensionFilter)
            },
        )
        val state by viewModel.state.collectAsState()
        var showSingleLoadingScreen by remember {
            mutableStateOf(
                searchQuery.isNotEmpty() && !extensionFilter.isNullOrEmpty() && state.total == 1,
            )
        }

        if (showSingleLoadingScreen) {
            LoadingScreen()

            LaunchedEffect(state.items) {
                when (val result = state.items.values.singleOrNull()) {
                    AnimeSearchItemResult.Loading -> return@LaunchedEffect

                    is AnimeSearchItemResult.Success -> {
                        val anime = result.result.singleOrNull()
                        if (anime != null) {
                            navigator.replace(AnimeScreen(anime.id, true))
                        } else {
                            // Backoff to result screen
                            showSingleLoadingScreen = false
                        }
                    }

                    else -> showSingleLoadingScreen = false
                }
            }

            return
        }

        GlobalAnimeSearchScreen(
            state = state,
            navigateUp = navigator::pop,
            onChangeSearchQuery = viewModel::updateSearchQuery,
            onSearch = { viewModel.search() },
            getAnime = viewModel::getAnime,
            onChangeSearchFilter = viewModel::setSourceFilter,
            onToggleResults = viewModel::toggleFilterResults,
            onClickSource = { source ->
                navigator.push(
                    BrowseAnimeSourceScreen(
                        source.id,
                        viewModel.state.value.searchQuery,
                    ),
                )
            },
            onClickItem = { navigator.push(AnimeScreen(it.id, true)) },
            onLongClickItem = { navigator.push(AnimeScreen(it.id, true)) },
        )
    }
}
