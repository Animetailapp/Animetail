package eu.kanade.tachiyomi.ui.entries.manga

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEventBus
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.presentation.browse.RemoveEntryDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.components.DeleteItemsDialog
import eu.kanade.presentation.entries.components.SetDateDialog
import eu.kanade.presentation.entries.components.SetIntervalDialog
import eu.kanade.presentation.entries.manga.ChapterSettingsDialog
import eu.kanade.presentation.entries.manga.DuplicateMangaDialog
import eu.kanade.presentation.entries.manga.MangaScreen
import eu.kanade.presentation.entries.manga.components.MangaCoverDialog
import eu.kanade.presentation.entries.manga.components.ScanlatorFilterDialog
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.more.settings.screen.openSettings
import eu.kanade.presentation.theme.CoverBasedTheme
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.manga.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialog
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSearchEvent
import eu.kanade.tachiyomi.ui.category.CategoriesShowMangaEvent
import eu.kanade.tachiyomi.ui.home.TabEvent
import eu.kanade.tachiyomi.ui.home.TopLevelRoute
import eu.kanade.tachiyomi.ui.library.manga.MangaLibrarySearchEvent
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.core.navigation.BrowseMangaSourceRoute
import mihon.core.navigation.CategoryRoute
import mihon.core.navigation.GlobalMangaSearchRoute
import mihon.core.navigation.HomeRoute
import mihon.core.navigation.LocalAssistUrl
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaMigrationConfigRoute
import mihon.core.navigation.MangaNotesRoute
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.MangaTrackInfoDialogRoute
import mihon.core.navigation.Navigator
import mihon.core.navigation.WebViewRoute
import mihon.presentation.core.util.isTabletUi
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun MangaScreen(
    mangaId: Long,
    fromSource: Boolean = false,
) {
    val navigator = LocalNavigator.current
    val resultEventBus = LocalResultEventBus.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel =
        assistedMetroViewModel<MangaViewModel, MangaViewModel.Factory> {
            create(mangaId = mangaId, isFromSource = fromSource)
        }

    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state is MangaViewModel.State.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as MangaViewModel.State.Success
    val isHttpSource = remember { successState.source is HttpSource }
    var assistUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(successState.manga, viewModel.source) {
        if (isHttpSource) {
            try {
                withIOContext {
                    assistUrl = getMangaUrl(viewModel.manga, viewModel.source)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
            }
        }
    }
    LocalAssistUrl.current(assistUrl)

    // KMK -->
    val showingRelatedMangasScreen = rememberSaveable { mutableStateOf(false) }

    BackHandler(showingRelatedMangasScreen.value) {
        when {
            showingRelatedMangasScreen.value -> showingRelatedMangasScreen.value = false
        }
    }

    val content = @Composable {
        Crossfade(
            targetState = showingRelatedMangasScreen.value,
            label = "manga_related_crossfade",
        ) { showRelatedMangasScreen ->
            when (showRelatedMangasScreen) {
                true -> RelatedMangasScreen(
                    viewModel = viewModel,
                    successState = successState,
                    navigateUp = { showingRelatedMangasScreen.value = false },
                    navigator = navigator,
                    scope = scope,
                )

                false -> MangaDetailContent(
                    context = context,
                    viewModel = viewModel,
                    successState = successState,
                    showRelatedMangasScreen = { showingRelatedMangasScreen.value = true },
                    navigator = navigator,
                    resultEventBus = resultEventBus,
                    scope = scope,
                )
            }
        }
    }
    TachiyomiTheme {
        CoverBasedTheme(manga = successState.manga) {
            content()
        }
    }
    // KMK <--

    var showScanlatorsDialog by remember { mutableStateOf(false) }

    val onDismissRequest = {
        viewModel.dismissDialog()
        if (viewModel.autoOpenTrack && viewModel.isFromChangeCategory) {
            viewModel.isFromChangeCategory = false
            viewModel.showTrackDialog()
        }
    }
    when (val dialog = successState.dialog) {
        null -> {}

        is MangaViewModel.Dialog.ChangeCategory -> {
            ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = onDismissRequest,
                onEditCategories = {
                    navigator.push(CategoryRoute)
                    resultEventBus.sendResult(result = CategoriesShowMangaEvent)
                },
                onConfirm = { include, _ ->
                    viewModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                },
            )
        }

        MangaViewModel.Dialog.RemoveFromLibrary -> {
            RemoveEntryDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = {
                    if (successState.manga.favorite) viewModel.toggleFavorite()
                },
                entryToRemove = successState.manga.title,
            )
        }

        is MangaViewModel.Dialog.DeleteChapters -> {
            DeleteItemsDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = {
                    viewModel.toggleAllSelection(false)
                    viewModel.deleteChapters(dialog.chapters)
                },
                isManga = true,
            )
        }

        is MangaViewModel.Dialog.SetChapterDate -> {
            SetDateDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = { dateMillis ->
                    viewModel.setChapterDateOverride(dialog.chapters, dateMillis)
                    viewModel.dismissDialog()
                },
                onRemove = {
                    viewModel.setChapterDateOverride(dialog.chapters, 0)
                    viewModel.dismissDialog()
                },
                initialDateMillis = dialog.chapters.firstOrNull()?.let {
                    it.dateUploadOverride.takeIf { d -> d > 0 } ?: it.dateUpload
                } ?: 0,
            )
        }

        is MangaViewModel.Dialog.DuplicateManga -> {
            DuplicateMangaDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = { viewModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                onOpenManga = { navigator.push(MangaRoute(dialog.duplicate.id)) },
                onMigrate = {
                    viewModel.showMigrateDialog(dialog.duplicate)
                },
            )
        }

        is MangaViewModel.Dialog.Migrate -> {
            MigrateMangaDialog(
                oldManga = dialog.oldManga,
                newManga = dialog.newManga,
                screenModel = metroViewModel<MigrateMangaDialogScreenModel>(),
                onDismissRequest = onDismissRequest,
                onClickTitle = { navigator.push(MangaRoute(dialog.oldManga.id)) },
                onPopScreen = { navigator.replace(MangaRoute(dialog.newManga.id)) },
            )
        }

        MangaViewModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
            onDismissRequest = onDismissRequest,
            manga = successState.manga,
            onDownloadFilterChanged = viewModel::setDownloadedFilter,
            onUnreadFilterChanged = viewModel::setUnreadFilter,
            onBookmarkedFilterChanged = viewModel::setBookmarkedFilter,
            onSortModeChanged = viewModel::setSorting,
            onDisplayModeChanged = viewModel::setDisplayMode,
            onSetAsDefault = viewModel::setCurrentSettingsAsDefault,
            onResetToDefault = viewModel::resetToDefaultSettings,
            scanlatorFilterActive = successState.scanlatorFilterActive,
            onScanlatorFilterClicked = { showScanlatorsDialog = true },
        )

        MangaViewModel.Dialog.TrackSheet -> {
            LaunchedEffect(dialog) {
                onDismissRequest()
                navigator.push(
                    MangaTrackInfoDialogRoute(
                        mangaId = successState.manga.id,
                        mangaTitle = successState.manga.title,
                        sourceId = successState.source.id,
                    ),
                )
            }
        }

        MangaViewModel.Dialog.FullCover -> {
            val sm = assistedMetroViewModel<MangaCoverViewModel, MangaCoverViewModel.Factory> {
                create(mangaId = successState.manga.id)
            }
            val manga by sm.state.collectAsStateWithLifecycle()
            if (manga != null) {
                val getContent = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent(),
                ) {
                    if (it == null) return@rememberLauncherForActivityResult
                    sm.editCover(context, it)
                }
                MangaCoverDialog(
                    manga = manga!!,
                    snackbarHostState = sm.snackbarHostState,
                    isCustomCover = remember(manga) { manga!!.hasCustomCover(context.appGraph.mangaCoverCache) },
                    onShareClick = { sm.shareCover(context) },
                    onSaveClick = { sm.saveCover(context) },
                    onEditClick = {
                        when (it) {
                            EditCoverAction.EDIT -> getContent.launch("image/*")
                            EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                        }
                    },
                    onDismissRequest = onDismissRequest,
                )
            } else {
                LoadingScreen(Modifier.systemBarsPadding())
            }
        }

        // SY -->
        is MangaViewModel.Dialog.EditMangaInfo -> {
            EditMangaDialog(
                manga = dialog.manga,
                onDismissRequest = viewModel::dismissDialog,
                onPositiveClick = viewModel::updateMangaInfo,
            )
        }

        // SY <--
        is MangaViewModel.Dialog.SetMangaFetchInterval -> {
            SetIntervalDialog(
                interval = dialog.manga.fetchInterval,
                nextUpdate = dialog.manga.expectedNextUpdate,
                onDismissRequest = onDismissRequest,
                isManga = true,
                onValueChanged = { interval: Int -> viewModel.setFetchInterval(dialog.manga, interval) }
                    .takeIf { viewModel.isUpdateIntervalEnabled },
            )
        }
    }

    if (showScanlatorsDialog) {
        ScanlatorFilterDialog(
            availableScanlators = successState.availableScanlators,
            excludedScanlators = successState.excludedScanlators,
            onDismissRequest = { showScanlatorsDialog = false },
            onConfirm = viewModel::setExcludedScanlators,
        )
    }
}

