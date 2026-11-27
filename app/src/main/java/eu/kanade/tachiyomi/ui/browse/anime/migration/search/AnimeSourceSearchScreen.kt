package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.result.LocalResultEventBus
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.browse.anime.BrowseAnimeSourceContent
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceViewModel
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.SourceFilterAnimeDialog
import eu.kanade.tachiyomi.ui.home.TabEvent
import eu.kanade.tachiyomi.util.system.toast
import mihon.core.navigation.AnimeMigrationListRoute
import mihon.core.navigation.AnimeRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MigrateSeasonSelectRoute
import mihon.core.navigation.WebViewRoute
import mihon.feature.migration.list.AnimeMatchOverrideEvent
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.anime.LocalAnimeSource

@Composable
fun AnimeSourceSearchScreen(
    currentAnimeId: Long,
    sourceId: Long,
    query: String?,
) {
    if (!ifAnimeSourcesLoaded()) {
        LoadingScreen()
        return
    }

    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.current
    val resultEventBus = LocalResultEventBus.current

    val viewModel =
        assistedMetroViewModel<BrowseAnimeSourceViewModel, BrowseAnimeSourceViewModel.Factory> {
            create(sourceId = sourceId, listingQuery = query)
        }
    val state by viewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    // KMK -->
    val context = LocalContext.current
    // KMK <-

    Scaffold(
        topBar = { scrollBehavior ->
            SearchToolbar(
                searchQuery = state.toolbarQuery ?: "",
                onChangeSearchQuery = viewModel::setToolbarQuery,
                onClickCloseSearch = navigator::pop,
                onSearch = viewModel::search,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = state.filters.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(text = stringResource(MR.strings.action_filter)) },
                    icon = { Icon(Icons.Outlined.FilterList, contentDescription = "") },
                    onClick = viewModel::openFilterSheet,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        val openMigrateDialog: (Anime) -> Unit = {
            val migrateListRoute = navigator.routes
                .filterIsInstance<AnimeMigrationListRoute>()
                .lastOrNull()

            if (migrateListRoute == null) {
                viewModel.showMigrateDialog(currentAnimeId = currentAnimeId, target = it)
            } else {
                resultEventBus.sendResult(
                    result = AnimeMatchOverrideEvent(current = currentAnimeId, target = it.id),
                )
                navigator.popTo { route -> route is AnimeMigrationListRoute }
            }
        }
        BrowseAnimeSourceContent(
            source = viewModel.source,
            animeList = viewModel.animePagerFlowFlow.collectAsLazyPagingItems(),
            columns = viewModel.getColumnsPreference(LocalConfiguration.current.orientation),
            displayMode = viewModel.displayMode,
            snackbarHostState = snackbarHostState,
            contentPadding = paddingValues,
            onWebViewClick = {
                val source = viewModel.source as? AnimeHttpSource ?: return@BrowseAnimeSourceContent
                navigator.push(
                    WebViewRoute(
                        url = source.getHomeUrl(),
                        initialTitle = source.name,
                        sourceId = source.id,
                    ),
                )
            },
            onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
            onLocalAnimeSourceHelpClick = { uriHandler.openUri(LocalAnimeSource.HELP_URL) },
            onAnimeClick = openMigrateDialog,
            onAnimeLongClick = { navigator.push(AnimeRoute(it.id, true)) },
        )
    }

    val onDismissRequest = { viewModel.setDialog(null) }
    when (val dialog = state.dialog) {
        is BrowseAnimeSourceViewModel.Dialog.Filter -> {
            SourceFilterAnimeDialog(
                onDismissRequest = onDismissRequest,
                filters = state.filters,
                onReset = viewModel::resetFilters,
                onFilter = { viewModel.search(filters = state.filters) },
                onUpdate = viewModel::setFilters,
                // SY -->
                startExpanded = viewModel.startExpanded,
                onSave = {},
                // KMK -->
                savedSearches = state.savedSearches,
                onSavedSearch = { search ->
                    viewModel.onSavedSearch(search) {
                        context.toast(it)
                    }
                },
                onSavedSearchPressDesc = stringResource(TLMR.strings.saved_searches),
                shouldShowSavingButton = false,
                // KMK <--
                onSavedSearchPress = {},
                // SY <--
            )
        }

        is BrowseAnimeSourceViewModel.Dialog.Migrate -> {
            MigrateAnimeDialog(
                oldAnime = dialog.oldAnime,
                newAnime = dialog.newAnime,
                screenModel = metroViewModel<MigrateAnimeDialogScreenModel>(),
                onDismissRequest = onDismissRequest,
                onClickTitle = { navigator.push(AnimeRoute(dialog.newAnime.id)) },
                onClickSeasons = { navigator.push(MigrateSeasonSelectRoute(dialog.oldAnime.id, dialog.newAnime.id)) },
                onPopScreen = {
                    navigator.popToRoot()
                    resultEventBus.sendResult<TabEvent>(TabEvent.Browse(anime = true))
                    navigator.push(AnimeRoute(dialog.newAnime.id))
                },
            )
        }

        else -> {}
    }
}
