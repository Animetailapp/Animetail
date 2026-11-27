package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.browse.anime.GlobalAnimeSearchScreen
import mihon.core.navigation.AnimeRoute
import mihon.core.navigation.BrowseAnimeSourceRoute
import mihon.core.navigation.LocalNavigator
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun GlobalAnimeSearchScreen(
    searchQuery: String = "",
    extensionFilter: String? = null,
) {
    if (!ifAnimeSourcesLoaded()) {
        LoadingScreen()
        return
    }

    val navigator = LocalNavigator.current

    val viewModel = assistedMetroViewModel<GlobalAnimeSearchViewModel, GlobalAnimeSearchViewModel.Factory> {
        create(searchQuery, extensionFilter)
    }
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
                        navigator.replace(AnimeRoute(anime.id, true))
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
                BrowseAnimeSourceRoute(
                    source.id,
                    viewModel.state.value.searchQuery,
                ),
            )
        },
        onClickItem = { navigator.push(AnimeRoute(it.id, true)) },
        onLongClickItem = { navigator.push(AnimeRoute(it.id, true)) },
    )
}
