package eu.kanade.tachiyomi.ui.entries.anime

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.domain.entries.anime.model.hasCustomBackground
import eu.kanade.domain.entries.anime.model.hasCustomCover
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.anime.AnimeScreen
import eu.kanade.presentation.entries.anime.DuplicateAnimeDialog
import eu.kanade.presentation.entries.anime.EpisodeOptionsDialogScreen
import eu.kanade.presentation.entries.anime.EpisodeSettingsDialog
import eu.kanade.presentation.entries.anime.SeasonSettingsDialog
import eu.kanade.presentation.entries.anime.components.AnimeImagesDialog
import eu.kanade.presentation.entries.components.DeleteItemsDialog
import eu.kanade.presentation.entries.components.SetDateDialog
import eu.kanade.presentation.entries.components.SetIntervalDialog
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsGesturesScreen.SkipIntroLengthDialog
import eu.kanade.presentation.theme.CoverBasedTheme
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import eu.kanade.tachiyomi.source.anime.isLocalOrStub
import eu.kanade.tachiyomi.source.anime.isSourceForTorrents
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialog
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.anime.notes.AnimeNotesScreen
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.feature.migration.config.AnimeMigrationConfigScreen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeScreen(
    private val animeId: Long,
    val fromSource: Boolean = false,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    @Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod")
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val viewModel = viewModel<AnimeViewModel>(
            factory = AnimeViewModel.Factory,
            extras = CreationExtras {
                set(AnimeViewModel.ANIME_ID_KEY, animeId)
                set(AnimeViewModel.IS_FROM_SOURCE_KEY, fromSource)
            },
        )

        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state is AnimeViewModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AnimeViewModel.State.Success
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
                    true -> RelatedAnimesScreen(
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
            CoverBasedTheme(anime = successState.anime) {
                content()
            }
        }
    }

    @Composable
    fun MangaDetailContent(
        context: Context,
        viewModel: AnimeViewModel,
        successState: AnimeViewModel.State.Success,
        showRelatedMangasScreen: () -> Unit,
        navigator: Navigator,
        scope: CoroutineScope,
    ) {
        // KMK <--
        val isAnimeHttpSource = remember { successState.source is AnimeHttpSource }

        LaunchedEffect(successState.anime, viewModel.source) {
            if (isAnimeHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getAnimeUrl(viewModel.anime, viewModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get anime URL" }
                }
            }
        }
        val haptic = LocalHapticFeedback.current
        AnimeScreen(

            state = successState,
            snackbarHostState = viewModel.snackbarHostState,
            nextUpdate = successState.anime.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            episodeSwipeStartAction = viewModel.episodeSwipeStartAction,
            episodeSwipeEndAction = viewModel.episodeSwipeEndAction,
            showNextEpisodeAirTime = viewModel.showNextEpisodeAirTime,
            alwaysUseExternalPlayer = viewModel.alwaysUseExternalPlayer,
            // AM (FILE_SIZE) -->
            showFileSize = viewModel.showFileSize,
            // <-- AM (FILE_SIZE)
            navigateUp = navigator::pop,
            onEpisodeClicked = { episode, alt ->
                scope.launchIO {
                    if (viewModel.isTorrentEnabled() && successState.source.isSourceForTorrents()) {
                        TorrentServerService.start()
                    }
                    val extPlayer = viewModel.alwaysUseExternalPlayer != alt
                    openEpisode(context, episode, extPlayer)
                }
            },
            onDownloadEpisode = viewModel::runEpisodeDownloadActions.takeIf {
                !successState.source.isLocalOrStub() && successState.anime.fetchType == FetchType.Episodes
            },
            onAddToLibraryClicked = {
                viewModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onWebViewClicked = {
                openAnimeInWebView(
                    navigator,
                    viewModel.anime,
                    viewModel.source,
                )
            }.takeIf { isAnimeHttpSource },
            onWebViewLongClicked = {
                copyAnimeUrl(
                    context,
                    viewModel.anime,
                    viewModel.source,
                )
            }.takeIf { isAnimeHttpSource },
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
            onContinueWatching = {
                scope.launchIO {
                    val extPlayer = viewModel.alwaysUseExternalPlayer
                    continueWatching(context, viewModel.getNextUnseenEpisode(), extPlayer)
                }
            },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onCoverClicked = viewModel::showImagesDialog,
            onShareClicked = {
                shareAnime(
                    context,
                    viewModel.anime,
                    viewModel.source,
                )
            }.takeIf { isAnimeHttpSource },
            onDownloadActionClicked = viewModel::runDownloadAction.takeIf {
                !successState.source.isLocalOrStub() && successState.anime.fetchType == FetchType.Episodes
            },
            onEditCategoryClicked = viewModel::showChangeCategoryDialog.takeIf { successState.anime.favorite },
            // SY -->
            onEditInfoClicked = viewModel::showEditAnimeInfoDialog,
            // SY <--
            onEditFetchIntervalClicked = viewModel::showSetAnimeFetchIntervalDialog.takeIf {
                successState.anime.favorite
            },
            onMigrateClicked = {
                navigator.push(AnimeMigrationConfigScreen(successState.anime.id))
            }.takeIf { successState.anime.favorite },
            onEditNotesClicked = {
                navigator.push(
                    AnimeNotesScreen(
                        animeId = successState.anime.id,
                        animeTitle = successState.anime.title,
                        animeNotes = successState.anime.notes,
                    ),
                )
            },
            changeAnimeSkipIntro = viewModel::showAnimeSkipIntroDialog
                .takeIf { successState.anime.favorite && successState.anime.fetchType == FetchType.Episodes },
            onMultiBookmarkClicked = viewModel::bookmarkEpisodes,
            onMultiFillermarkClicked = viewModel::fillermarkEpisodes,
            onMultiMarkAsSeenClicked = viewModel::markEpisodesSeen,
            onMarkPreviousAsSeenClicked = viewModel::markPreviousEpisodeSeen,
            onMultiDeleteClicked = viewModel::showDeleteEpisodeDialog,
            onSetDateClicked = viewModel::showSetEpisodeDateDialog,
            onEpisodeSwipe = viewModel::episodeSwipe,
            onEpisodeSelected = viewModel::toggleSelection,
            onAllEpisodeSelected = viewModel::toggleAllSelection,
            onInvertSelection = viewModel::invertSelection,
            onSeasonClicked = {
                navigator.push(AnimeScreen(it.id))
            },
            onContinueWatchingClicked = {
                scope.launchIO {
                    val episode = viewModel.getNextUnseenEpisode(it.anime)
                    episode?.let { ep ->
                        openEpisode(context, ep, viewModel.alwaysUseExternalPlayer)
                    }
                }
            },
            // KMK -->
            getAnimeState = { viewModel.getManga(initialManga = it) },
            onRelatedAnimesScreenClick = {
                if (successState.isRelatedMangasFetched == null) {
                    scope.launchIO { viewModel.fetchRelatedMangasFromSource(onDemand = true) }
                }
                showRelatedMangasScreen()
            },
            onRelatedAnimeClick = {
                scope.launchIO {
                    val manga = viewModel.networkToLocalAnime.getLocal(it)
                    navigator.push(AnimeScreen(manga.id, true))
                }
            },
            onRelatedAnimeLongClick = {
                scope.launchIO {
                    val manga = viewModel.networkToLocalAnime.getLocal(it)
                }
            },
        )

        val onDismissRequest = {
            viewModel.dismissDialog()
            if (viewModel.autoOpenTrack && viewModel.isFromChangeCategory) {
                viewModel.isFromChangeCategory = false
                viewModel.showTrackDialog()
            }
        }
        when (val dialog = successState.dialog) {
            null -> {}

            is AnimeViewModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoriesTab) },
                    onConfirm = { include, _ ->
                        viewModel.moveAnimeToCategoriesAndAddToLibrary(dialog.anime, include)
                    },
                )
            }

            is AnimeViewModel.Dialog.DeleteEpisodes -> {
                DeleteItemsDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        viewModel.toggleAllSelection(false)
                        viewModel.deleteEpisodes(dialog.episodes)
                    },
                    isManga = false,
                )
            }

            is AnimeViewModel.Dialog.SetEpisodeDate -> {
                SetDateDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { dateMillis ->
                        viewModel.setEpisodeDateOverride(dialog.episodes, dateMillis)
                        viewModel.dismissDialog()
                    },
                    onRemove = {
                        viewModel.setEpisodeDateOverride(dialog.episodes, 0)
                        viewModel.dismissDialog()
                    },
                    initialDateMillis = dialog.episodes.firstOrNull()?.let {
                        it.dateUploadOverride.takeIf { d -> d > 0 } ?: it.dateUpload
                    } ?: 0,
                )
            }

            is AnimeViewModel.Dialog.DuplicateAnime -> {
                DuplicateAnimeDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { viewModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenAnime = { navigator.push(AnimeScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        viewModel.showMigrateDialog(dialog.duplicate)
                    },
                )
            }

            is AnimeViewModel.Dialog.Migrate -> {
                MigrateAnimeDialog(
                    oldAnime = dialog.oldAnime,
                    newAnime = dialog.newAnime,
                    screenModel = MigrateAnimeDialogScreenModel(),
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(AnimeScreen(dialog.oldAnime.id)) },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(dialog.oldAnime, dialog.newAnime)) },
                    onPopScreen = { navigator.replace(AnimeScreen(dialog.newAnime.id)) },
                )
            }

            AnimeViewModel.Dialog.EpisodeSettingsSheet -> EpisodeSettingsDialog(
                onDismissRequest = onDismissRequest,
                anime = successState.anime,
                onDownloadFilterChanged = viewModel::setDownloadedFilter,
                onUnseenFilterChanged = viewModel::setUnseenFilter,
                onBookmarkedFilterChanged = viewModel::setBookmarkedFilter,
                onFillermarkedFilterChanged = viewModel::setFillermarkedFilter,
                onSortModeChanged = viewModel::setSorting,
                onDisplayModeChanged = viewModel::setDisplayMode,
                onShowPreviewsEnabled = viewModel::showEpisodePreviews,
                onShowSummariesEnabled = viewModel::showEpisodeSummaries,
                onSetAsDefault = viewModel::setCurrentSettingsAsDefault,
            )

            AnimeViewModel.Dialog.SeasonSettingsSheet -> SeasonSettingsDialog(
                onDismissRequest = onDismissRequest,
                anime = successState.anime,
                onDownloadFilterChanged = viewModel::setSeasonDownloadedFilter,
                onUnseenFilterChanged = viewModel::setSeasonUnseenFilter,
                onStartedFilterChanged = viewModel::setSeasonStartedFilter,
                onCompletedFilterChanged = viewModel::setSeasonCompletedFilter,
                onBookmarkedFilterChanged = viewModel::setSeasonBookmarkedFilter,
                onFillermarkedFilterChanged = viewModel::setSeasonFillermarkedFilter,
                onSortModeChanged = viewModel::setSeasonSorting,
                onDisplayGridModeChanged = viewModel::setSeasonDisplayGridMode,
                onDisplayGridSizeChanged = viewModel::setSeasonDisplayGridSize,
                onOverlayDownloadedChanged = viewModel::setSeasonDownloadOverlay,
                onOverlayUnseenChanged = viewModel::setSeasonUnseenOverlay,
                onOverlayLocalChanged = viewModel::setSeasonLocalOverlay,
                onOverlayLangChanged = viewModel::setSeasonLangOverlay,
                onOverlayContinueChanged = viewModel::setSeasonContinueOverlay,
                onDisplayModeChanged = viewModel::setSeasonDisplayMode,
                onSetAsDefault = viewModel::setSeasonCurrentSettingsAsDefault,
            )

            AnimeViewModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = AnimeTrackInfoDialogHomeScreen(
                        animeId = successState.anime.id,
                        animeTitle = successState.anime.title,
                        sourceId = successState.source.id,
                        // AM -->
                        isSeason = successState.anime.fetchType == FetchType.Seasons,
                        // <-- AM
                    ),
                    enableSwipeDismiss = { it.lastItem is AnimeTrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }

            AnimeViewModel.Dialog.FullImages -> {
                val sm = rememberScreenModel { AnimeImageScreenModel(successState.anime.id) }
                val anime by sm.state.collectAsState()
                if (anime != null) {
                    val getContent = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editImage(context, it)
                    }
                    AnimeImagesDialog(
                        anime = anime!!,
                        snackbarHostState = sm.snackbarHostState,
                        pagerState = sm.pagerState,
                        isCustomCover = remember(anime) { anime!!.hasCustomCover() },
                        isCustomBackground = remember(anime) { anime!!.hasCustomBackground() },
                        onShareClick = { sm.shareImage(context) },
                        onSaveClick = { sm.saveImage(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomImage(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }

            // SY -->
            is AnimeViewModel.Dialog.EditAnimeInfo -> {
                EditAnimeDialog(
                    anime = dialog.anime,
                    onDismissRequest = viewModel::dismissDialog,
                    onPositiveClick = viewModel::updateAnimeInfo,
                )
            }

            // SY <--
            is AnimeViewModel.Dialog.SetAnimeFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.anime.fetchInterval,
                    nextUpdate = dialog.anime.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    isManga = false,
                    onValueChanged = { interval: Int -> viewModel.setFetchInterval(dialog.anime, interval) }
                        .takeIf { viewModel.isUpdateIntervalEnabled },
                )
            }

            AnimeViewModel.Dialog.ChangeAnimeSkipIntro -> {
                fun updateSkipIntroLength(newLength: Long) {
                    scope.launchIO {
                        viewModel.setAnimeViewerFlags.awaitSetSkipIntroLength(animeId, newLength)
                    }
                }
                SkipIntroLengthDialog(
                    initialSkipIntroLength = if (!successState.anime.skipIntroDisable &&
                        successState.anime.skipIntroLength == 0
                    ) {
                        viewModel.gesturePreferences.defaultIntroLength().get()
                    } else {
                        successState.anime.skipIntroLength
                    },
                    onDismissRequest = onDismissRequest,
                    onValueChanged = {
                        updateSkipIntroLength(it.toLong())
                        onDismissRequest()
                    },
                )
            }

            is AnimeViewModel.Dialog.ShowQualities -> {
                EpisodeOptionsDialogScreen.onDismissDialog = onDismissRequest
                val episodeTitle = if (dialog.anime.displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                    stringResource(
                        AYMR.strings.display_mode_episode,
                        formatEpisodeNumber(dialog.episode.episodeNumber),
                    )
                } else {
                    dialog.episode.name
                }
                NavigatorAdaptiveSheet(
                    screen = EpisodeOptionsDialogScreen(
                        useExternalDownloader = viewModel.useExternalDownloader,
                        episodeTitle = episodeTitle,
                        episodeId = dialog.episode.id,
                        animeId = dialog.anime.id,
                        sourceId = dialog.source.id,
                    ),
                    onDismissRequest = onDismissRequest,
                )
            }
        }
    }

    private suspend fun continueWatching(
        context: Context,
        unseenEpisode: Episode?,
        useExternalPlayer: Boolean,
    ) {
        if (unseenEpisode != null) openEpisode(context, unseenEpisode, useExternalPlayer)
    }

    private suspend fun openEpisode(context: Context, episode: Episode, useExternalPlayer: Boolean) {
        withIOContext {
            MainActivity.startPlayerActivity(
                context,
                episode.animeId,
                episode.id,
                useExternalPlayer,
            )
        }
    }

    private fun getAnimeUrl(anime_: Anime?, source_: AnimeSource?): String? {
        val anime = anime_ ?: return null
        val source = source_ as? AnimeHttpSource ?: return null

        return try {
            source.getAnimeUrl(anime.toSAnime())
        } catch (e: Exception) {
            null
        }
    }

    private fun openAnimeInWebView(navigator: Navigator, anime_: Anime?, source_: AnimeSource?) {
        getAnimeUrl(anime_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = anime_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    private fun shareAnime(context: Context, anime_: Anime?, source_: AnimeSource?) {
        try {
            getAnimeUrl(anime_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        context.stringResource(MR.strings.action_share),
                    ),
                )
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
            navigator.push(GlobalAnimeSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                AnimeLibraryTab.search(query)
            }

            is BrowseAnimeSourceScreen -> {
                navigator.pop()
                (previousController as BrowseAnimeSourceScreen).search(query)
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
        source: AnimeSource,
    ) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseAnimeSourceScreen && source is AnimeHttpSource) {
            navigator.pop()
            (previousController as BrowseAnimeSourceScreen).searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
        }
    }

    /**
     * Copy Anime URL to Clipboard
     */
    private fun copyAnimeUrl(context: Context, anime_: Anime?, source_: AnimeSource?) {
        val anime = anime_ ?: return
        val source = source_ as? AnimeHttpSource ?: return
        val url = source.getAnimeUrl(anime.toSAnime())
        context.copyToClipboard(url, url)
    }
}
