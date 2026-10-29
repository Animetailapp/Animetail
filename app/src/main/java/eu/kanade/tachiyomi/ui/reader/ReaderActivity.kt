package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.presentation.reader.DisplayRefreshHost
import eu.kanade.presentation.reader.OrientationSelectDialog
import eu.kanade.presentation.reader.PageIndicatorText
import eu.kanade.presentation.reader.ReaderContentOverlay
import eu.kanade.presentation.reader.ReaderNavigationOverlay
import eu.kanade.presentation.reader.ReaderNavigationOverlayState
import eu.kanade.presentation.reader.ReaderPageActionsDialog
import eu.kanade.presentation.reader.ReadingModeSelectDialog
import eu.kanade.presentation.reader.appbars.ReaderAppBars
import eu.kanade.presentation.reader.components.ChapterNavigatorType
import eu.kanade.presentation.reader.settings.ReaderSettingsDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.ReaderData
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.manga.isNsfw
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.GeneralSettings
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.LocalPageColorFilter
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.Action
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import eu.kanade.tachiyomi.ui.reader.viewer.collectChanges
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import eu.kanade.tachiyomi.util.system.isNightMode
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.readerBackgroundColor
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
class ReaderActivity : BaseActivity() {

    private val graph: AppGraph by lazy { metroGraph() }