// KMK -->
@Composable
private fun MangaDetailContent(
    context: Context,
    viewModel: MangaViewModel,
    successState: MangaViewModel.State.Success,
    showRelatedMangasScreen: () -> Unit,
    navigator: Navigator,
    resultEventBus: ResultEventBus,
    scope: CoroutineScope,
) {
    val isHttpSource = remember { successState.source is HttpSource }
    val haptic = LocalHapticFeedback.current
    val isTabletUi = isTabletUi()

    MangaScreen(
        state = successState,
        snackbarHostState = viewModel.snackbarHostState,
        nextUpdate = successState.manga.expectedNextUpdate,
        isTabletUi = isTabletUi,
        chapterSwipeStartAction = viewModel.chapterSwipeStartAction,
        chapterSwipeEndAction = viewModel.chapterSwipeEndAction,
        navigateUp = navigator::pop,
        onChapterClicked = { openChapter(context, it) },
        onDownloadChapter = viewModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
        downloadProgressProvider = viewModel::getDownloadProgress,
        onAddToLibraryClicked = {
            if (successState.manga.favorite) {
                viewModel.showRemoveFromLibraryDialog()
            } else {
                viewModel.toggleFavorite()
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onWebViewClicked = {
            openMangaInWebView(
                navigator,
                viewModel.manga,
                viewModel.source,
            )
        }.takeIf { isHttpSource },
        onWebViewLongClicked = {
            copyMangaUrl(
                context,
                viewModel.manga,
                viewModel.source,
            )
        }.takeIf { isHttpSource },
        onTrackingClicked = {
            if (!successState.hasLoggedInTrackers) {
                navigator.openSettings(SettingsTrackingScreen, isTabletUi)
            } else {
                viewModel.showTrackDialog()
            }
        },
        onTagSearch = { performGenreSearch(navigator, resultEventBus, it, viewModel.source!!) },
        onFilterButtonClicked = viewModel::showSettingsDialog,
        onRefresh = viewModel::fetchAllFromSource,
        onContinueReading = { continueReading(context, viewModel.getNextUnreadChapter()) },
        onSearch = { query, global -> performSearch(navigator, resultEventBus, query, global) },
        onCoverClicked = viewModel::showCoverDialog,
        onShareClicked = { shareManga(context, viewModel.manga, viewModel.source) }.takeIf { isHttpSource },
        onDownloadActionClicked = viewModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
        onEditCategoryClicked = viewModel::showChangeCategoryDialog.takeIf { successState.manga.favorite },
        // SY -->
        onEditInfoClicked = viewModel::showEditMangaInfoDialog,
        // SY <--
        onEditFetchIntervalClicked = viewModel::showSetMangaFetchIntervalDialog.takeIf {
            successState.manga.favorite
        },
        onMigrateClicked = {
            navigator.push(MangaMigrationConfigRoute(successState.manga.id))
        }.takeIf { successState.manga.favorite },
        onEditNotesClicked = {
            navigator.push(MangaNotesRoute(mangaId = successState.manga.id))
        },
        getMangaState = { viewModel.getManga(initialManga = it) },
        onRelatedMangasScreenClick = {
            if (successState.isRelatedMangasFetched == null) {
                scope.launchIO { viewModel.fetchRelatedMangasFromSource(onDemand = true) }
            }
            showRelatedMangasScreen()
        },
        onRelatedMangaClick = {
            scope.launch {
                val manga = withIOContext { viewModel.networkToLocalManga.getLocal(it) }
                navigator.push(MangaRoute(manga.id, true))
            }
        },
        onRelatedMangaLongClick = {
            scope.launch {
                val manga = withIOContext { viewModel.networkToLocalManga.getLocal(it) }
                navigator.push(MangaRoute(manga.id, true))
            }
        },
        onMultiBookmarkClicked = viewModel::bookmarkChapters,
        onMultiMarkAsReadClicked = viewModel::markChaptersRead,
        onMarkPreviousAsReadClicked = viewModel::markPreviousChapterRead,
        onMultiDeleteClicked = viewModel::showDeleteChapterDialog,
        onSetDateClicked = viewModel::showSetChapterDateDialog,
        onChapterSwipe = viewModel::chapterSwipe,
        onChapterSelected = viewModel::toggleSelection,
        onAllChapterSelected = viewModel::toggleAllSelection,
        onInvertSelection = viewModel::invertSelection,
    )
}
// KMK <--

private fun continueReading(context: Context, unreadChapter: Chapter?) {
    if (unreadChapter != null) openChapter(context, unreadChapter)
}

private fun openChapter(context: Context, chapter: Chapter) {
    context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
}

private fun getMangaUrl(manga_: Manga?, source_: MangaSource?): String? {
    val manga = manga_ ?: return null
    val source = source_ as? HttpSource ?: return null

    return try {
        source.getMangaUrl(manga.toSManga())
    } catch (e: Exception) {
        null
    }
}

private fun openMangaInWebView(navigator: Navigator, manga_: Manga?, source_: MangaSource?) {
    getMangaUrl(manga_, source_)?.let { url ->
        navigator.push(
            WebViewRoute(
                url = url,
                initialTitle = manga_?.title,
                sourceId = source_?.id,
            ),
        )
    }
}

private fun shareManga(context: Context, manga_: Manga?, source_: MangaSource?) {
    try {
        getMangaUrl(manga_, source_)?.let { url ->
            val intent = url.toUri().toShareIntent(context, type = "text/plain")
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        context.toast(e.message)
    }
}

/**
 * Perform a search using the provided query.
 *
 * @param query the search query to the parent controller
 */
private fun performSearch(
    navigator: Navigator,
    resultEventBus: ResultEventBus,
    query: String,
    global: Boolean,
) {
    if (global) {
        navigator.push(GlobalMangaSearchRoute(query))
        return
    }

    when (navigator.previous) {
        is HomeRoute -> {
            navigator.pop()
            resultEventBus.sendResult<TabEvent>(TabEvent.MangaLibrary(query = query))
        }

        is TopLevelRoute.MangaLibrary -> {
            navigator.pop()
            resultEventBus.sendResult(MangaLibrarySearchEvent(query))
        }

        is BrowseMangaSourceRoute -> {
            navigator.pop()
            resultEventBus.sendResult(BrowseMangaSearchEvent(query))
        }
    }
}

/**
 * Performs a genre search using the provided genre name.
 *
 * @param genreName the search genre to the parent controller
 */
private fun performGenreSearch(
    navigator: Navigator,
    resultEventBus: ResultEventBus,
    genreName: String,
    source: MangaSource,
) {
    if (navigator.previous is BrowseMangaSourceRoute && source is HttpSource) {
        navigator.pop()
        resultEventBus.sendResult(BrowseMangaSearchEvent(genreName, genre = true))
    } else {
        performSearch(navigator, resultEventBus, genreName, global = false)
    }
}

/**
 * Copy Manga URL to Clipboard
 */
private fun copyMangaUrl(context: Context, manga_: Manga?, source_: MangaSource?) {
    val manga = manga_ ?: return
    val source = source_ as? HttpSource ?: return
    val url = source.getMangaUrl(manga.toSManga())
    context.copyToClipboard(url, url)
}
