package eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.manga.GlobalMangaSearchScreen
import mihon.core.navigation.BrowseMangaSourceRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaRoute
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun GlobalMangaSearchScreen(
    searchQuery: String = "",
    extensionFilter: String? = null,
) {
    val navigator = LocalNavigator.current

    val viewModel =
        assistedMetroViewModel<GlobalMangaSearchViewModel, GlobalMangaSearchViewModel.Factory> {
            create(initialQuery = searchQuery, initialExtensionFilter = extensionFilter)
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
                MangaSearchItemResult.Loading -> return@LaunchedEffect

                is MangaSearchItemResult.Success -> {
                    val manga = result.result.singleOrNull()
                    if (manga != null) {
                        navigator.replace(MangaRoute(manga.id, true))
                    } else {
                        // Backoff to result screen
                        showSingleLoadingScreen = false
                    }
                }

                else -> showSingleLoadingScreen = false
            }
        }
    } else {
        GlobalMangaSearchScreen(
            state = state,
            navigateUp = navigator::pop,
            onChangeSearchQuery = viewModel::updateSearchQuery,
            onSearch = { viewModel.search() },
            getManga = { viewModel.getManga(it) },
            onChangeSearchFilter = viewModel::setSourceFilter,
            onToggleResults = viewModel::toggleFilterResults,
            onClickSource = {
                navigator.push(BrowseMangaSourceRoute(it.id, state.searchQuery))
            },
            onClickItem = { navigator.push(MangaRoute(it.id, true)) },
            onLongClickItem = { navigator.push(MangaRoute(it.id, true)) },
        )
    }
}