    companion object {
        fun newIntent(
            context: Context,
            mangaId: Long?,
            chapterId: Long?,
            page: Int? = null,
        ): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                // SY -->
                putExtra("page", page)
                // SY <--
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    @Inject private lateinit var readerPreferences: ReaderPreferences

    @Inject private lateinit var preferences: BasePreferences

    @Inject private lateinit var connectionsPreferences: ConnectionsPreferences
    val viewModel by viewModels<ReaderViewModel> { graph.viewModelFactory }
    val settingsViewModel by viewModels<ReaderSettingsViewModel> {
        viewModelFactory {
            initializer { ReaderSettingsViewModel(viewModel, readerPreferences, preferences, hasCutout) }
        }
    }
    private var assistUrl: String? = null

    private val hasCutout by lazy { hasDisplayCutout() }
    private var menuToggleToast: Toast? = null
    private var readingModeToast: Toast? = null
    private val displayRefreshHost = DisplayRefreshHost()
    private val navigationOverlayState = ReaderNavigationOverlayState()

    private val windowInsetsController by lazy { WindowInsetsControllerCompat(window, window.decorView) }

    private var isLoadingViewer by mutableStateOf(false)

    /**
     * Viewer used to display the pages (pager, webtoon, ...).
     */
    private val viewerFlow = MutableStateFlow<Viewer?>(null)
    private val viewer: Viewer? inline get() = viewerFlow.value

    var isScrollingThroughPages = false
        private set

    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        registerSecureActivity(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }

        super.onCreate(savedInstanceState)

        setComposeContent {
            val state by viewModel.state.collectAsState()
            val general by settingsViewModel.general.collectAsState()
            val showPageNumber by readerPreferences.showPageNumber.collectAsState()
            val colorOverlayEnabled by readerPreferences.colorFilter.collectAsState()
            val colorOverlay by readerPreferences.colorFilterValue.collectAsState()
            val colorOverlayMode by readerPreferences.colorFilterMode.collectAsState()
            val brightness by settingsViewModel.brightness.collectAsState()
            val navigation = settingsViewModel.navigation.collectAsState().value

            val backgroundColor = remember(general.theme) { Color(baseContext.readerBackgroundColor(general.theme)) }
            val colorOverlayBlendMode = remember(colorOverlayMode) {
                ReaderPreferences.ColorFilterMode.getOrNull(colorOverlayMode)?.second
            }

            LaunchedEffect(brightness) { setWindowBrightness(brightness) }

            val forceNavigationOverlay = remember { settingsViewModel.takeNavigationOverlayForNewUser() }
            LaunchedEffect(navigation) {
                if (navigation == null) return@LaunchedEffect
                val showOnStart = forceNavigationOverlay || settingsViewModel.general.value.navigationOverlayOnStart
                navigationOverlayState.setNavigation(navigation, showOnStart)
            }

            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .onPreviewKeyEvent(::onKeyEvent)
                    .focusRequester(focusRequester)
                    .focusable()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            navigationOverlayState.hide()
                        }
                    }
                    .then(if (general.keepScreenOn) Modifier.keepScreenOn() else Modifier),
            ) {
                ReaderViewer()

                if (isLoadingViewer) {
                    LoadingScreen()
                    return@Box
                }

                ReaderContentOverlay(
                    brightness = brightness,
                    color = colorOverlay.takeIf { colorOverlayEnabled },
                    colorBlendMode = colorOverlayBlendMode,
                )

                if (!state.menuVisible && showPageNumber) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                        PageIndicatorText(currentPage = state.currentPageText, totalPages = state.totalPages)
                    }
                }

                ReaderNavigationOverlay(state = navigationOverlayState)

                ReaderMenus()
            }
        }

        if (!viewModel.hasValidArgs) {
            finish()
            return
        }

        NotificationReceiver.dismissNotification(
            this,
            viewModel.mangaId.hashCode(),
            Notifications.ID_NEW_CHAPTERS,
        )

        settingsViewModel.general.collectChanges(lifecycleScope, ::applyGeneralSettings)
        setMenuVisibility(viewModel.state.value.menuVisible)
        enableExhAutoScroll()
        readerPreferences.pageLayout.changes()
            .drop(1)
            .onEach { viewModel.setDoublePages((viewer as? PagerViewer)?.config?.doublePages ?: false) }
            .launchIn(lifecycleScope)
        readerPreferences.dualPageSplitPaged.changes()
            .drop(1)
            .onEach {
                if (viewer !is PagerViewer) return@onEach
                reloadChapters(
                    !it && when (readerPreferences.pageLayout.get()) {
                        PagerConfig.PageLayout.DOUBLE_PAGES -> true

                        PagerConfig.PageLayout.AUTOMATIC ->
                            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                        else -> false
                    },
                    true,
                )
            }
            .launchIn(lifecycleScope)

        // Finish when incognito mode is disabled
        preferences.incognitoMode.changes()
            .drop(1)
            .onEach { if (!it) finish() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.initError }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setInitialChapterError)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.manga }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { updateViewer() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.viewerChapters }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setChapters)
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    ReaderViewModel.Event.ReloadViewerChapters -> {
                        viewModel.state.value.viewerChapters?.let(::setChapters)
                    }

                    ReaderViewModel.Event.PageChanged -> {
                        displayRefreshHost.flash()
                    }

                    is ReaderViewModel.Event.SetOrientation -> {
                        setOrientation(event.orientation)
                    }

                    is ReaderViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }

                    is ReaderViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.page, event.secondPage)
                    }

                    is ReaderViewModel.Event.CopyImage -> {
                        onCopyImageResult(event.uri)
                    }

                    is ReaderViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                }
            }
            .launchIn(lifecycleScope)

        lifecycleScope.launchUI {
            // AM (DISCORD) -->
            updateDiscordRPC(exitingReader = false)
            // <-- AM (DISCORD)
        }
    }

    /**
     * Called when the activity is destroyed. Cleans up the viewer and any view.
     */
    override fun onDestroy() {
        super.onDestroy()
        viewer?.destroy()
        menuToggleToast?.cancel()
        readingModeToast?.cancel()
        // AM (DISCORD) -->
        updateDiscordRPC(exitingReader = true)
        // <-- AM (DISCORD)
    }

    override fun onPause() {
        viewModel.flushReadTimer()
        super.onPause()
    }

    /**
     * Set menu visibility again on activity resume to apply immersive mode again if needed.
     * Helps with rotations.
     */
    override fun onResume() {
        super.onResume()
        viewModel.restartReadTimer()
        setMenuVisibility(viewModel.state.value.menuVisible)
    }

    /**
     * Called when the window focus changes. It sets the menu visibility to the last known state
     * to apply immersive mode again if needed.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setMenuVisibility(viewModel.state.value.menuVisible)
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    /**
     * Called when the user clicks the back key or the button on the toolbar. The call is
     * delegated to the presenter.
     */
    override fun finish() {
        viewModel.onActivityFinish()
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    @Composable
    private fun ReaderViewer() {
        val viewer by viewerFlow.collectAsState()
        val general by settingsViewModel.general.collectAsState()
        val insets = when {
            !general.fullscreen -> WindowInsets.systemBars
            !general.drawUnderCutout -> WindowInsets.displayCutout
            else -> WindowInsets(0)
        }
        val colorFilter = remember(general.grayscale, general.invertedColors) {
            viewerColorFilter(general.grayscale, general.invertedColors)
        }

        // Each viewer filters its page images alone: transition, loading and error pages keep their colours.
        CompositionLocalProvider(LocalPageColorFilter provides colorFilter) {
            key(viewer) {
                viewer?.Content(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(insets),
                )
            }
        }
    }

    /**
     * Handles a key [event] before anything else in the reader. Movement happens on key up.
     */
    private fun onKeyEvent(event: KeyEvent): Boolean {
        val viewer = viewer ?: return false
        val navigation = settingsViewModel.navigation.value ?: return false
        val isUp = event.type == KeyEventType.KeyUp
        val action = when (event.key) {
            Key.VolumeDown, Key.VolumeUp -> {
                val general = settingsViewModel.general.value
                if (!general.volumeKeysEnabled || viewModel.state.value.menuVisible) return false
                if ((event.key == Key.VolumeDown) != general.volumeKeysInverted) Action.FORWARD else Action.BACKWARD
            }

            Key.DirectionRight -> if (event.isCtrlPressed) {
                Action.FORWARD
            } else {
                navigation.actionOf(
                    NavigationRegion.RIGHT,
                )
            }

            Key.DirectionLeft -> if (event.isCtrlPressed) {
                Action.BACKWARD
            } else {
                navigation.actionOf(
                    NavigationRegion.LEFT,
                )
            }

            Key.DirectionDown, Key.PageDown -> Action.FORWARD

            Key.DirectionUp, Key.PageUp -> Action.BACKWARD

            Key.Menu -> Action.MENU

            Key.N -> {
                if (isUp) loadNextChapter()
                return true
            }

            Key.P -> {
                if (isUp) loadPreviousChapter()
                return true
            }

            else -> return false
        }
        if (isUp) {
            when (action) {
                Action.MENU -> toggleMenu()
                Action.FORWARD -> viewer.moveForward()
                Action.BACKWARD -> viewer.moveBackward()
            }
        }
        return true
    }

    /**
     * Called from the viewer when it's tapped at [pos], given as a fraction of its size.
     */
    fun onViewerTap(pos: Offset) {
        val action = settingsViewModel.navigation.value?.getAction(pos) ?: Action.MENU
        when (action) {
            Action.MENU -> toggleMenu()
            Action.FORWARD -> viewer?.moveForward()
            Action.BACKWARD -> viewer?.moveBackward()
        }
    }

    @Composable
    private fun ReaderMenus() {
        val state by viewModel.state.collectAsState()
        val currentViewer by viewerFlow.collectAsState()

        val isHttpSource = state.source is HttpSource
        val isFullscreen by readerPreferences.fullscreen.collectAsState()
        val flashOnPageChange by readerPreferences.flashOnPageChange.collectAsState()

        val cropBorderPaged by readerPreferences.cropBorders.collectAsState()
        val cropBorderWebtoon by readerPreferences.cropBordersWebtoon.collectAsState()
        val isPagerType = ReadingMode.isPagerType(viewModel.getMangaReadingMode())
        val cropEnabled = if (isPagerType) cropBorderPaged else cropBorderWebtoon
        // SY -->
        val readerBottomButtons by readerPreferences.readerBottomButtons.changes().map { it.toImmutableSet() }
            .collectAsState(persistentSetOf())
        val dualPageSplitPaged by readerPreferences.dualPageSplitPaged.collectAsState()
        // SY <--
        val verticalNavigatorModes by readerPreferences.verticalNavigator.collectAsState()
        val verticalNavigator = verticalNavigatorModes.contains(
            ReadingMode.fromPreference(viewModel.getMangaReadingMode()),
        )
        val verticalNavigatorOnLeft by readerPreferences.verticalNavigatorOnLeft.collectAsState()
        val verticalNavigatorHeight by readerPreferences.verticalNavigatorHeight.collectAsState()

        ReaderAppBars(
            visible = state.menuVisible,
            fullscreen = isFullscreen,

            mangaTitle = state.manga?.title,
            chapterTitle = state.currentChapter?.chapter?.name,
            navigateUp = onBackPressedDispatcher::onBackPressed,
            onClickTopAppBar = ::openMangaScreen,
            bookmarked = state.bookmarked,
            onToggleBookmarked = viewModel::toggleChapterBookmark,
            onOpenInWebView = ::openChapterInWebView.takeIf { isHttpSource },
            onOpenInBrowser = ::openChapterInBrowser.takeIf { isHttpSource },
            onShare = ::shareChapter.takeIf { isHttpSource },

            chapterNavigatorType = if (!verticalNavigator) {
                if (currentViewer is R2LPagerViewer || (currentViewer as? WebGpuViewer)?.isReversed ?: false) {
                    ChapterNavigatorType.HORIZONTAL_RTL
                } else {
                    ChapterNavigatorType.HORIZONTAL_LTR
                }
            } else {
                if (verticalNavigatorOnLeft) {
                    ChapterNavigatorType.VERTICAL_LEFT
                } else {
                    ChapterNavigatorType.VERTICAL_RIGHT
                }
            },
            verticalNavigatorHeight = verticalNavigatorHeight / 100f,
            onNextChapter = ::loadNextChapter,
            enabledNext = state.viewerChapters?.nextChapter != null,
            onPreviousChapter = ::loadPreviousChapter,
            enabledPrevious = state.viewerChapters?.prevChapter != null,
            currentPage = state.currentPage,
            totalPages = state.totalPages,
            onPageIndexChange = {
                isScrollingThroughPages = true
                moveToPageIndex(it)
            },
            onPageIndexChangeFinished = {
                isScrollingThroughPages = false
            },

            readingMode = ReadingMode.fromPreference(
                viewModel.getMangaReadingMode(resolveDefault = false),
            ),
            onClickReadingMode = viewModel::openReadingModeSelectDialog,
            orientation = ReaderOrientation.fromPreference(
                viewModel.getMangaOrientation(resolveDefault = false),
            ),
            onClickOrientation = viewModel::openOrientationModeSelectDialog,
            cropEnabled = cropEnabled,
            onClickCropBorder = {
                val enabled = viewModel.toggleCropBorders()
                menuToggleToast?.cancel()
                menuToggleToast = toast(if (enabled) MR.strings.on else MR.strings.off)
            },
            onClickSettings = viewModel::openSettingsDialog,
            // SY -->
            isExhToolsVisible = state.ehUtilsVisible,
            onSetExhUtilsVisibility = viewModel::showEhUtils,
            isAutoScroll = state.autoScroll,
            isAutoScrollEnabled = state.isAutoScrollEnabled,
            onToggleAutoscroll = viewModel::toggleAutoScroll,
            autoScrollFrequency = state.ehAutoscrollFreq,
            onSetAutoScrollFrequency = viewModel::setAutoScrollFrequency,
            onClickAutoScrollHelp = viewModel::openAutoScrollHelpDialog,
            onClickRetryAll = ::exhRetryAll,
            onClickRetryAllHelp = viewModel::openRetryAllHelp,
            currentPageText = state.currentPageText,
            enabledButtons = readerBottomButtons,
            dualPageSplitEnabled = dualPageSplitPaged,
            doublePages = state.doublePages,
            onClickPageLayout = {
                if (readerPreferences.pageLayout.get() == PagerConfig.PageLayout.AUTOMATIC) {
                    (viewer as? PagerViewer)?.config?.let { config ->
                        config.doublePages = !config.doublePages
                        reloadChapters(config.doublePages, true)
                    }
                } else {
                    readerPreferences.pageLayout.set(1 - readerPreferences.pageLayout.get())
                }
            },
            onClickShiftPage = ::shiftDoublePages,
            // SY <--
        )

        if (flashOnPageChange) {
            DisplayRefreshHost(
                hostState = displayRefreshHost,
            )
        }

        val onDismissRequest = viewModel::closeDialog
        if (state.isLoadingAdjacentChapter) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(MR.strings.loading))
                    }
                },
            )
        }
        when (state.dialog) {
            is ReaderViewModel.Dialog.Settings -> {
                ReaderSettingsDialog(
                    onDismissRequest = onDismissRequest,
                    onShowMenus = { setMenuVisibility(true) },
                    onHideMenus = { setMenuVisibility(false) },
                    viewModel = settingsViewModel,
                )
            }

            is ReaderViewModel.Dialog.ReadingModeSelect -> {
                ReadingModeSelectDialog(
                    onDismissRequest = onDismissRequest,
                    viewModel = settingsViewModel,
                    onChange = { stringRes ->
                        menuToggleToast?.cancel()
                        if (!readerPreferences.showReadingMode.get()) {
                            menuToggleToast = toast(stringRes)
                        }
                    },
                )
            }

            is ReaderViewModel.Dialog.OrientationModeSelect -> {
                OrientationSelectDialog(
                    onDismissRequest = onDismissRequest,
                    viewModel = settingsViewModel,
                    onChange = { stringRes ->
                        menuToggleToast?.cancel()
                        menuToggleToast = toast(stringRes)
                    },
                )
            }

            is ReaderViewModel.Dialog.PageActions -> {
                ReaderPageActionsDialog(
                    onDismissRequest = onDismissRequest,
                    onSetAsCover = viewModel::setAsCover,
                    onShare = viewModel::shareImage,
                    onSave = viewModel::saveImage,
                    onShareCombined = ::shareCombinedImages,
                    onSaveCombined = ::saveCombinedImages,
                    hasExtraPage = (state.dialog as? ReaderViewModel.Dialog.PageActions)?.extraPage != null,
                )
            }

            // SY -->
            ReaderViewModel.Dialog.AutoScrollHelp -> AlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
                title = { Text(text = stringResource(TLMR.strings.eh_autoscroll_help)) },
                text = { Text(text = stringResource(TLMR.strings.eh_autoscroll_help_message)) },
            )

            ReaderViewModel.Dialog.BoostPageHelp -> AlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
                title = { Text(text = stringResource(TLMR.strings.eh_boost_page_help)) },
                text = { Text(text = stringResource(TLMR.strings.eh_boost_page_help_message)) },
            )

            ReaderViewModel.Dialog.RetryAllHelp -> AlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
                title = { Text(text = stringResource(TLMR.strings.eh_retry_all_help)) },
                text = { Text(text = stringResource(TLMR.strings.eh_retry_all_help_message)) },
            )

            // SY <--

            null -> {}
        }
    }

    /**
     * Sets the visibility of the menu according to [visible].
     */
    private fun setMenuVisibility(visible: Boolean) {
        viewModel.showMenus(visible)
        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        } else {
            if (readerPreferences.fullscreen.get()) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun enableExhAutoScroll() {
        readerPreferences.autoscrollInterval.changes()
            .combine(viewModel.state.map { it.autoScroll }.distinctUntilChanged()) { interval, enabled ->
                interval.toDouble() to enabled
            }.mapLatest { (intervalFloat, enabled) ->
                if (enabled) {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        val interval = intervalFloat.seconds
                        while (true) {
                            if (!viewModel.state.value.menuVisible) {
                                viewer.let { v ->
                                    when (v) {
                                        is PagerViewer -> v.moveForward()

                                        is WebtoonViewer -> {
                                            if (readerPreferences.smoothAutoScroll.get()) {
                                                v.linearScroll(interval)
                                            } else {
                                                v.moveForward()
                                            }
                                        }
                                    }
                                }
                                delay(interval)
                            } else {
                                delay(100)
                            }
                        }
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun exhRetryAll() {
        var retried = 0

        viewModel.state.value.viewerChapters
            ?.currChapter
            ?.pages
            ?.forEachIndexed { _, page ->
                var shouldQueuePage = false
                if (page.status == Page.State.ERROR) {
                    shouldQueuePage = true
                }
                /*else if (page.status == Page.LOAD_PAGE ||
                   page.status == Page.DOWNLOAD_IMAGE) {
                 // Do nothing
              }*/

                if (shouldQueuePage) {
                    page.status = Page.State.QUEUE
                } else {
                    return@forEachIndexed
                }

                retried++
            }

        toast(pluralStringResource(TLMR.plurals.eh_retry_toast, retried, retried))
    }

    // SY -->

    fun reloadChapters(doublePages: Boolean, force: Boolean = false) {
        val viewer = viewer as? PagerViewer ?: return
        viewer.updateShifting()
        if (!force && viewer.config.autoDoublePages) {
            setDoublePageMode(viewer)
        } else {
            viewer.config.doublePages = doublePages
            viewModel.setDoublePages(viewer.config.doublePages)
        }
        val currentChapter = viewModel.state.value.currentChapter
        if (doublePages) {
            // If we're moving from singe to double, we want the current page to be the first page
            val currentPage = viewModel.state.value.currentPage
            viewer.config.shiftDoublePage = (
                currentPage + (currentChapter?.pages?.take(currentPage)?.count { it.fullPage || it.isolatedPage } ?: 0)
                ) % 2 != 0
        }
        viewModel.state.value.viewerChapters?.let {
            viewer.setChaptersDoubleShift(it)
        }
        // AM (DISCORD) -->
        updateDiscordRPC(exitingReader = false)
        // <-- AM (DISCORD)
    }

    private fun setDoublePageMode(viewer: PagerViewer) {
        val currentOrientation = resources.configuration.orientation
        viewer.config.doublePages = currentOrientation == Configuration.ORIENTATION_LANDSCAPE
        viewModel.setDoublePages(viewer.config.doublePages)
    }

    private fun shiftDoublePages() {
        val viewer = viewer as? PagerViewer ?: return
        viewer.config.let { config ->
            config.shiftDoublePage = !config.shiftDoublePage
            viewModel.state.value.viewerChapters?.let {
                viewer.updateShifting()
                viewer.setChaptersDoubleShift(it)
                invalidateOptionsMenu()
            }
        }
    }

    private fun saveCombinedImages() {
        val viewer = viewer as? PagerViewer ?: return
        val isLTR = (viewer !is R2LPagerViewer) xor viewer.config.invertDoublePages
        viewModel.saveImages(isLTR, viewer.config.pageCanvasColor)
    }

    private fun shareCombinedImages(copyToClipboard: Boolean) {
        val viewer = viewer as? PagerViewer ?: return
        val isLTR = (viewer !is R2LPagerViewer) xor viewer.config.invertDoublePages
        viewModel.shareImages(copyToClipboard, isLTR, viewer.config.pageCanvasColor)
    }

    // SY <--

    /**
     * Called from the presenter when a manga is ready. Used to instantiate the appropriate viewer.
     */
    private fun updateViewer() {
        val prevViewer = viewer
        val readingMode = viewModel.getMangaReadingMode()
        settingsViewModel.setViewerReadingMode(ReadingMode.fromPreference(readingMode))
        val newViewer = ReadingMode.toViewer(readingMode, this)

        setOrientation(viewModel.getMangaOrientation())

        // Destroy previous viewer if there was one
        prevViewer?.destroy()
        viewerFlow.value = newViewer

        // SY -->
        if (newViewer is PagerViewer) {
            if (readerPreferences.pageLayout.get() == PagerConfig.PageLayout.AUTOMATIC) {
                setDoublePageMode(newViewer)
            }
            viewModel.state.value.lastShiftDoubleState?.let { newViewer.config.shiftDoublePage = it }
        }

        if (readerPreferences.showReadingMode.get()) {
            // SY <--
            showReadingModeToast(viewModel.getMangaReadingMode())
        }

        isLoadingViewer = true
        // AM (DISCORD) -->
        updateDiscordRPC(exitingReader = false)
        // <-- AM (DISCORD)
    }

    private fun openMangaScreen() {
        viewModel.manga?.id?.let { id ->
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = Constants.SHORTCUT_MANGA
                    putExtra(Constants.MANGA_EXTRA, id)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }
    }

    private fun openChapterInWebView() {
        val manga = viewModel.manga ?: return
        val source = viewModel.getSource() ?: return
        assistUrl?.let {
            val intent = WebViewActivity.newIntent(this@ReaderActivity, it, source.id, manga.title)
            startActivity(intent)
        }
    }

    private fun openChapterInBrowser() {
        assistUrl?.let {
            openInBrowser(it.toUri(), forceDefaultBrowser = false)
        }
    }

    private fun shareChapter() {
        assistUrl?.let {
            val intent = it.toUri().toShareIntent(this, type = "text/plain")
            startActivity(intent)
        }
    }

    private fun showReadingModeToast(mode: Int) {
        try {
            readingModeToast?.cancel()
            readingModeToast = toast(ReadingMode.fromPreference(mode).stringRes)
        } catch (e: ArrayIndexOutOfBoundsException) {
            logcat(LogPriority.ERROR) { "Unknown reading mode: $mode" }
        }
    }

    /**
     * Called from the presenter whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the toolbar, and
     * hides or disables the reader prev/next buttons if there's a prev or next chapter
     */
    private fun setChapters(viewerChapters: ViewerChapters) {
        isLoadingViewer = false
        // SY -->
        val state = viewModel.state.value
        if (state.indexChapterToShift != null && state.indexPageToShift != null) {
            viewerChapters.currChapter.pages?.find {
                it.index == state.indexPageToShift && it.chapter.chapter.id == state.indexChapterToShift
            }?.let {
                (viewer as? PagerViewer)?.updateShifting(it)
            }
            viewModel.setIndexChapterToShift(null)
            viewModel.setIndexPageToShift(null)
        } else if (state.lastShiftDoubleState != null) {
            val currentChapter = viewerChapters.currChapter
            (viewer as? PagerViewer)?.config?.shiftDoublePage = (
                currentChapter.requestedPage +
                    (
                        currentChapter.pages?.take(currentChapter.requestedPage)
                            ?.count { it.fullPage || it.isolatedPage } ?: 0
                        )
                ) % 2 != 0
        }
        // SY <--

        viewer?.setChapters(viewerChapters)

        lifecycleScope.launchIO {
            viewModel.getChapterUrl()?.let { url ->
                assistUrl = url
            }
        }
        // AM (DISCORD) -->
        updateDiscordRPC(exitingReader = false)
        // <-- AM (DISCORD)
    }

    /**
     * Called from the presenter if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialChapterError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
        finish()
        toast(error.message)
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    private fun moveToPageIndex(index: Int) {
        val viewer = this.viewer ?: return
        val currentChapter = viewModel.state.value.currentChapter ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page)
    }

    /**
     * Tells the presenter to load the next chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadNextChapter() {
        lifecycleScope.launch {
            viewModel.loadNextChapter()
            moveToPageIndex(0)
            // AM (DISCORD) -->
            updateDiscordRPC(exitingReader = false)
            // <-- AM (DISCORD)
        }
    }

    /**
     * Tells the presenter to load the previous chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadPreviousChapter() {
        lifecycleScope.launch {
            viewModel.loadPreviousChapter()
            moveToPageIndex(0)
            // AM (DISCORD) -->
            updateDiscordRPC(exitingReader = false)
            // <-- AM (DISCORD)
        }
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the presenter.
     */
    @SuppressLint("SetTextI18n")
    fun onPageSelected(page: ReaderPage, hasExtraPage: Boolean = false) {
        // SY -->
        val currentPageText = if (hasExtraPage) {
            val invertDoublePage = (viewer as? PagerViewer)?.config?.invertDoublePages ?: false
            if ((resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) xor invertDoublePage) {
                "${page.number}-${page.number + 1}"
            } else {
                "${page.number + 1}-${page.number}"
            }
        } else {
            "${page.number}"
        }
        viewModel.onPageSelected(page, currentPageText, hasExtraPage)
        // SY <--

        // AM (DISCORD) -->
        updateDiscordRPC(exitingReader = false)
        // <-- AM (DISCORD)
    }

    /**
     * Called from the viewer whenever a [page] is long clicked. A bottom sheet with a list of
     * actions to perform is shown.
     */
    fun onPageLongTap(page: ReaderPage, extraPage: ReaderPage? = null) {
        // SY -->
        viewModel.openPageDialog(page, extraPage)
        // SY <--
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        lifecycleScope.launchIO { viewModel.preload(chapter) }
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     */
    fun toggleMenu() {
        setMenuVisibility(!viewModel.state.value.menuVisible)
    }

    /**
     * Called from the viewer to show the menu.
     */
    fun showMenu() {
        if (!viewModel.state.value.menuVisible) {
            setMenuVisibility(true)
        }
    }

    /**
     * Called from the viewer to hide the menu.
     */
    fun hideMenu() {
        if (viewModel.state.value.menuVisible) {
            setMenuVisibility(false)
        }
    }

    /**
     * Called from the presenter when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    fun onShareImageResult(uri: Uri, page: ReaderPage, secondPage: ReaderPage? = null) {
        val manga = viewModel.manga ?: return
        val chapter = page.chapter.chapter

        // SY -->
        val text = if (secondPage != null) {
            stringResource(
                TLMR.strings.share_pages_info,
                manga.title,
                chapter.name,
                if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
                    "${page.number}-${page.number + 1}"
                } else {
                    "${page.number + 1}-${page.number}"
                },
            )
        } else {
            stringResource(MR.strings.share_page_info, manga.title, chapter.name, page.number)
        }
        // SY <--

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = text,
        )
        startActivity(intent)
    }

    private fun onCopyImageResult(uri: Uri) {
        val clipboardManager = applicationContext.getSystemService<ClipboardManager>() ?: return
        val clipData = ClipData.newUri(applicationContext.contentResolver, "", uri)
        clipboardManager.setPrimaryClip(clipData)
    }

    /**
     * Called from the presenter when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    private fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }

            is ReaderViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    private fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> MR.strings.cover_updated
                AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    private fun setOrientation(orientation: Int) {
        val newOrientation = ReaderOrientation.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
    }

    private fun applyGeneralSettings(old: GeneralSettings?, new: GeneralSettings) {
        if (old?.fullscreen != new.fullscreen || old?.drawUnderCutout != new.drawUnderCutout) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = if (new.drawUnderCutout) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                    }
                }
            }
        }
    }

    // AM (DISCORD) -->
    private fun updateDiscordRPC(exitingReader: Boolean) {
        if (connectionsPreferences.enableDiscordRPC().get()) {
            viewModel.viewModelScope.launchIO {
                if (!exitingReader) {
                    DiscordRPCService.setReaderActivity(
                        context = this@ReaderActivity,
                        ReaderData(
                            incognitoMode = viewModel.currentSource?.isNsfw() == true || viewModel.incognitoMode,
                            mangaId = viewModel.manga?.id,
                            // AM (CU)>
                            mangaTitle = viewModel.manga?.ogTitle,
                            thumbnailUrl = viewModel.manga?.thumbnailUrl,
                            chapterProgress = Pair(viewModel.state.value.currentPage, viewModel.state.value.totalPages),
                            chapterNumber =
                            if (connectionsPreferences.useChapterTitles().get()) {
                                viewModel.state.value.currentChapter?.chapter?.name
                            } else {
                                viewModel.state.value.currentChapter?.chapter?.chapter_number.toString()
                            },
                        ),
                    )
                } else {
                    val lastUsedScreen = DiscordRPCService.lastUsedScreen
                    DiscordRPCService.setMangaScreen(this@ReaderActivity, lastUsedScreen)
                }
            }
        }
    }

    // <-- AM (DISCORD)

    /**
     * Sets the brightness of the screen. Range is [-75, 100].
     * From -75 to -1 the minimum brightness is used, and the overlay darkens further.
     * From 1 to 100 it sets that value as brightness.
     * 0 sets system brightness.
     */
    private fun setWindowBrightness(value: Int) {
        // Calculate and set reader brightness.
        val readerBrightness = when {
            value > 0 -> {
                value / 100f
            }

            value < 0 -> {
                0.01f
            }

            else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = window.attributes.apply { screenBrightness = readerBrightness }
    }
}

private fun viewerColorFilter(grayscale: Boolean, invertedColors: Boolean): ColorFilter? {
    if (!grayscale && !invertedColors) return null
    val matrix = ColorMatrix()
    if (invertedColors) {
        matrix *= ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
    if (grayscale) {
        matrix *= ColorMatrix().apply { setToSaturation(0f) }
    }
    return ColorFilter.colorMatrix(matrix)
}
