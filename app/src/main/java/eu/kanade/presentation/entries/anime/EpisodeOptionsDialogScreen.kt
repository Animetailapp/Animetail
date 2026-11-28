package eu.kanade.presentation.entries.anime

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Input
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.common.images.WebImage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.cast.CastProgressSync
import eu.kanade.tachiyomi.ui.player.cast.CastVideoUrl
import eu.kanade.tachiyomi.ui.player.cast.ExpandedControlsActivity
import eu.kanade.tachiyomi.ui.player.cast.castWhenConnected
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.QualitySheetHosterContent
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.QualitySheetVideoContent
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.getChangedAt
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun EpisodeOptionsDialogScreen(
    useExternalDownloader: Boolean,
    episodeTitle: String,
    episodeId: Long,
    animeId: Long,
    sourceId: Long,
    onDismissRequest: () -> Unit,
) {
    val sm = assistedMetroViewModel<EpisodeOptionsDialogScreenModel, EpisodeOptionsDialogScreenModel.Factory> {
        create(
            episodeId = episodeId,
            animeId = animeId,
            sourceId = sourceId,
        )
    }

    val episode by sm.episode.collectAsState()
    val anime by sm.anime.collectAsState()
    val hosterState by sm.hosterState.collectAsState()
    val hosterExpandedList by sm.hosterExpandedList.collectAsState()
    val selectedHosterVideoIndex by sm.selectedHosterVideoIndex.collectAsState()
    val currentVideo by sm.currentVideo.collectAsState()
    val showAllQualities by sm.showAllQualities.collectAsState()

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        EpisodeOptionsDialog(
            useExternalDownloader = useExternalDownloader,
            episodeTitle = episodeTitle,
            episode = episode,
            anime = anime,
            showAllQualities = showAllQualities,
            resultList = hosterState,
            expandedList = hosterExpandedList,
            currentVideo = currentVideo,
            selectedHosterVideoIndex = selectedHosterVideoIndex,
            onShowAllQualities = sm::onShowAllQualities,
            onClickHoster = sm::onClickHoster,
            onClickVideo = sm::onClickVideo,
            getHosterList = sm::getHosterList,
            onDismissRequest = onDismissRequest,
        )
    }
}

