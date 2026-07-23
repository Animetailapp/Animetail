package eu.kanade.tachiyomi.ui.entries.manga

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifMangaSourcesLoaded
import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.components.DeleteItemsDialog
import eu.kanade.presentation.entries.components.SetDateDialog
import eu.kanade.presentation.entries.components.SetIntervalDialog
import eu.kanade.presentation.entries.manga.ChapterSettingsDialog
import eu.kanade.presentation.entries.manga.DuplicateMangaDialog
import eu.kanade.presentation.entries.manga.MangaScreen
import eu.kanade.presentation.entries.manga.components.MangaCoverDialog
import eu.kanade.presentation.entries.manga.components.ScanlatorFilterDialog
import eu.kanade.presentation.theme.CoverBasedTheme
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.manga.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialog
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.manga.notes.MangaNotesScreen
import eu.kanade.tachiyomi.ui.entries.manga.track.MangaTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.feature.migration.config.MangaMigrationConfigScreen
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.presentation.core.screens.LoadingScreen

class MangaScreen(
    private val mangaId: Long,
    val fromSource: Boolean = false,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifMangaSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val viewModel = viewModel<MangaViewModel>(
            factory = MangaViewModel.Factory,
            extras = CreationExtras {
                set(MangaViewModel.MANGA_ID_KEY, mangaId)
                set(MangaViewModel.IS_FROM_SOURCE_KEY, fromSource)
            },
        )

        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state is MangaViewModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as MangaViewModel.State.Success
        val isHttpSource = remember { successState.source is HttpSource }

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
                        navigator.push(CategoriesTab)
                        CategoriesTab.showMangaCategory()
                    },
                    onConfirm = { include, _ ->
                        viewModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
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
                    onOpenManga = { navigator.push(MangaScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        viewModel.showMigrateDialog(dialog.duplicate)
                    },
                )
            }

            is MangaViewModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    oldManga = dialog.oldManga,
                    newManga = dialog.newManga,
                    screenModel = MigrateMangaDialogScreenModel(),
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(MangaScreen(dialog.oldManga.id)) },
                    onPopScreen = { navigator.replace(MangaScreen(dialog.newManga.id)) },
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
                NavigatorAdaptiveSheet(
                    screen = MangaTrackInfoDialogHomeScreen(
                        mangaId = successState.manga.id,
                        mangaTitle = successState.manga.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is MangaTrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }

            MangaViewModel.Dialog.FullCover -> {
                val sm = viewModel<MangaCoverViewModel>(
                    factory = MangaCoverViewModel.Factory,
                    extras = CreationExtras {
                        set(MangaCoverViewModel.MANGA_ID_KEY, successState.manga.id)
                    },
                )
                val manga by sm.state.collectAsState()
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
                        isCustomCover = remember(manga) { manga!!.hasCustomCover() },
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
    fun MangaDetailContent(
        context: Context,
        viewModel: MangaViewModel,
        successState: MangaViewModel.State.Success,
        showRelatedMangasScreen: () -> Unit,
        navigator: Navigator,
        scope: CoroutineScope,
    ) {
        val isHttpSource = remember { successState.source is HttpSource }
        val haptic = LocalHapticFeedback.current

        MangaScreen(
            state = successState,
            snackbarHostState = viewModel.snackbarHostState,
            nextUpdate = successState.manga.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = viewModel.chapterSwipeStartAction,
            chapterSwipeEndAction = viewModel.chapterSwipeEndAction,
            navigateUp = navigator::pop,
            onChapterClicked = { openChapter(context, it) },
            onDownloadChapter = viewModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                viewModel.toggleFavorite()
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
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    viewModel.showTrackDialog()
                }
            },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, viewModel.source!!) } },
            onFilterButtonClicked = viewModel::showSettingsDialog,
            onRefresh = viewModel::fetchAllFromSource,
            onContinueReading = { continueReading(context, viewModel.getNextUnreadChapter()) },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
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
                navigator.push(MangaMigrationConfigScreen(successState.manga.id))
            }.takeIf { successState.manga.favorite },
            onEditNotesClicked = {
                navigator.push(
                    MangaNotesScreen(
                        mangaId = successState.manga.id,
                        mangaTitle = successState.manga.title,
                        mangaNotes = successState.manga.notes,
                    ),
                )
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
                    navigator.push(MangaScreen(manga.id, true))
                }
            },
            onRelatedMangaLongClick = {
                scope.launch {
                    val manga = withIOContext { viewModel.networkToLocalManga.getLocal(it) }
                    navigator.push(MangaScreen(manga.id, true))
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
                WebViewScreen(
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
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalMangaSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                MangaLibraryTab.search(query)
            }

            is BrowseMangaSourceScreen -> {
                navigator.pop()
                previousController.search(query)
            }
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(
        navigator: Navigator,
        genreName: String,
        source: MangaSource,
    ) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseMangaSourceScreen && source is HttpSource) {
            navigator.pop()
            previousController.searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
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
}
