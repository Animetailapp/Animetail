package eu.kanade.tachiyomi.ui.browse.manga.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.RemoveEntryDialog
import eu.kanade.presentation.browse.manga.BrowseSourceContent
import eu.kanade.presentation.browse.manga.MissingSourceScreen
import eu.kanade.presentation.browse.manga.components.BrowseMangaSourceToolbar
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.manga.DuplicateMangaDialog
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialog
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceViewModel.Listing
import eu.kanade.tachiyomi.ui.category.CategoriesShowMangaEvent
import mihon.core.navigation.CategoryRoute
import mihon.core.navigation.LocalAssistUrl
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.MangaSourcePreferencesRoute
import mihon.core.navigation.WebViewRoute
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.NewReleases
import mihon.icons.materialsymbols.roundedfilled.Favorite
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.manga.LocalMangaSource

data class BrowseMangaSearchEvent(
    val query: String,
    val genre: Boolean = false,
)

@Composable
fun BrowseMangaSourceScreen(
    sourceId: Long,
    listingQuery: String?,
) {
    val viewModel =
        assistedMetroViewModel<BrowseMangaSourceViewModel, BrowseMangaSourceViewModel.Factory> {
            create(sourceId = sourceId, listingQuery = listingQuery)
        }
    val state by viewModel.state.collectAsState()

    val navigator = LocalNavigator.current
    val resultEventBus = LocalResultEventBus.current
    val navigateUp: () -> Unit = {
        when {
            !state.isUserQuery && state.toolbarQuery != null -> viewModel.setToolbarQuery(
                null,
            )

            else -> navigator.pop()
        }
    }

    val source = state.source
    if (source == null) {
        LoadingScreen()
        return
    }

    if (source is StubMangaSource) {
        MissingSourceScreen(
            source = source,
            navigateUp = navigateUp,
        )
        return
    }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }

    val onHelpClick = { uriHandler.openUri(LocalMangaSource.HELP_URL) }
    val onWebViewClick = f@{
        val httpSource = source as? HttpSource ?: return@f
        navigator.push(
            WebViewRoute(
                url = httpSource.getHomeUrl(),
                initialTitle = httpSource.name,
                sourceId = httpSource.id,
            ),
        )
    }

    var assistUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(source) {
        assistUrl = (source as? HttpSource)?.getHomeUrl()
    }
    LocalAssistUrl.current(assistUrl)

    var topBarHeight by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .onSizeChanged { topBarHeight = it.height },
            ) {
                BrowseMangaSourceToolbar(
                    searchQuery = state.toolbarQuery,
                    onSearchQueryChange = viewModel::setToolbarQuery,
                    source = source,
                    displayMode = viewModel.displayMode,
                    onDisplayModeChange = { viewModel.displayMode = it },
                    navigateUp = navigateUp,
                    onWebViewClick = onWebViewClick,
                    onHelpClick = onHelpClick,
                    onSettingsClick = { navigator.push(MangaSourcePreferencesRoute(sourceId)) },
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
                                imageVector = MaterialSymbols.RoundedFilled.Favorite,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = {
                            Text(text = stringResource(MR.strings.popular))
                        },
                    )
                    if ((source as? CatalogueSource)?.supportsLatest == true) {
                        FilterChip(
                            selected = state.listing == Listing.Latest,
                            onClick = {
                                viewModel.resetFilters()
                                viewModel.setListing(Listing.Latest)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.NewReleases,
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
                    if (state.filters.isNotEmpty()) {
                        FilterChip(
                            selected = state.listing is Listing.Search,
                            onClick = viewModel::openFilterSheet,
                            leadingIcon = {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.action_filter))
                            },
                        )
                    }
                }

                HorizontalDivider()
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        BrowseSourceContent(
            source = source,
            mangaList = viewModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
            columns = viewModel.getColumnsPreference(LocalConfiguration.current.orientation),
            entries = viewModel.getColumnsPreferenceForCurrentOrientation(LocalConfiguration.current.orientation),
            topBarHeight = topBarHeight,
            displayMode = viewModel.displayMode,
            snackbarHostState = snackbarHostState,
            contentPadding = paddingValues,
            onWebViewClick = onWebViewClick,
            onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
            onLocalSourceHelpClick = onHelpClick,
            onMangaClick = { navigator.push(MangaRoute(it.id, true)) },
            onMangaLongClick = { manga ->
                scope.launchIO {
                    val duplicateManga = viewModel.getDuplicateLibraryManga(manga)
                    when {
                        manga.favorite -> viewModel.setDialog(
                            BrowseMangaSourceViewModel.Dialog.RemoveManga(manga),
                        )

                        duplicateManga != null -> viewModel.setDialog(
                            BrowseMangaSourceViewModel.Dialog.AddDuplicateManga(
                                manga,
                                duplicateManga,
                            ),
                        )

                        else -> viewModel.addFavorite(manga)
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
        )
    }

    val onDismissRequest = { viewModel.setDialog(null) }
    when (val dialog = state.dialog) {
        is BrowseMangaSourceViewModel.Dialog.Filter -> {
            SourceFilterMangaDialog(
                onDismissRequest = onDismissRequest,
                filters = state.filters,
                onReset = viewModel::resetFilters,
                onFilter = { viewModel.search(filters = state.filters) },
                onUpdate = viewModel::setFilters,
            )
        }

        is BrowseMangaSourceViewModel.Dialog.AddDuplicateManga -> {
            DuplicateMangaDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = { viewModel.addFavorite(dialog.manga) },
                onOpenManga = { navigator.push(MangaRoute(dialog.duplicate.id)) },
                onMigrate = {
                    viewModel.setDialog(
                        BrowseMangaSourceViewModel.Dialog.Migrate(dialog.manga, dialog.duplicate),
                    )
                },
            )
        }

        is BrowseMangaSourceViewModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                oldManga = dialog.oldManga,
                newManga = dialog.newManga,
                screenModel = metroViewModel<MigrateMangaDialogScreenModel>(),
                onDismissRequest = onDismissRequest,
                onClickTitle = { navigator.push(MangaRoute(dialog.oldManga.id)) },
                onPopScreen = {
                    onDismissRequest()
                },
            )
        }

        is BrowseMangaSourceViewModel.Dialog.RemoveManga -> {
            RemoveEntryDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = {
                    viewModel.changeMangaFavorite(dialog.manga)
                },
                entryToRemove = dialog.manga.title,
            )
        }

        is BrowseMangaSourceViewModel.Dialog.ChangeMangaCategory -> {
            ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = onDismissRequest,
                onEditCategories = {
                    navigator.push(CategoryRoute)
                    resultEventBus.sendResult(result = CategoriesShowMangaEvent)
                },
                onConfirm = { include, _ ->
                    viewModel.changeMangaFavorite(dialog.manga)
                    viewModel.moveMangaToCategories(dialog.manga, include)
                },
            )
        }

        else -> {}
    }

    ResultEffect<BrowseMangaSearchEvent> {
        if (it.genre) {
            viewModel.searchGenre(it.query)
        } else {
            viewModel.search(it.query)
        }
    }
}
