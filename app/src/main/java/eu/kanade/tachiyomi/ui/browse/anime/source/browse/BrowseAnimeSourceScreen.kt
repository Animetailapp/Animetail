package eu.kanade.tachiyomi.ui.browse.anime.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.browse.RemoveEntryDialog
import eu.kanade.presentation.browse.SavedSearchCreateDialog
import eu.kanade.presentation.browse.SavedSearchDeleteDialog
import eu.kanade.presentation.browse.anime.BrowseAnimeSourceContent
import eu.kanade.presentation.browse.anime.MissingSourceScreen
import eu.kanade.presentation.browse.anime.components.BrowseAnimeSourceToolbar
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.anime.DuplicateAnimeDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialog
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceViewModel.Listing
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.anime.LocalAnimeSource

data class BrowseAnimeSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
    // SY -->
    private val filtersJson: String? = null,
    private val savedSearch: Long? = null,
    // SY <--
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val viewModel = viewModel<BrowseAnimeSourceViewModel>(
            factory = BrowseAnimeSourceViewModel.Factory,
            extras = CreationExtras {
                set(BrowseAnimeSourceViewModel.SOURCE_ID_KEY, sourceId)
                set(BrowseAnimeSourceViewModel.LISTING_QUERY_KEY, listingQuery)
                set(BrowseAnimeSourceViewModel.FILTERS_KEY, filtersJson)
                set(BrowseAnimeSourceViewModel.SAVED_SEARCH_KEY, savedSearch)
            },
        )
        val state by viewModel.state.collectAsState()

        val navigator = LocalNavigator.currentOrThrow
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> viewModel.setToolbarQuery(null)
                else -> navigator.pop()
            }
        }

        // SY -->
        val context = LocalContext.current
        // SY <--

        // KMK -->
        viewModel.source.let {
            // KMK <--
            if (it is StubAnimeSource) {
                MissingSourceScreen(
                    source = it,
                    navigateUp = navigateUp,
                )
                return
            }
        }

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }

        val onHelpClick = { uriHandler.openUri(LocalAnimeSource.HELP_URL) }
        val onWebViewClick = f@{
            val source = viewModel.source as? AnimeHttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.baseUrl,
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

        LaunchedEffect(viewModel.source) {
            assistUrl = (viewModel.source as? AnimeHttpSource)?.baseUrl
        }

        var topBarHeight by remember { mutableIntStateOf(0) }
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .onSizeChanged { topBarHeight = it.height },
                ) {
                    BrowseAnimeSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = viewModel::setToolbarQuery,
                        source = viewModel.source,
                        displayMode = viewModel.displayMode,
                        onDisplayModeChange = { viewModel.displayMode = it },
                        navigateUp = navigateUp,
                        onWebViewClick = onWebViewClick,
                        onHelpClick = onHelpClick,
                        onSettingsClick = { navigator.push(AnimeSourcePreferencesScreen(sourceId)) },
                        onSearch = viewModel::search,
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == Listing.Popular,
                            onClick = {
                                viewModel.resetFilters()
                                viewModel.setListing(Listing.Popular)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.popular))
                            },
                        )
                        if ((viewModel.source as AnimeCatalogueSource).supportsLatest) {
                            FilterChip(
                                selected = state.listing == Listing.Latest,
                                onClick = {
                                    viewModel.resetFilters()
                                    viewModel.setListing(Listing.Latest)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.latest))
                                },
                            )
                        }
                        if (state.filterable) {
                            FilterChip(
                                selected = state.listing is Listing.Search &&
                                    // KMK -->
                                    (state.listing as Listing.Search).savedSearchId == null,
                                // KMK <--
                                onClick = viewModel::openFilterSheet,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    // SY -->
                                    Text(
                                        text = if (state.filters.isNotEmpty()) {
                                            stringResource(MR.strings.action_filter)
                                        } else {
                                            stringResource(MR.strings.action_search)
                                        },
                                    )
                                    // SY <--
                                },
                            )
                        }
                        // KMK -->
                        state.savedSearches.forEach { savedSearch ->
                            FilterChip(
                                selected = state.listing is Listing.Search &&
                                    (state.listing as Listing.Search).savedSearchId == savedSearch.id,
                                onClick = {
                                    viewModel.onSavedSearch(savedSearch) {
                                        context.toast(it)
                                    }
                                },
                                label = {
                                    Text(
                                        text = savedSearch.name,
                                    )
                                },
                            )
                        }
                        // KMK <--
                    }

                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            BrowseAnimeSourceContent(
                source = viewModel.source,
                animeList = viewModel.animePagerFlowFlow.collectAsLazyPagingItems(),
                columns = viewModel.getColumnsPreference(LocalConfiguration.current.orientation),
                entries = viewModel.getColumnsPreferenceForCurrentOrientation(LocalConfiguration.current.orientation),
                topBarHeight = topBarHeight,
                displayMode = viewModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = onWebViewClick,
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalAnimeSourceHelpClick = onHelpClick,
                onAnimeClick = { navigator.push((AnimeScreen(it.id, true))) },
                onAnimeLongClick = { anime ->
                    scope.launchIO {
                        val duplicateAnime = viewModel.getDuplicateAnimelibAnime(anime)
                        when {
                            anime.favorite -> viewModel.setDialog(
                                BrowseAnimeSourceViewModel.Dialog.RemoveAnime(anime),
                            )

                            duplicateAnime != null -> viewModel.setDialog(
                                BrowseAnimeSourceViewModel.Dialog.AddDuplicateAnime(
                                    anime,
                                    duplicateAnime,
                                ),
                            )

                            else -> viewModel.addFavorite(anime)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
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
                    onSave = viewModel::onSaveSearch,
                    savedSearches = state.savedSearches,
                    onSavedSearch = { search ->
                        viewModel.onSavedSearch(search) {
                            context.toast(it)
                        }
                    },
                    onSavedSearchPress = viewModel::onSavedSearchPress,
                    // KMK -->
                    onSavedSearchPressDesc = stringResource(TLMR.strings.saved_searches_delete),
                    // KMK <--
                    // SY <--
                )
            }

            is BrowseAnimeSourceViewModel.Dialog.AddDuplicateAnime -> {
                DuplicateAnimeDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { viewModel.changeAnimeFavorite(dialog.anime) },
                    onOpenAnime = { navigator.push(AnimeScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        viewModel.setDialog(
                            BrowseAnimeSourceViewModel.Dialog.Migrate(dialog.anime, dialog.duplicate),
                        )
                    },
                )
            }

            is BrowseAnimeSourceViewModel.Dialog.Migrate -> {
                MigrateAnimeDialog(
                    oldAnime = dialog.oldAnime,
                    newAnime = dialog.newAnime,
                    screenModel = MigrateAnimeDialogScreenModel(),
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(AnimeScreen(dialog.oldAnime.id)) },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(dialog.oldAnime, dialog.newAnime)) },
                    onPopScreen = navigator::pop,
                )
            }

            is BrowseAnimeSourceViewModel.Dialog.RemoveAnime -> {
                RemoveEntryDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { viewModel.changeAnimeFavorite(dialog.anime) },
                    entryToRemove = dialog.anime.title,
                )
            }

            is BrowseAnimeSourceViewModel.Dialog.ChangeAnimeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoriesTab) },
                    onConfirm = { include, _ ->
                        viewModel.changeAnimeFavorite(dialog.anime)
                        viewModel.moveAnimeToCategories(dialog.anime, include)
                    },
                )
            }

            is BrowseAnimeSourceViewModel.Dialog.CreateSavedSearch -> SavedSearchCreateDialog(
                onDismissRequest = onDismissRequest,
                currentSavedSearches = state.savedSearches.map { it.name }.toImmutableList(),
                saveSearch = viewModel::saveSearch,
            )

            is BrowseAnimeSourceViewModel.Dialog.DeleteSavedSearch -> SavedSearchDeleteDialog(
                onDismissRequest = onDismissRequest,
                name = dialog.name,
                deleteSavedSearch = { viewModel.deleteSearch(dialog.idToDelete) },
            )

            else -> {}
        }

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow()
                .collectLatest {
                    when (it) {
                        is SearchType.Genre -> viewModel.searchGenre(it.txt)
                        is SearchType.Text -> viewModel.search(it.txt)
                    }
                }
        }
    }

    suspend fun search(query: String) = queryEvent.send(SearchType.Text(query))
    suspend fun searchGenre(name: String) = queryEvent.send(SearchType.Genre(name))

    companion object {
        private val queryEvent = Channel<SearchType>()
    }

    sealed class SearchType(val txt: String) {
        class Text(txt: String) : SearchType(txt)
        class Genre(txt: String) : SearchType(txt)
    }
}
