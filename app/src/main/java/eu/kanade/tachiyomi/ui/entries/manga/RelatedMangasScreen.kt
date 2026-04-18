package eu.kanade.tachiyomi.ui.entries.manga

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.browse.anime.RelatedAnimeTitle
import eu.kanade.presentation.browse.anime.RelatedAnimesLoadingItem
import eu.kanade.presentation.browse.anime.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import kotlinx.coroutines.CoroutineScope
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun RelatedMangasScreen(
    screenModel: MangaScreenModel,
    navigateUp: () -> Unit,
    navigator: Navigator,
    scope: CoroutineScope,
    successState: MangaScreenModel.State.Success,
) {
    val sourcePreferences: SourcePreferences = Injekt.get()
    var displayMode by sourcePreferences.sourceDisplayMode.asState(scope)

    val haptic = LocalHapticFeedback.current

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { scrollBehavior ->
            BrowseSourceSimpleToolbar(
                navigateUp = navigateUp,
                title = successState.manga.title,
                displayMode = displayMode,
                onDisplayModeChange = { displayMode = it },
                scrollBehavior = scrollBehavior,
                toggleSelectionMode = {},
                isRunning = false,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        RelatedMangasContent(
            relatedMangas = successState.relatedMangasSorted,
            getMangaState = { manga -> screenModel.getManga(initialManga = manga) },
            columns = getColumnsPreference(LocalConfiguration.current.orientation),
            contentPadding = paddingValues,
            onMangaClick = {
                scope.launchIO {
                    val manga = screenModel.networkToLocalManga.getLocal(it)
                    navigator.push(MangaScreen(manga.id, true))
                }
            },
            onMangaLongClick = {
                scope.launchIO {
                    val manga = screenModel.networkToLocalManga.getLocal(it)
                    navigator.push(MangaScreen(manga.id, true))
                }
            },
            onKeywordClick = { query ->
                navigator.push(BrowseMangaSourceScreen(successState.source.id, query))
            },
            onKeywordLongClick = { query ->
                navigator.push(GlobalMangaSearchScreen(query))
            },
            selection = emptyList(),
        )
    }
}

@Composable
fun RelatedMangasContent(
    relatedMangas: List<RelatedManga>?,
    getMangaState: @Composable (Manga) -> State<Manga>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    onKeywordClick: (String) -> Unit,
    onKeywordLongClick: (String) -> Unit,
    selection: List<Manga>,
) {
    if (relatedMangas == null) {
        LoadingScreen(
            modifier = Modifier.then(Modifier),
        )
        return
    }

    if (relatedMangas.isEmpty()) {
        EmptyScreen(
            message = stringResource(MR.strings.no_results_found),
        )
        return
    }

    FastScrollLazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding + PaddingValues(horizontal = MaterialTheme.padding.small),
        topContentPadding = contentPadding.calculateTopPadding(),
        verticalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridHorizontalSpacer),
    ) {
        relatedMangas.forEach { related ->
            val isLoading = related is RelatedManga.Loading
            if (isLoading) {
                item(
                    key = "${related.hashCode()}#header",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    RelatedAnimeTitle(
                        title = stringResource(MR.strings.loading),
                        subtitle = null,
                        onClick = {},
                        onLongClick = null,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    )
                }
                item(
                    key = "${related.hashCode()}#content",
                    span = { GridItemSpan(maxLineSpan) },
                ) { RelatedAnimesLoadingItem() }
            } else {
                val relatedManga = related as RelatedManga.Success
                item(
                    key = "${related.hashCode()}#divider",
                    span = { GridItemSpan(maxLineSpan) },
                ) { HorizontalDivider() }
                item(
                    key = "${related.hashCode()}#header",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    RelatedAnimeTitle(
                        title = if (relatedManga.keyword.isNotBlank()) {
                            stringResource(TLMR.strings.related_mangas_more)
                        } else {
                            stringResource(TLMR.strings.related_mangas_website_suggestions)
                        },
                        showArrow = relatedManga.keyword.isNotBlank(),
                        subtitle = null,
                        onClick = {
                            if (relatedManga.keyword.isNotBlank()) onKeywordClick(relatedManga.keyword)
                        },
                        onLongClick = {
                            if (relatedManga.keyword.isNotBlank()) onKeywordLongClick(relatedManga.keyword)
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    )
                }
                items(
                    key = { "related-manga-${relatedManga.mangaList[it].url.hashCode()}" },
                    count = relatedManga.mangaList.size,
                ) { index ->
                    val manga by getMangaState(relatedManga.mangaList[index])
                    EntryComfortableGridItem(
                        title = manga.title,
                        coverData = MangaCover(
                            mangaId = manga.id,
                            sourceId = manga.source,
                            isMangaFavorite = manga.favorite,
                            url = manga.thumbnailUrl,
                            lastModified = manga.coverLastModified,
                        ),
                        coverAlpha = if (manga.favorite) {
                            CommonEntryItemDefaults.BrowseFavoriteCoverAlpha
                        } else {
                            1f
                        },
                        coverBadgeStart = {
                            InLibraryBadge(enabled = manga.favorite)
                        },
                        onLongClick = { onMangaLongClick(manga) },
                        onClick = { onMangaClick(manga) },
                    )
                }
            }
        }
    }
}

private fun getColumnsPreference(orientation: Int): GridCells {
    val libraryPreferences: LibraryPreferences = Injekt.get()

    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) {
        libraryPreferences.mangaLandscapeColumns
    } else {
        libraryPreferences.mangaPortraitColumns
    }.get()
    return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
}