@AssistedInject
class EpisodeOptionsDialogScreenModel(
    private val context: Context,
    @Assisted episodeId: Long,
    @Assisted animeId: Long,
    @Assisted sourceId: Long,
    private val sourceManager: AnimeSourceManager,
    getEpisode: GetEpisode,
    getAnime: GetAnime,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(
            episodeId: Long,
            animeId: Long,
            sourceId: Long,
        ): EpisodeOptionsDialogScreenModel
    }

    private val _hosterState = MutableStateFlow<Result<List<HosterState>>?>(null)
    val hosterState = _hosterState.asStateFlow()
    private val _hosterExpandedList = MutableStateFlow<List<Boolean>>(emptyList())
    val hosterExpandedList = _hosterExpandedList.asStateFlow()
    private val _selectedHosterVideoIndex = MutableStateFlow(Pair(-1, -1))
    val selectedHosterVideoIndex = _selectedHosterVideoIndex.asStateFlow()
    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo = _currentVideo.asStateFlow()

    private val _episode = MutableStateFlow<Episode?>(null)
    val episode = _episode.asStateFlow()
    private val _anime = MutableStateFlow<Anime?>(null)
    val anime = _anime.asStateFlow()

    @Suppress("ktlint:standard:backing-property-naming")
    private val _hosterList = MutableStateFlow<List<Hoster>>(emptyList())

    @Suppress("ktlint:standard:backing-property-naming")
    private val _source = MutableStateFlow<AnimeSource?>(null)

    private val _showAllQualities = MutableStateFlow(false)
    val showAllQualities = _showAllQualities.asStateFlow()

    init {
        val hasFoundPreferredVideo = AtomicBoolean(false)

        viewModelScope.launchIO {
            val episode = getEpisode.await(episodeId)!!
            val anime = getAnime.await(animeId)!!
            val source = sourceManager.getOrStub(sourceId)

            _episode.update { _ -> episode }
            _anime.update { _ -> anime }
            _source.update { _ -> source }

            val hosterListResult = withIOContext {
                try {
                    Result.success(EpisodeLoader.getHosters(context, episode, anime, source))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            if (hosterListResult.isFailure) {
                _hosterState.update { _ -> Result.failure(hosterListResult.exceptionOrNull()!!) }
                return@launchIO
            }

            val hosterList = hosterListResult.getOrThrow()
            _hosterList.update { _ -> hosterList }
            _hosterExpandedList.update { _ ->
                List(hosterList.size) { true }
            }

            val initialHosterState = hosterList.map { hoster ->
                if (hoster.videoList == null) {
                    HosterState.Loading(hoster.hosterName)
                } else {
                    val videoList = hoster.videoList!!
                    HosterState.Ready(
                        hoster.hosterName,
                        videoList,
                        List(videoList.size) { Video.State.LOAD_VIDEO },
                    )
                }
            }

            _hosterState.update { _ -> Result.success(initialHosterState) }

            try {
                hosterList.mapIndexed { hosterIdx, hoster ->
                    async {
                        val hosterState = EpisodeLoader.loadHosterVideos(context, source, hoster)

                        _hosterState.updateAt(hosterIdx, hosterState)

                        if (hosterState is HosterState.Ready) {
                            val prefIndex = hosterState.videoList.indexOfFirst { it.preferred }
                            if (prefIndex != -1) {
                                if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                                    val success =
                                        loadVideo(source, hosterState.videoList[prefIndex], hosterIdx, prefIndex)
                                    if (!success) {
                                        hasFoundPreferredVideo.set(false)
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()

                if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                    if (selectedHosterVideoIndex.value == Pair(-1, -1)) {
                        val hosterStateList = hosterState.value!!.getOrThrow()
                        val (hosterIdx, videoIdx) = HosterLoader.selectBestVideo(hosterStateList)
                        if (hosterIdx == -1) {
                            _hosterState.update { _ ->
                                Result.failure(NoSuchElementException("No available videos"))
                            }
                            return@launchIO
                        }

                        val video = (hosterStateList[hosterIdx] as HosterState.Ready).videoList[videoIdx]

                        loadVideo(source, video, hosterIdx, videoIdx)
                    }
                }
            } catch (e: CancellationException) {
                _hosterState.update { _ ->
                    Result.success(hosterList.map { HosterState.Idle(it.hosterName) })
                }

                throw e
            }
        }
    }

    private suspend fun loadVideo(source: AnimeSource, video: Video, hosterIndex: Int, videoIndex: Int): Boolean {
        val selectedHosterState = (_hosterState.value!!.getOrThrow()[hosterIndex] as? HosterState.Ready) ?: return false

        val oldSelectedIndex = _selectedHosterVideoIndex.value
        _selectedHosterVideoIndex.update { _ -> Pair(hosterIndex, videoIndex) }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, video, Video.State.LOAD_VIDEO),
        )

        val resolvedVideo = if (selectedHosterState.videoState[videoIndex] != Video.State.READY) {
            HosterLoader.getResolvedVideo(source, video)
        } else {
            video
        }

        if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
            if (currentVideo.value == null) {
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR),
                )

                val hosterStateList = hosterState.value?.getOrNull() ?: return false

                val (newHosterIdx, newVideoIdx) = HosterLoader.selectBestVideo(hosterStateList)
                if (newHosterIdx == -1) {
                    _hosterState.update { _ ->
                        Result.failure(NoSuchElementException("No available videos"))
                    }
                    return false
                }

                val newVideo = (hosterStateList[newHosterIdx] as HosterState.Ready).videoList[newVideoIdx]

                return loadVideo(source, newVideo, newHosterIdx, newVideoIdx)
            } else {
                _selectedHosterVideoIndex.update { _ -> oldSelectedIndex }
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR),
                )
                return false
            }
        }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, resolvedVideo, Video.State.READY),
        )
        _currentVideo.update { _ -> resolvedVideo }

        return true
    }

    private fun <T> MutableStateFlow<Result<List<T>>?>.updateAt(index: Int, newValue: T) {
        this.update { values ->
            values?.getOrNull()?.let {
                Result.success(
                    it.toMutableList().apply {
                        this[index] = newValue
                    },
                )
            } ?: values
        }
    }

    fun onShowAllQualities(value: Boolean) {
        _showAllQualities.update { _ -> value }
    }

    fun onClickHoster(hosterIndex: Int) {
        val hosterState = hosterState.value?.getOrNull()?.getOrNull(hosterIndex) ?: return

        when {
            hosterState.canReload -> {
                val hosterName = hosterState.name
                _hosterState.updateAt(hosterIndex, HosterState.Loading(hosterName))

                viewModelScope.launchIO {
                    val newHosterState = EpisodeLoader.loadHosterVideos(
                        context,
                        _source.value!!,
                        _hosterList.value[hosterIndex],
                        force = true,
                    )
                    _hosterState.updateAt(hosterIndex, newHosterState)
                }
            }

            hosterState is HosterState.Ready -> {
                _hosterExpandedList.update { values ->
                    values.toMutableList().apply {
                        this[hosterIndex] = !hosterExpandedList.value[hosterIndex]
                    }
                }
            }
        }
    }

    fun onClickVideo(hosterIndex: Int, videoIndex: Int) {
        val video = (_hosterState.value?.getOrNull()?.getOrNull(hosterIndex) as? HosterState.Ready)
            ?.videoList
            ?.getOrNull(videoIndex)
            ?: return

        viewModelScope.launchIO {
            val success = loadVideo(_source.value!!, video, hosterIndex, videoIndex)
            if (success) {
                _showAllQualities.update { _ -> false }
            }
        }
    }

    fun getHosterList(): List<Hoster>? {
        val hosterStateList = hosterState.value?.getOrNull() ?: return null
        return _hosterList.value.mapIndexed { index, h ->
            if (hosterStateList[index] is HosterState.Ready) {
                Hoster(
                    hosterName = h.hosterName,
                    hosterUrl = h.hosterUrl,
                    videoList = (hosterStateList[index] as HosterState.Ready).videoList,
                    internalData = h.internalData,
                    memo = h.memo,
                )
            } else {
                h
            }
        }
    }
}

