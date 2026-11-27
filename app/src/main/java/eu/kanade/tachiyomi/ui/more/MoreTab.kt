package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.currentNavigationStyle
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.openSettings
import eu.kanade.presentation.more.settings.screen.player.openPlayerSettings
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.home.TopLevelRoute
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import mihon.core.navigation.AboutRoute
import mihon.core.navigation.CategoryRoute
import mihon.core.navigation.DownloadQueueRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.NetworkStreamRoute
import mihon.core.navigation.StatsRoute
import mihon.core.navigation.StorageRoute
import mihon.core.navigation.SupportUsRoute
import mihon.presentation.core.util.isTabletUi
import tachiyomi.core.common.util.lang.launchIO

@Composable
fun MoreTab() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = metroViewModel<MoreViewModel>()
    val downloadQueueState by viewModel.downloadQueueState.collectAsState()
    val navStyle = currentNavigationStyle()
    val twoPane = isTabletUi()

    MoreScreen(
        downloadQueueStateProvider = { downloadQueueState },
        downloadedOnly = viewModel.downloadedOnly,
        onDownloadedOnlyChange = { viewModel.downloadedOnly = it },
        incognitoMode = viewModel.incognitoMode,
        onIncognitoModeChange = { viewModel.incognitoMode = it },
        // SY -->
        showNavUpdates = viewModel.showNavUpdates,
        showNavHistory = viewModel.showNavHistory,
        // SY <--
        navStyle = navStyle,
        onClickAlt = { navigator.push(navStyle.moreTab) },
        onClickDownloadQueue = { navigator.push(DownloadQueueRoute) },
        onClickCategories = { navigator.push(CategoryRoute) },
        onClickStats = { navigator.push(StatsRoute) },
        onClickNetworkStream = { navigator.push(NetworkStreamRoute) },
        onClickStorage = { navigator.push(StorageRoute) },
        onClickDataAndStorage = { navigator.openSettings(SettingsDataScreen, twoPane = twoPane) },
        onClickPlayerSettings = { navigator.openPlayerSettings(mainSettings = false, twoPane = twoPane) },
        onClickSettings = { navigator.openSettings(twoPane = twoPane) },
        onClickSupport = { navigator.push(SupportUsRoute) },
        onClickAbout = { navigator.openSettings(AboutRoute, twoPane = twoPane) },
    )

    ResultEffect<Unit>(resultKey = TopLevelRoute.More.reselectEventKey) {
        navigator.openSettings(twoPane = twoPane)
    }

    LaunchedEffect(Unit) {
        (context as? MainActivity)?.ready = true
        // AM (DISCORD) -->
        DiscordRPCService.setScreen(context, DiscordScreen.MORE)
        // <-- AM (DISCORD)
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class MoreViewModel(
    private val downloadManager: MangaDownloadManager,
    private val animeDownloadManager: AnimeDownloadManager,
    preferences: BasePreferences,
    // SY -->
    uiPreferences: UiPreferences,
    // SY <--
) : ViewModel() {

    var downloadedOnly by preferences.downloadedOnly.asState(viewModelScope)
    var incognitoMode by preferences.incognitoMode.asState(viewModelScope)

    // SY -->
    val showNavUpdates by uiPreferences.showNavUpdates.asState(viewModelScope)
    val showNavHistory by uiPreferences.showNavHistory.asState(viewModelScope)
    // SY <--

    private var _downloadQueueState: MutableStateFlow<DownloadQueueState> = MutableStateFlow(
        DownloadQueueState.Stopped,
    )
    val downloadQueueState: StateFlow<DownloadQueueState> = _downloadQueueState.asStateFlow()

    init {
        // Handle running/paused status change and queue progress updating
        viewModelScope.launchIO {
            combine(
                downloadManager.isDownloaderRunning,
                downloadManager.queueState,
            ) { isRunningManga, mangaDownloadQueue -> Pair(isRunningManga, mangaDownloadQueue.size) }
                .collectLatest { (isDownloadingManga, mangaDownloadQueueSize) ->
                    combine(
                        animeDownloadManager.isDownloaderRunning,
                        animeDownloadManager.queueState,
                    ) { isRunningAnime, animeDownloadQueue ->
                        Pair(
                            isRunningAnime,
                            animeDownloadQueue.size,
                        )
                    }
                        .collectLatest { (isDownloadingAnime, animeDownloadQueueSize) ->
                            val isDownloading = isDownloadingAnime || isDownloadingManga
                            val downloadQueueSize = mangaDownloadQueueSize + animeDownloadQueueSize
                            val pendingDownloadExists = downloadQueueSize != 0
                            _downloadQueueState.value = when {
                                !pendingDownloadExists -> DownloadQueueState.Stopped
                                !isDownloading -> DownloadQueueState.Paused(downloadQueueSize)
                                else -> DownloadQueueState.Downloading(downloadQueueSize)
                            }
                        }
                }
        }
    }
}

sealed interface DownloadQueueState {
    data object Stopped : DownloadQueueState
    data class Paused(val pending: Int) : DownloadQueueState
    data class Downloading(val pending: Int) : DownloadQueueState
}
