package eu.kanade.tachiyomi.ui.entries.anime

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.anime.RelatedAnimesContent
import eu.kanade.presentation.browse.anime.components.BrowseSourceSimpleToolbar
import kotlinx.coroutines.CoroutineScope
import mihon.app.di.appGraph
import mihon.core.navigation.AnimeRoute
import mihon.core.navigation.BrowseAnimeSourceRoute
import mihon.core.navigation.GlobalAnimeSearchRoute
import mihon.core.navigation.Navigator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.material.Scaffold

@Composable
fun RelatedAnimesScreen(
    viewModel: AnimeViewModel,
    navigateUp: () -> Unit,
    navigator: Navigator,
    scope: CoroutineScope,
    successState: AnimeViewModel.State.Success,
) {
    val appGraph = androidx.compose.ui.platform.LocalContext.current.appGraph
    val sourcePreferences = appGraph.sourcePreferences
    var displayMode by sourcePreferences.sourceDisplayMode.asState(scope)

    val haptic = LocalHapticFeedback.current

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { scrollBehavior ->
            BrowseSourceSimpleToolbar(
                navigateUp = navigateUp,
                title = successState.anime.title,
                displayMode = displayMode,
                onDisplayModeChange = { displayMode = it },
                scrollBehavior = scrollBehavior,
                toggleSelectionMode = {},
                isRunning = false,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        RelatedAnimesContent(
            relatedAnimes = successState.relatedAnimesSorted,
            getMangaState = { manga -> viewModel.getManga(initialManga = manga) },
            columns = getColumnsPreference(LocalConfiguration.current.orientation, appGraph.libraryPreferences),
            displayMode = displayMode,
            contentPadding = paddingValues,
            onMangaClick = {
                scope.launchIO {
                    val manga = viewModel.networkToLocalAnime.getLocal(it)
                    navigator.push(AnimeRoute(manga.id, true))
                }
            },
            onMangaLongClick = {
                scope.launchIO {
                    val manga = viewModel.networkToLocalAnime.getLocal(it)
                    navigator.push(AnimeRoute(manga.id, true))
                }
            },
            onKeywordClick = { query ->
                navigator.push(BrowseAnimeSourceRoute(successState.source.id, query))
            },
            onKeywordLongClick = { query ->
                navigator.push(GlobalAnimeSearchRoute(query))
            },
            selection = emptyList(),
        )
    }
}

private fun getColumnsPreference(orientation: Int, libraryPreferences: LibraryPreferences): GridCells {
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) {
        libraryPreferences.animeLandscapeColumns
    } else {
        libraryPreferences.animePortraitColumns
    }.get()
    return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
}