@Composable
fun EpisodeOptionsDialog(
    useExternalDownloader: Boolean,
    episodeTitle: String,
    episode: Episode?,
    anime: Anime?,
    showAllQualities: Boolean,
    resultList: Result<List<HosterState>>? = null,
    expandedList: List<Boolean>,
    currentVideo: Video?,
    selectedHosterVideoIndex: Pair<Int, Int>,
    onShowAllQualities: (Boolean) -> Unit,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    getHosterList: () -> List<Hoster>?,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = TabbedDialogPaddings.Vertical)
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = episodeTitle,
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            text = stringResource(AYMR.strings.choose_video_quality),
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyMedium,
        )

        val onError: () -> Unit = {
            logcat(LogPriority.ERROR) { "Error getting links" }
            scope.launchUI { context.toast("No available videos") }
            onDismissRequest()
        }
        if (resultList?.isFailure == true) {
            onError()
        }

        if (resultList == null || episode == null || anime == null || currentVideo == null) {
            LoadingScreen()
        } else {
            val hosterStateList = resultList.getOrNull()
            if (!hosterStateList.isNullOrEmpty()) {
                VideoList(
                    useExternalDownloader = useExternalDownloader,
                    episode = episode,
                    anime = anime,
                    showAllQualities = showAllQualities,
                    hosterStateList = hosterStateList,
                    expandedList = expandedList,
                    currentVideo = currentVideo,
                    selectedHosterVideoIndex = selectedHosterVideoIndex,
                    onShowAllQualities = onShowAllQualities,
                    onClickHoster = onClickHoster,
                    onClickVideo = onClickVideo,
                    getHosterList = getHosterList,
                    onDismissRequest = onDismissRequest,
                )
            } else {
                onError()
            }
        }
    }
}

