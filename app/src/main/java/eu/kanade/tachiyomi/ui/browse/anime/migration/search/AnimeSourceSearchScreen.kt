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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.browse.anime.BrowseAnimeSourceContent
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceViewModel
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.SourceFilterAnimeDialog
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.anime.LocalAnimeSource

data class AnimeSourceSearchScreen(
    private val oldAnime: Anime,
    private val sourceId: Long,
    private val query: String?,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val uriHandler = LocalUriHandler.current
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = viewModel<BrowseAnimeSourceViewModel>(
            factory = BrowseAnimeSourceViewModel.Factory,
            extras = CreationExtras {
                set(BrowseAnimeSourceViewModel.SOURCE_ID_KEY, sourceId)
                set(BrowseAnimeSourceViewModel.LISTING_QUERY_KEY, query)
            },
        )
        val state by viewModel.state.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }
        // KMK -->
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
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
                viewModel.setDialog(BrowseAnimeSourceViewModel.Dialog.Migrate(newAnime = it, oldAnime = oldAnime))
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
                        WebViewScreen(
                            url = source.baseUrl,
                            initialTitle = source.name,
                            sourceId = source.id,
                        ),
                    )
                },
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalAnimeSourceHelpClick = { uriHandler.openUri(LocalAnimeSource.HELP_URL) },
                onAnimeClick = openMigrateDialog,
                onAnimeLongClick = { navigator.push(AnimeScreen(it.id, true)) },
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
                    oldAnime = oldAnime,
                    newAnime = dialog.newAnime,
                    screenModel = rememberScreenModel { MigrateAnimeDialogScreenModel() },
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(AnimeScreen(dialog.newAnime.id)) },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(oldAnime, dialog.newAnime)) },
                    onPopScreen = {
                        scope.launch {
                            navigator.popUntilRoot()
                            HomeScreen.openTab(HomeScreen.Tab.Browse())
                            navigator.push(AnimeScreen(dialog.newAnime.id))
                        }
                    },
                )
            }

            else -> {}
        }
    }
}
