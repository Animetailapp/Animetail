package eu.kanade.tachiyomi.ui.browse.manga.migration.search

import androidx.compose.material3.Icon
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.result.LocalResultEventBus
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.manga.BrowseSourceContent
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceViewModel
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.SourceFilterMangaDialog
import eu.kanade.tachiyomi.ui.home.TabEvent
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaMigrationListRoute
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.WebViewRoute
import mihon.feature.migration.list.MangaMatchOverrideEvent
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.manga.LocalMangaSource

@Composable
fun MangaSourceSearchScreen(
    currentMangaId: Long,
    sourceId: Long,
    query: String?,
) {
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.current
    val resultEventBus = LocalResultEventBus.current

    val viewModel =
        assistedMetroViewModel<BrowseMangaSourceViewModel, BrowseMangaSourceViewModel.Factory> {
            create(sourceId = sourceId, listingQuery = query)
        }
    val state by viewModel.state.collectAsState()

    val source = state.source
    if (source == null) {
        LoadingScreen()
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }

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
            SmallExtendedFloatingActionButton(
                text = { Text(text = stringResource(MR.strings.action_filter)) },
                icon = { Icon(MaterialSymbols.Rounded.FilterList, contentDescription = null) },
                onClick = viewModel::openFilterSheet,
                modifier = Modifier.animateFloatingActionButton(
                    visible = state.filters.isNotEmpty(),
                    alignment = Alignment.BottomEnd,
                ),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        val openMigrateDialog: (Manga) -> Unit = {
            val migrateListRoute = navigator.routes
                .filterIsInstance<MangaMigrationListRoute>()
                .lastOrNull()

            if (migrateListRoute == null) {
                viewModel.showMigrateDialog(currentMangaId = currentMangaId, target = it)
            } else {
                resultEventBus.sendResult(
                    result = MangaMatchOverrideEvent(current = currentMangaId, target = it.id),
                )
                navigator.popTo { route -> route is MangaMigrationListRoute }
            }
        }
        BrowseSourceContent(
            source = source,
            mangaList = viewModel.mangaPagerFlowFlow.collectAsLazyPagingItems(),
            columns = viewModel.getColumnsPreference(LocalConfiguration.current.orientation),
            displayMode = viewModel.displayMode,
            snackbarHostState = snackbarHostState,
            contentPadding = paddingValues,
            onWebViewClick = {
                val httpSource = source as? HttpSource ?: return@BrowseSourceContent
                navigator.push(
                    WebViewRoute(
                        url = httpSource.getHomeUrl(),
                        initialTitle = httpSource.name,
                        sourceId = httpSource.id,
                    ),
                )
            },
            onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
            onLocalSourceHelpClick = { uriHandler.openUri(LocalMangaSource.HELP_URL) },
            onMangaClick = openMigrateDialog,
            onMangaLongClick = { navigator.push(MangaRoute(it.id, true)) },
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

        is BrowseMangaSourceViewModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                oldManga = dialog.oldManga,
                newManga = dialog.newManga,
                screenModel = metroViewModel<MigrateMangaDialogScreenModel>(),
                onDismissRequest = onDismissRequest,
                onClickTitle = { navigator.push(MangaRoute(dialog.newManga.id)) },
                onPopScreen = {
                    navigator.popToRoot()
                    resultEventBus.sendResult<TabEvent>(TabEvent.Browse())
                    navigator.push(MangaRoute(dialog.newManga.id))
                },
            )
        }

        else -> {}
    }
}