@Composable
private fun VideoList(
    useExternalDownloader: Boolean,
    episode: Episode,
    anime: Anime,
    showAllQualities: Boolean,
    hosterStateList: List<HosterState>,
    expandedList: List<Boolean>,
    currentVideo: Video,
    selectedHosterVideoIndex: Pair<Int, Int>,
    onShowAllQualities: (Boolean) -> Unit,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    getHosterList: () -> List<Hoster>?,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val downloadManager = context.appGraph.animeDownloadManager
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedString = stringResource(AYMR.strings.copied_video_link_to_clipboard)
    // Same as the player's qualities sheet, so both list the same hosters.
    val playerPreferences = context.appGraph.playerPreferences
    val showFailedHosters by playerPreferences.showFailedHosters().collectAsState()
    val showEmptyHosters by playerPreferences.showEmptyHosters().collectAsState()

    AnimatedVisibility(
        visible = !showAllQualities,
        enter = slideInHorizontally(),
        exit = slideOutHorizontally(),
    ) {
        Column {
            if (currentVideo.videoUrl.isNotEmpty() && !showAllQualities) {
                ClickableRow(
                    text = currentVideo.videoTitle,
                    icon = null,
                    onClick = { onShowAllQualities(true) },
                    showDropdownArrow = true,
                )

                val downloadEpisode: (Boolean) -> Unit = {
                    downloadManager.downloadEpisodes(
                        anime,
                        listOf(episode),
                        true,
                        it,
                        currentVideo,
                    )
                }

                QualityOptions(
                    onDownloadClicked = { downloadEpisode(useExternalDownloader) },
                    onExtDownloadClicked = { downloadEpisode(!useExternalDownloader) },
                    onCopyClicked = {
                        scope.launch {
                            var videoUrl = currentVideo.videoUrl
                            if (currentVideo.usesHttpServer()) {
                                val (success, port) = MainActivity.startHttpServerService(context, anime.source)
                                if (success) {
                                    videoUrl = currentVideo.copyHttpServer(port).videoUrl
                                }
                            }
                            val clipEntry = ClipData.newPlainText(
                                videoUrl,
                                videoUrl,
                            ).toClipEntry()
                            clipboard.setClipEntry(clipEntry)
                            context.toast(copiedString)
                        }
                    },
                    onExtPlayerClicked = {
                        scope.launch {
                            MainActivity.startPlayerActivity(
                                context,
                                anime.id,
                                episode.id,
                                true,
                                anime.source,
                                currentVideo,
                            )
                        }
                    },
                    onIntPlayerClicked = {
                        scope.launch {
                            MainActivity.startPlayerActivity(
                                context,
                                anime.id,
                                episode.id,
                                false,
                                anime.source,
                                currentVideo,
                                selectedHosterVideoIndex.first,
                                selectedHosterVideoIndex.second,
                                getHosterList(),
                            )
                        }
                    },
                    // start tail cast
                    onCastClicked = {
                        scope.launch {
                            if (context.appGraph.playerPreferences.enableCast().get()) {
                                val sourceHeaders =
                                    (context.appGraph.animeSourceManager.get(anime.source) as? AnimeHttpSource)?.headers
                                val videoUrl =
                                    withIOContext { CastVideoUrl.proxied(context, currentVideo, sourceHeaders) }
                                        ?: currentVideo.videoUrl
                                castWhenConnected(context) { session ->
                                    sendChaptersToCast(
                                        context,
                                        session,
                                        episode.id,
                                        anime.title,
                                        episode.name,
                                        episode.lastSecondSeen,
                                        anime.thumbnailUrl.orEmpty(),
                                        videoUrl,
                                    )
                                }
                            } else {
                                context.toast("Cast is disabled")
                            }
                        }
                    },
                    // end tail cast
                    onDismissRequest = onDismissRequest,
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showAllQualities,
        enter = slideInHorizontally(initialOffsetX = { it / 2 }),
        exit = slideOutHorizontally(targetOffsetX = { it / 2 }),
    ) {
        if (showAllQualities) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TabbedDialogPaddings.Horizontal)
                    .heightIn(max = 600.dp),
            ) {
                if (
                    hosterStateList.size == 1 &&
                    hosterStateList.first().name == Hoster.NO_HOSTER_LIST &&
                    hosterStateList.first() is HosterState.Ready
                ) {
                    QualitySheetVideoContent(
                        videoList = (hosterStateList.first() as HosterState.Ready).videoList,
                        videoState = (hosterStateList.first() as HosterState.Ready).videoState,
                        selectedVideoIndex = selectedHosterVideoIndex.second,
                        onClickVideo = onClickVideo,
                    )
                } else {
                    QualitySheetHosterContent(
                        hosterState = hosterStateList,
                        expandedState = expandedList,
                        selectedVideoIndex = selectedHosterVideoIndex,
                        onClickHoster = onClickHoster,
                        onClickVideo = onClickVideo,
                        displayHosters = Pair(showFailedHosters, showEmptyHosters),
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityOptions(
    onDownloadClicked: () -> Unit = {},
    onExtDownloadClicked: () -> Unit = {},
    onCopyClicked: () -> Unit = {},
    onExtPlayerClicked: () -> Unit = {},
    onIntPlayerClicked: () -> Unit = {},
    onCastClicked: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
) {
    val closeMenu = onDismissRequest

    Column {
        ClickableRow(
            text = stringResource(MR.strings.copy),
            icon = Icons.Outlined.ContentCopy,
            onClick = { onCopyClicked() },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_start_download_internally),
            icon = Icons.Outlined.Download,
            onClick = {
                onDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_start_download_externally),
            icon = Icons.Outlined.SystemUpdateAlt,
            onClick = {
                onExtDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_play_externally),
            icon = Icons.AutoMirrored.Outlined.OpenInNew,
            onClick = {
                onExtPlayerClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_play_internally),
            icon = Icons.AutoMirrored.Outlined.Input,
            onClick = {
                onIntPlayerClicked()
                closeMenu()
            },

        )
        ClickableRow(
            text = stringResource(TLMR.strings.action_cast), // Texto para la nueva opción
            icon = Icons.Outlined.Cast, // Icono para la nueva opción
            onClick = {
                onCastClicked()
                closeMenu()
            },
        )
    }
}

@Composable
private fun ClickableRow(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    showDropdownArrow: Boolean = false,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = TabbedDialogPaddings.Horizontal)
            .clickable(role = Role.DropdownList, onClick = onClick)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var textPadding = MaterialTheme.padding.medium

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.width(MaterialTheme.padding.small))

            textPadding = MaterialTheme.padding.small
        }
        Text(
            text = text,
            modifier = Modifier.padding(vertical = textPadding),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (showDropdownArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.NavigateNext,
                contentDescription = null,
                modifier = Modifier,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// Start tail cast

private fun sendChaptersToCast(
    context: Context,
    castSession: CastSession,
    episodeId: Long,
    title: String,
    episode: String,
    lastSecondSeen: Long,
    image: String,
    videoUrl: String,
) {
    val remoteMediaClient = castSession.remoteMediaClient
    if (remoteMediaClient != null) {
        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_SUBTITLE, episode)
            addImage(WebImage(Uri.parse(image)))
        }
        val contentType = when {
            videoUrl.contains(".m3u8") -> "application/x-mpegURL"
            videoUrl.contains(".mpd") -> "application/dash+xml"
            else -> "video/mp4"
        }
        val mediaInfo = MediaInfo.Builder(videoUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(mediaMetadata)
            .setCustomData(CastProgressSync.episodeData(episodeId))
            .build()
        val mediaQueueItem = MediaQueueItem.Builder(mediaInfo)
            .setAutoplay(true)
            .setStartTime(lastSecondSeen.toDouble() / 1000)
            .build()
        // Anything loaded on the device, paused or buffering too, keeps playing and gets the episode
        // queued after it.
        val playerState = remoteMediaClient.mediaStatus?.playerState
        if (playerState != null && playerState != MediaStatus.PLAYER_STATE_IDLE) {
            remoteMediaClient.queueAppendItem(mediaQueueItem, null).setResultCallback { result ->
                if (result.status.isSuccess) {
                    context.toast(TLMR.strings.cast_video_added_to_queue)
                } else {
                    castSession.logcat(LogPriority.ERROR) { "Cast device couldn't queue the episode: ${result.status}" }
                    context.toast(TLMR.strings.cast_error_loading)
                }
            }
        } else {
            // Si no hay un video reproduciéndose, cargar el video directamente
            val mediaLoadRequestData = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(lastSecondSeen)
                .build()
            remoteMediaClient.load(mediaLoadRequestData).setResultCallback { result ->
                if (result.status.isSuccess) {
                    context.startActivity(Intent(context, ExpandedControlsActivity::class.java))
                } else {
                    castSession.logcat(LogPriority.ERROR) { "Cast device couldn't load the episode: ${result.status}" }
                    context.toast(TLMR.strings.cast_error_loading)
                }
            }
        }
    } else {
        Toast.makeText(context, "remoteMediaClient is null", Toast.LENGTH_SHORT).show()
    }
}

// End tail cast0
