/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Code is a mix between PlayerViewModel from mpvKt and the former
 * PlayerViewModel from Aniyomi.
 */
package eu.kanade.tachiyomi.ui.player

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import animetail.feature.mpvfiles.MpvConfig
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.GetAnimeAudioLanguage
import eu.kanade.domain.entries.anime.interactor.SetAnimeAudioLanguage
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.model.toDbEpisode
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.screen.player.custombutton.CustomButtonFetchState
import eu.kanade.presentation.more.settings.screen.player.custombutton.getButtons
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.ThumbnailInfo
import eu.kanade.tachiyomi.animesource.model.TileInfo
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.data.database.models.anime.EpisodeImpl
import eu.kanade.tachiyomi.data.database.models.anime.isRecognizedNumber
import eu.kanade.tachiyomi.data.database.models.anime.toDomainEpisode
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.ui.player.cache.VideoCacheProxy
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.VideoDownloadState
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.getChangedAt
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterPriority
import eu.kanade.tachiyomi.ui.player.loader.LastPlayedVideo
import eu.kanade.tachiyomi.ui.player.loader.LastPlayedVideoStore
import eu.kanade.tachiyomi.ui.player.loader.NextEpisodePreloader
import eu.kanade.tachiyomi.ui.player.network.NetworkStreamRequest
import eu.kanade.tachiyomi.ui.player.network.NetworkStreamSource
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.utils.AniSkipApi
import eu.kanade.tachiyomi.ui.player.utils.AudioLanguageTarget
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils.Companion.getStringRes
import eu.kanade.tachiyomi.ui.player.utils.LanguageTags
import eu.kanade.tachiyomi.ui.player.utils.TrackSelect
import eu.kanade.tachiyomi.ui.player.utils.applyVideoAspect
import eu.kanade.tachiyomi.ui.reader.SaveImageNotifier
import eu.kanade.tachiyomi.util.editBackground
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.editThumbnail
import eu.kanade.tachiyomi.util.episode.filterDownloadedEpisodes
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.lang.takeBytes
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.system.toast
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.service.getEpisodeSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.entries.anime.isLocal
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import java.io.File
import java.io.InputStream
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
private const val NETWORK_STREAM_ANIME_ID = Long.MIN_VALUE + 101
private const val NETWORK_STREAM_EPISODE_ID = Long.MIN_VALUE + 102

// Hosters extracted at once: the one about to play plus one backup for a quick fallback.
private const val EXTRACTION_PERMITS = 2

// Video index of a remembered video, resolved by title once its hoster is loaded.
private const val REMEMBERED_VIDEO = -2

// Leaves several minutes of a regular episode to get the next one ready.
private const val PRELOAD_NEXT_EPISODE_PROGRESS = 0.75

@AssistedInject
class PlayerViewModel(
    @Assisted private val savedState: SavedStateHandle,
    private val context: Context,
    private val sourceManager: AnimeSourceManager,
    private val downloadManager: AnimeDownloadManager,
    private val imageSaver: ImageSaver,
    private val downloadPreferences: DownloadPreferences,
    private val trackPreferences: TrackPreferences,
    private val trackEpisode: TrackEpisode,
    private val getAnime: GetAnime,
    private val getNextEpisodes: GetNextEpisodes,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val getAnimeCategories: GetAnimeCategories,
    private val getTracks: GetAnimeTracks,
    private val upsertHistory: UpsertAnimeHistory,
    private val updateEpisode: UpdateEpisode,
    private val setAnimeViewerFlags: SetAnimeViewerFlags,
    private val getAnimeAudioLanguage: GetAnimeAudioLanguage,
    private val setAnimeAudioLanguage: SetAnimeAudioLanguage,
    internal val playerPreferences: PlayerPreferences,
    internal val gesturePreferences: GesturePreferences,
    private val basePreferences: BasePreferences,
    private val getCustomButtons: GetCustomButtons,
    private val trackSelect: TrackSelect,
    private val getIncognitoState: GetAnimeIncognitoState,
    private val libraryPreferences: LibraryPreferences,
    uiPreferences: UiPreferences,
    private val downloadCache: AnimeDownloadCache,
    private val updateAnime: UpdateAnime,
    private val coverCache: AnimeCoverCache,
    private val backgroundCache: AnimeBackgroundCache,
    private val localAnimeCoverManager: LocalAnimeCoverManager,
    private val localAnimeBackgroundManager: LocalAnimeBackgroundManager,
    private val localEpisodeThumbnailManager: LocalEpisodeThumbnailManager,
    private val trackerManager: TrackerManager,
    private val json: Json,
) : ViewModel() {

    @AssistedFactory
    @ViewModelAssistedFactoryKey(PlayerViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): PlayerViewModel {
            return create(extras.createSavedStateHandle())
        }

        fun create(@Assisted savedState: SavedStateHandle): PlayerViewModel
    }

    val cachePath: String = context.cacheDir.path

    val mpv = MPV(context.applicationContext) {
        val mpvDir = context.filesDir.resolve(PlayerActivity.MPV_DIR)
        val fontsDir = mpvDir.resolve(MpvConfig.MPV_FONTS_DIR)
        MpvConfig.setupFontsConf(context.applicationContext)
        it.setOptionString("config", "yes")
        it.setOptionString("config-dir", mpvDir.toString())
        it.setOptionString("sub-fonts-dir", fontsDir.toString())
        it.setOptionString("osd-fonts-dir", fontsDir.toString())
        it.setOptionString("gpu-shader-cache-dir", cachePath)
        it.setOptionString("icc-cache-dir", cachePath)
        it.setOptionString("keep-open", "yes")
    }

    // Playback opened through the video cache proxy; if it fails, it's retried once without it. Kept
    // here so a recreated activity still knows which playback to retry.
    var videoCachePlaybackId = -1

    /** Whether a player view has set up [mpv]; a recreated activity only attaches to it. */
    var isMpvSetUp = false

    private val eventChannel = Channel<Event>(Channel.UNLIMITED)
    val eventFlow = eventChannel.receiveAsFlow()

    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private val _currentPlaylist = MutableStateFlow<List<Episode>>(emptyList())
    val currentPlaylist = _currentPlaylist.asStateFlow()

    private val _isStopped = MutableStateFlow(false)
    val isStopped = _isStopped.asStateFlow()

    private val _hasPreviousEpisode = MutableStateFlow(false)
    val hasPreviousEpisode = _hasPreviousEpisode.asStateFlow()

    private val _hasNextEpisode = MutableStateFlow(false)
    val hasNextEpisode = _hasNextEpisode.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode = _currentEpisode.asStateFlow()

    private val _currentAnime = MutableStateFlow<Anime?>(null)
    val currentAnime = _currentAnime.asStateFlow()

    /** The current anime's audio language; null until it is resolved, or for network streams. */
    @Volatile
    var audioTarget: AudioLanguageTarget? = null
        private set

    private val _currentSource = MutableStateFlow<AnimeSource?>(null)
    val currentSource = _currentSource.asStateFlow()

    private val _isEpisodeOnline = MutableStateFlow(false)
    val isEpisodeOnline = _isEpisodeOnline.asStateFlow()

    private val _isLoadingEpisode = MutableStateFlow(false)
    val isLoadingEpisode = _isLoadingEpisode.asStateFlow()

    private val _currentDecoder = MutableStateFlow(getDecoderFromValue(mpv.getPropertyString("hwdec") ?: ""))
    val currentDecoder = _currentDecoder.asStateFlow()

    val mediaTitle = MutableStateFlow("")
    val animeTitle = MutableStateFlow("")

    val isLoading = MutableStateFlow(true)
    val playbackSpeed = MutableStateFlow(playerPreferences.playerSpeed().get())

    private val _subtitleTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val subtitleTracks = _subtitleTracks.asStateFlow()
    private val _selectedSubtitles = MutableStateFlow(Pair(-1, -1))
    val selectedSubtitles = _selectedSubtitles.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val audioTracks = _audioTracks.asStateFlow()
    private val _selectedAudio = MutableStateFlow(-1)
    val selectedAudio = _selectedAudio.asStateFlow()

    val isLoadingTracks = MutableStateFlow(true)
    val isCasting = MutableStateFlow(false)

    private val _hosterList = MutableStateFlow<List<Hoster>>(emptyList())
    val hosterList = _hosterList.asStateFlow()
    private val _isLoadingHosters = MutableStateFlow(true)
    val isLoadingHosters = _isLoadingHosters.asStateFlow()
    private val _hosterState = MutableStateFlow<List<HosterState>>(emptyList())
    val hosterState = _hosterState.asStateFlow()
    private val _hosterExpandedList = MutableStateFlow<List<Boolean>>(emptyList())
    val hosterExpandedList = _hosterExpandedList.asStateFlow()
    private val _selectedHosterVideoIndex = MutableStateFlow(Pair(-1, -1))
    val selectedHosterVideoIndex = _selectedHosterVideoIndex.asStateFlow()
    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo = _currentVideo.asStateFlow()

    val videoDownload = combine(currentEpisode, downloadManager.queueState) { episode, queue ->
        queue.find { it.episode.id == episode?.id }
    }
        .flatMapLatest { download ->
            if (download == null) return@flatMapLatest flowOf(null)
            combine(download.statusFlow, download.progressFlow) { status, progress ->
                VideoDownloadState(download.video?.videoTitle, status, progress)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _chapters = MutableStateFlow<List<IndexedSegment>>(emptyList())
    val chapters = _chapters.asStateFlow()
    private val _currentChapter = MutableStateFlow<IndexedSegment?>(null)
    val currentChapter = _currentChapter.asStateFlow()
    private val _skipIntroText = MutableStateFlow<String?>(null)
    val skipIntroText = _skipIntroText.asStateFlow()

    private val _pos = MutableStateFlow(0f)
    val pos = _pos.asStateFlow()

    private var castProgressJob: Job? = null
    private val _seekPosition = MutableStateFlow(0f)
    val seekPosition = _seekPosition.asStateFlow()

    private val _isSeeking = MutableStateFlow(false)
    val isSeeking = _isSeeking.asStateFlow()

    private val _thumbnailImage = MutableStateFlow<ImageBitmap?>(null)
    val thumbnailImage = _thumbnailImage.asStateFlow()

    private val thumbnailInfo = MutableStateFlow<ThumbnailInfo?>(null)
    private val thumbnailTileCache =
        object : LinkedHashMap<Int, Bitmap>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?) = size > 3
        }
    private var thumbnailFetchJob: Job? = null

    val duration = MutableStateFlow(0f)

    private val _readAhead = MutableStateFlow(0f)
    val readAhead = _readAhead.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused = _paused.asStateFlow()

    // False because the video shouldn't start paused
    private val _pausedState = MutableStateFlow<Boolean?>(false)
    val pausedState = _pausedState.asStateFlow()

    private val _controlsShown = MutableStateFlow(!playerPreferences.hideControls().get())
    val controlsShown = _controlsShown.asStateFlow()
    private val _seekBarShown = MutableStateFlow(!playerPreferences.hideControls().get())
    val seekBarShown = _seekBarShown.asStateFlow()
    private val _areControlsLocked = MutableStateFlow(false)
    val areControlsLocked = _areControlsLocked.asStateFlow()

    val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
    val isBrightnessSliderShown = MutableStateFlow(false)
    val isVolumeSliderShown = MutableStateFlow(false)
    val currentBrightness = MutableStateFlow(
        runCatching {
            Settings.System.getFloat(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                .normalize(0f, 255f, 0f, 1f)
        }.getOrElse { 0f },
    )
    val currentVolume = MutableStateFlow(
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getStreamVolume(AudioManager.STREAM_MUSIC),
    )
    val currentMPVVolume = MutableStateFlow(mpv.getPropertyInt("volume") ?: 100)
    var volumeBoostCap: Int = (mpv.getPropertyInt("volume-max") ?: 100) - 100

    // Pair(startingPosition, seekAmount)
    val gestureSeekAmount = MutableStateFlow<Pair<Int, Int>?>(null)

    val sheetShown = MutableStateFlow(Sheets.None)
    val panelShown = MutableStateFlow(Panels.None)
    val dialogShown = MutableStateFlow<Dialogs>(Dialogs.None)

    private val _dismissSheet = MutableStateFlow(false)
    val dismissSheet = _dismissSheet.asStateFlow()

    private val _seekText = MutableStateFlow<String?>(null)
    val seekText = _seekText.asStateFlow()
    private val _doubleTapSeekAmount = MutableStateFlow(0)
    val doubleTapSeekAmount = _doubleTapSeekAmount.asStateFlow()
    private val _isSeekingForwards = MutableStateFlow(false)
    val isSeekingForwards = _isSeekingForwards.asStateFlow()

    private var timerJob: Job? = null
    private val _remainingTime = MutableStateFlow(0)
    val remainingTime = _remainingTime.asStateFlow()

    private val _customButtons = MutableStateFlow<CustomButtonFetchState>(CustomButtonFetchState.Loading)
    val customButtons = _customButtons.asStateFlow()

    private val _primaryButtonTitle = MutableStateFlow("")
    val primaryButtonTitle = _primaryButtonTitle.asStateFlow()

    private val _primaryButton = MutableStateFlow<CustomButton?>(null)
    val primaryButton = _primaryButton.asStateFlow()

    private fun setCustomButtons(buttons: List<CustomButton>) {
        _customButtons.update { _ -> CustomButtonFetchState.Success(buttons.toImmutableList()) }
        buttons.firstOrNull { it.isFavorite }?.let {
            _primaryButton.update { _ -> it }
            // If the button text is not empty, it has been set by a lua script in which
            // case we don't want to override it
            if (_primaryButtonTitle.value.isEmpty()) {
                setPrimaryCustomButtonTitle(it)
            }
        }
    }

    /**
     * Starts a sleep timer/cancels the current timer if [seconds] is less than 1.
     */
    fun startTimer(seconds: Int) {
        timerJob?.cancel()
        _remainingTime.value = seconds
        if (seconds < 1) return
        timerJob = viewModelScope.launch {
            for (time in seconds downTo 0) {
                _remainingTime.value = time
                delay(1000)
            }
            pause()
            withUIContext { context.toast(AYMR.strings.toast_sleep_timer_ended) }
        }
    }

    fun isEpisodeOnline(): Boolean? {
        val anime = currentAnime.value ?: return null
        val episode = currentEpisode.value ?: return null
        val source = currentSource.value ?: return null
        return source is AnimeHttpSource &&
            !EpisodeLoader.isDownload(
                context,
                episode.toDomainEpisode()!!,
                anime,
            )
    }

    fun updateIsLoadingEpisode(value: Boolean) {
        _isLoadingEpisode.update { _ -> value }
    }

    private fun updateEpisodeList(episodeList: List<Episode>) {
        _currentPlaylist.update { _ -> filterEpisodeList(episodeList) }
    }

    fun getDecoder() {
        _currentDecoder.update { getDecoderFromValue(mpv.getPropertyString("hwdec-current") ?: "") }
    }

    fun updateDecoder(decoder: Decoder) {
        mpv.setPropertyString("hwdec", decoder.value)
    }

    val getTrackLanguage: (Int) -> String = {
        if (it != -1) {
            mpv.getPropertyString("track-list/$it/lang") ?: ""
        } else {
            context.stringResource(MR.strings.off)
        }
    }
    val getTrackTitle: (Int) -> String = {
        if (it != -1) {
            mpv.getPropertyString("track-list/$it/title") ?: ""
        } else {
            context.stringResource(MR.strings.off)
        }
    }
    val getTrackMPVId: (Int) -> Int = {
        if (it != -1) {
            mpv.getPropertyInt("track-list/$it/id") ?: -1
        } else {
            -1
        }
    }
    val getTrackType: (Int) -> String? = {
        mpv.getPropertyString("track-list/$it/type")
    }

    private var trackLoadingJob: Job? = null
    fun loadTracks() {
        trackLoadingJob?.cancel()
        trackLoadingJob = viewModelScope.launch {
            if (!populateTracks()) return@launch

            if (!isLoadingTracks.value) {
                onFinishLoadingTracks()
            }
        }
    }

    private fun populateTracks(): Boolean {
        val possibleTrackTypes = listOf("audio", "sub")
        val subTracks = mutableListOf<VideoTrack>()
        val audioTracks = mutableListOf(
            VideoTrack(-1, context.stringResource(MR.strings.off), null),
        )
        try {
            val tracksCount = mpv.getPropertyInt("track-list/count") ?: 0
            for (i in 0..<tracksCount) {
                val type = getTrackType(i)
                if (!possibleTrackTypes.contains(type) || type == null) continue
                when (type) {
                    "sub" -> subTracks.add(VideoTrack(getTrackMPVId(i), getTrackTitle(i), getTrackLanguage(i)))
                    "audio" -> audioTracks.add(VideoTrack(getTrackMPVId(i), getTrackTitle(i), getTrackLanguage(i)))
                    else -> error("Unrecognized track type")
                }
            }
        } catch (e: NullPointerException) {
            logcat(LogPriority.ERROR) { "Couldn't load tracks, probably cause mpv was destroyed" }
            return false
        }
        _subtitleTracks.update { subTracks }
        _audioTracks.update { audioTracks }
        return true
    }

    /**
     * When all subtitle/audio tracks are loaded, select the preferred one based on preferences,
     * or select the first one in the list if trackSelect fails.
     */
    fun onFinishLoadingTracks() {
        if (!populateTracks()) return

        val preferredSubtitle = trackSelect.getPreferredTrackIndex(subtitleTracks.value)
        (preferredSubtitle ?: subtitleTracks.value.firstOrNull())?.let {
            if (mpv.getPropertyInt("sid") != it.id) {
                mpv.setPropertyInt("sid", it.id)
            } else {
                updateSubtitle(it.id, selectedSubtitles.value.second)
            }
            mpv.setPropertyBoolean("secondary-sid", false)
        }

        val preferredAudio = trackSelect.getPreferredTrackIndex(
            audioTracks.value,
            subtitle = false,
            audioLanguages = audioTarget?.languages,
        )
        (preferredAudio ?: audioTracks.value.getOrNull(1))?.let {
            if (mpv.getPropertyInt("aid") != it.id) {
                mpv.setPropertyInt("aid", it.id)
            } else {
                updateAudio(it.id)
            }
        }

        isLoadingTracks.update { _ -> true }
        updateIsLoadingEpisode(false)
        setPausedState()
    }

    @Immutable
    data class VideoTrack(
        val id: Int,
        val name: String,
        val language: String?,
    )

    fun loadChapters() {
        val chapters = mutableListOf<IndexedSegment>()
        val count = mpv.getPropertyInt("chapter-list/count") ?: 0
        for (i in 0 until count) {
            val title = mpv.getPropertyString("chapter-list/$i/title") ?: ""
            val time = mpv.getPropertyInt("chapter-list/$i/time") ?: 0
            chapters.add(
                IndexedSegment(
                    name = title,
                    start = time.toFloat(),
                    index = 0,
                ),
            )
        }
        updateChapters(chapters.sortedBy { it.start })
    }

    fun updateChapters(chapters: List<IndexedSegment>) {
        _chapters.update { _ -> chapters }
    }

    fun selectChapter(index: Int) {
        val time = chapters.value[index].start
        seekTo(time.toInt())
    }

    fun updateChapter(index: Long) {
        if (chapters.value.isEmpty() || index == -1L) return
        _currentChapter.update { chapters.value.getOrNull(index.toInt()) ?: return }
    }

    fun addAudio(uri: Uri) {
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(context) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(context) else null
        if (name == null) {
            mpv.command("audio-add", path, "cached")
        } else {
            mpv.command("audio-add", path, "cached", name)
        }
    }

    /** Called when the user picks an audio track; remembers its language for this anime. */
    fun selectAudio(id: Int) {
        mpv.setPropertyInt("aid", id)
        val track = audioTracks.value.firstOrNull { it.id == id }?.takeIf { it.id != -1 } ?: return
        val target = audioTarget ?: return
        val setting = LanguageTags.normalize(track.language)?.let(target::settingForLanguage)
            ?: target.settingForLabel(track.name)
            ?: return
        saveAudioLanguage(setting)
    }

    private fun saveAudioLanguage(setting: String) {
        val anime = currentAnime.value?.takeIf { it.id >= 0 } ?: return
        viewModelScope.launchIO {
            val current = getAnime.await(anime.id) ?: return@launchIO
            if (current.audioLanguage == setting) return@launchIO
            setAnimeAudioLanguage.await(anime.id, setting)
            audioTarget = getAnimeAudioLanguage.await(current.copy(audioLanguage = setting))
        }
    }

    fun updateAudio(id: Int) {
        _selectedAudio.update { id }
    }

    fun addSubtitle(uri: Uri) {
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(context) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(context) else null
        if (name == null) {
            mpv.command("sub-add", path, "cached")
        } else {
            mpv.command("sub-add", path, "cached", name)
        }
    }

    fun selectSub(id: Int) {
        val selectedSubs = selectedSubtitles.value
        _selectedSubtitles.update {
            when (id) {
                selectedSubs.first -> Pair(selectedSubs.second, -1)

                selectedSubs.second -> Pair(selectedSubs.first, -1)

                else -> {
                    if (selectedSubs.first != -1) {
                        Pair(selectedSubs.first, id)
                    } else {
                        Pair(id, -1)
                    }
                }
            }
        }
        val (primary, secondary) = _selectedSubtitles.value
        if (secondary == -1) {
            mpv.setPropertyBoolean("secondary-sid", false)
        } else {
            mpv.setPropertyInt("secondary-sid", secondary)
        }
        if (primary == -1) {
            mpv.setPropertyBoolean("sid", false)
        } else {
            mpv.setPropertyInt("sid", primary)
        }
    }

    fun updateSubtitle(sid: Int, secondarySid: Int) {
        _selectedSubtitles.update { Pair(sid, secondarySid) }
    }

    fun updatePlayBackPos(pos: Float) {
        onSecondReached(pos.toInt(), duration.value.toInt())
        _pos.update { pos }
    }

    private var lastThumbnailFetch = 0L

    fun updateSeekPos(pos: Float) {
        _seekPosition.update { _ -> pos }

        val thumbInfo = thumbnailInfo.value ?: return
        val info = thumbInfo.tileInfo.lastOrNull { it.timeMs <= pos * 1000L }
        if (info != null) {
            val tileBitmap = thumbnailTileCache[info.imageIndex]
            if (tileBitmap != null) {
                createThumbnail(tileBitmap, info)
            } else {
                val now = System.currentTimeMillis()
                if (now - lastThumbnailFetch < 2.seconds.inWholeMilliseconds) return
                lastThumbnailFetch = now

                thumbnailFetchJob?.cancel()
                thumbnailFetchJob = viewModelScope.launchIO {
                    val source = currentSource.value as? AnimeHttpSource ?: return@launchIO

                    try {
                        val tileUrl = thumbInfo.imageTileUrls[info.imageIndex]
                        val bitmap = source.getImageTile(tileUrl)
                        if (bitmap != null) {
                            withUIContext {
                                thumbnailTileCache[info.imageIndex] = bitmap
                                createThumbnail(bitmap, info)
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.ERROR, e) { "Failed to fetch thumbnails tiles" }
                    }
                }
            }
        }
    }

    private fun createThumbnail(tileBitmap: Bitmap, tileInfo: TileInfo) {
        val thumbnail = Bitmap.createBitmap(tileBitmap, tileInfo.x, tileInfo.y, tileInfo.width, tileInfo.height)
        _thumbnailImage.update { _ -> thumbnail.asImageBitmap() }
    }

    fun updateIsSeeking(value: Boolean) {
        _isSeeking.update { _ -> value }
        if (!value) {
            _thumbnailImage.update { _ -> null }
        }
    }

    fun updateReadAhead(value: Long) {
        _readAhead.update { value.toFloat() }
    }

    private fun updatePausedState() {
        if (pausedState.value == null) {
            _pausedState.update { _ -> paused.value }
        }
    }

    private fun setPausedState() {
        pausedState.value?.let {
            if (it) {
                pause()
            } else {
                unpause()
            }

            _pausedState.update { _ -> null }
        }
    }

    fun pauseUnpause() {
        if (paused.value) {
            unpause()
        } else {
            pause()
        }
    }

    fun pause() {
        mpv.setPropertyBoolean("pause", true)
        _paused.update { true }
    }

    fun unpause() {
        mpv.setPropertyBoolean("pause", false)
        _paused.update { false }
    }

    fun showControls() {
        if (sheetShown.value != Sheets.None ||
            panelShown.value != Panels.None ||
            dialogShown.value != Dialogs.None
        ) {
            return
        }
        _controlsShown.update { true }
    }

    fun hideControls() {
        _controlsShown.update { false }
    }

    fun hideSeekBar() {
        _seekBarShown.update { false }
    }

    fun showSeekBar() {
        if (sheetShown.value != Sheets.None) return
        _seekBarShown.update { true }
    }

    fun lockControls() {
        _areControlsLocked.update { true }
    }

    fun unlockControls() {
        _areControlsLocked.update { false }
    }

    fun dismissSheet() {
        _dismissSheet.update { _ -> true }
    }

    private fun resetDismissSheet() {
        _dismissSheet.update { _ -> false }
    }

    fun showSheet(sheet: Sheets) {
        sheetShown.update { sheet }
        // The user is about to pick a video, so every hoster is worth extracting now.
        if (sheet == Sheets.QualityTracks) loadAllPendingHosters()
        if (sheet == Sheets.None) {
            resetDismissSheet()
            showControls()
        } else {
            hideControls()
            panelShown.update { Panels.None }
            dialogShown.update { Dialogs.None }
        }
    }

    fun showPanel(panel: Panels) {
        panelShown.update { panel }
        if (panel == Panels.None) {
            showControls()
        } else {
            hideControls()
            sheetShown.update { Sheets.None }
            dialogShown.update { Dialogs.None }
        }
    }

    fun showDialog(dialog: Dialogs) {
        dialogShown.update { dialog }
        if (dialog == Dialogs.None) {
            showControls()
        } else {
            hideControls()
            sheetShown.update { Sheets.None }
            panelShown.update { Panels.None }
        }
    }

    fun seekBy(offset: Int, precise: Boolean = false) {
        mpv.command("seek", offset.toString(), if (precise) "relative+exact" else "relative")
    }

    fun seekTo(position: Int, precise: Boolean = true) {
        if (position !in 0..(mpv.getPropertyInt("duration") ?: 0)) return
        mpv.command("seek", position.toString(), if (precise) "absolute" else "absolute+keyframes")
    }

    fun changeBrightnessTo(
        brightness: Float,
    ) {
        currentBrightness.update { _ -> brightness.coerceIn(-0.75f, 1f) }
    }

    fun displayBrightnessSlider() {
        isBrightnessSliderShown.update { true }
    }

    val maxVolume: Int get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    fun changeVolumeBy(change: Int) {
        val mpvVolume = mpv.getPropertyInt("volume") ?: 100
        if (volumeBoostCap > 0 && currentVolume.value == maxVolume) {
            if (mpvVolume == 100 && change < 0) changeVolumeTo(currentVolume.value + change)
            val finalMPVVolume = (mpvVolume + change).coerceAtLeast(100)
            if (finalMPVVolume in 100..volumeBoostCap + 100) {
                changeMPVVolumeTo(finalMPVVolume)
                return
            }
        }
        changeVolumeTo(currentVolume.value + change)
    }

    fun changeVolumeTo(volume: Int) {
        val newVolume = volume.coerceIn(0..maxVolume)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            newVolume,
            0,
        )
        currentVolume.update { newVolume }
    }

    fun changeMPVVolumeTo(volume: Int) {
        mpv.setPropertyInt("volume", volume)
    }

    fun setMPVVolume(volume: Int) {
        if (volume != currentMPVVolume.value) displayVolumeSlider()
        currentMPVVolume.update { volume }
    }

    fun displayVolumeSlider() {
        isVolumeSliderShown.update { true }
    }

    fun setAutoPlay(value: Boolean) {
        val textRes = if (value) {
            AYMR.strings.enable_auto_play
        } else {
            AYMR.strings.disable_auto_play
        }
        playerUpdate.update { PlayerUpdates.ShowTextResource(textRes) }
        playerPreferences.autoplayEnabled().set(value)
    }

    @Suppress("DEPRECATION")
    fun changeVideoAspect(aspect: VideoAspect) {
        mpv.applyVideoAspect(aspect, videoSurfaceAspect())
        playerPreferences.aspectState().set(aspect)
        playerUpdate.update { PlayerUpdates.AspectRatio }
    }

    /** Aspect ratio of the surface mpv renders into, which is smaller than the screen in tabletop mode. */
    private fun videoSurfaceAspect(): Double {
        val width = mpv.getPropertyInt("osd-width") ?: 0
        val height = mpv.getPropertyInt("osd-height") ?: 0
        if (width > 0 && height > 0) return width / height.toDouble()
        val dm = context.resources.displayMetrics
        return dm.widthPixels / dm.heightPixels.toDouble()
    }

    fun cycleScreenRotations() {
        val next = when (playerPreferences.defaultPlayerOrientationType().get()) {
            PlayerOrientation.Landscape,
            PlayerOrientation.ReverseLandscape,
            PlayerOrientation.SensorLandscape,
            -> PlayerOrientation.SensorPortrait

            else -> PlayerOrientation.SensorLandscape
        }
        playerPreferences.defaultPlayerOrientationType().set(next)
    }

    fun handleLuaInvocation(property: String, value: String) {
        val data = value
            .removePrefix("\"")
            .removeSuffix("\"")
            .ifEmpty { return }

        when (property.substringAfterLast("/")) {
            "show_text" -> playerUpdate.update { PlayerUpdates.ShowText(data) }

            "toggle_ui" -> {
                when (data) {
                    "show" -> showControls()

                    "toggle" -> {
                        if (controlsShown.value) hideControls() else showControls()
                    }

                    "hide" -> {
                        sheetShown.update { Sheets.None }
                        panelShown.update { Panels.None }
                        dialogShown.update { Dialogs.None }
                        hideControls()
                    }
                }
            }

            "show_panel" -> {
                when (data) {
                    "subtitle_settings" -> showPanel(Panels.SubtitleSettings)
                    "subtitle_delay" -> showPanel(Panels.SubtitleDelay)
                    "audio_delay" -> showPanel(Panels.AudioDelay)
                    "video_filters" -> showPanel(Panels.VideoFilters)
                }
            }

            "set_button_title" -> {
                _primaryButtonTitle.update { _ -> data }
            }

            "reset_button_title" -> {
                _customButtons.value.getButtons().firstOrNull { it.isFavorite }?.let {
                    setPrimaryCustomButtonTitle(it)
                }
            }

            "switch_episode" -> {
                when (data) {
                    "n" -> changeEpisode(false)
                    "p" -> changeEpisode(true)
                }
            }

            "launch_int_picker" -> {
                val (title, nameFormat, start, stop, step, pickerProperty) = data.split("|")
                val defaultValue = mpv.getPropertyInt(pickerProperty) ?: 0
                showDialog(
                    Dialogs.IntegerPicker(
                        defaultValue = defaultValue,
                        minValue = start.toInt(),
                        maxValue = stop.toInt(),
                        step = step.toInt(),
                        nameFormat = nameFormat,
                        title = title,
                        onChange = { mpv.setPropertyInt(pickerProperty, it) },
                        onDismissRequest = { showDialog(Dialogs.None) },
                    ),
                )
            }

            "pause" -> {
                when (data) {
                    "pause" -> pause()
                    "unpause" -> unpause()
                    "pauseunpause" -> pauseUnpause()
                }
            }

            "seek_to_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekToWithText(seekValue.toInt(), text)
            }

            "seek_by_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekByWithText(seekValue.toInt(), text)
            }

            "seek_by" -> seekByWithText(data.toInt(), null)

            "seek_to" -> seekToWithText(data.toInt(), null)

            "toggle_button" -> {
                fun showButton() {
                    if (_primaryButton.value == null) {
                        _primaryButton.update {
                            customButtons.value.getButtons().firstOrNull { it.isFavorite }
                        }
                    }
                }

                when (data) {
                    "show" -> showButton()
                    "hide" -> _primaryButton.update { null }
                    "toggle" -> if (_primaryButton.value == null) showButton() else _primaryButton.update { null }
                }
            }

            "software_keyboard" -> when (data) {
                "show" -> eventChannel.trySend(Event.SetKeyboardVisibility(true))
                "hide" -> eventChannel.trySend(Event.SetKeyboardVisibility(false))
                "toggle" -> eventChannel.trySend(Event.SetKeyboardVisibility(null))
            }
        }

        mpv.setPropertyString(property, "")
    }

    private operator fun <T> List<T>.component6(): T = get(5)

    private val doubleTapToSeekDuration = gesturePreferences.skipLengthPreference().get()
    private val preciseSeek = gesturePreferences.playerSmoothSeek().get()
    private val showSeekBar = gesturePreferences.showSeekBar().get()

    private fun seekToWithText(seekValue: Int, text: String?) {
        _isSeekingForwards.value = seekValue > 0
        _doubleTapSeekAmount.value = seekValue - pos.value.toInt()
        _seekText.update { _ -> text }
        seekTo(seekValue, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    private fun seekByWithText(value: Int, text: String?) {
        _doubleTapSeekAmount.update {
            if ((value < 0 && it < 0) ||
                pos.value + value > duration.value
            ) {
                0
            } else {
                it + value
            }
        }
        _seekText.update { text }
        _isSeekingForwards.value = value > 0
        seekBy(value, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    fun updateSeekAmount(amount: Int) {
        _doubleTapSeekAmount.update { _ -> amount }
    }

    fun updateSeekText(value: String?) {
        _seekText.update { _ -> value }
    }

    fun leftSeek() {
        if (pos.value > 0) {
            _doubleTapSeekAmount.value -= doubleTapToSeekDuration
        }
        _isSeekingForwards.value = false
        seekBy(-doubleTapToSeekDuration, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    fun rightSeek() {
        if (pos.value < duration.value) {
            _doubleTapSeekAmount.value += doubleTapToSeekDuration
        }
        _isSeekingForwards.value = true
        seekBy(doubleTapToSeekDuration, preciseSeek)
        if (showSeekBar) showSeekBar()
    }

    /**
     * Reset state when changing episodes
     */
    fun resetState() {
        _pausedState.update { _ -> false }
        _hosterState.update { _ -> emptyList() }
        _hosterList.update { _ -> emptyList() }
        _hosterExpandedList.update { _ -> emptyList() }
        _selectedHosterVideoIndex.update { _ -> Pair(-1, -1) }
        thumbnailTileCache.clear()
        thumbnailFetchJob?.cancel()
        lastThumbnailFetch = 0L
        _currentVideo.update { _ -> null }
    }

    fun changeEpisode(previous: Boolean, autoPlay: Boolean = false) {
        if (previous && !hasPreviousEpisode.value) {
            eventChannel.trySend(Event.ShowToast(context.stringResource(AYMR.strings.no_prev_episode)))
            return
        }

        if (!previous && !hasNextEpisode.value) {
            eventChannel.trySend(Event.ShowToast(context.stringResource(AYMR.strings.no_next_episode)))
            return
        }

        eventChannel.trySend(
            Event.ChangeEpisode(
                episodeId = getAdjacentEpisodeId(previous = previous),
                autoPlay = autoPlay,
            ),
        )
    }

    fun handleLeftDoubleTap() {
        when (gesturePreferences.leftDoubleTapGesture().get()) {
            SingleActionGesture.Seek -> {
                leftSeek()
            }

            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }

            SingleActionGesture.Custom -> {
                mpv.command("keypress", CustomKeyCodes.DoubleTapLeft.keyCode)
            }

            SingleActionGesture.None -> {}

            SingleActionGesture.Switch -> changeEpisode(true)
        }
    }

    fun handleCenterDoubleTap() {
        when (gesturePreferences.centerDoubleTapGesture().get()) {
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }

            SingleActionGesture.Custom -> {
                mpv.command("keypress", CustomKeyCodes.DoubleTapCenter.keyCode)
            }

            SingleActionGesture.Seek -> {}

            SingleActionGesture.None -> {}

            SingleActionGesture.Switch -> {}
        }
    }

    fun handleRightDoubleTap() {
        when (gesturePreferences.rightDoubleTapGesture().get()) {
            SingleActionGesture.Seek -> {
                rightSeek()
            }

            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }

            SingleActionGesture.Custom -> {
                mpv.command("keypress", CustomKeyCodes.DoubleTapRight.keyCode)
            }

            SingleActionGesture.None -> {}

            SingleActionGesture.Switch -> changeEpisode(false)
        }
    }

    override fun onCleared() {
        if (currentEpisode.value != null) {
            saveWatchingProgress(currentEpisode.value!!)
            clearVideoCacheIfSeen(currentEpisode.value!!)
            episodeToDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }
    }

    // Waits until the player leaves the episode, its cache is still being read while it plays.
    private fun clearVideoCacheIfSeen(episode: Episode) {
        val sourceId = currentAnime.value?.source ?: return
        if (!episode.seen) return
        // Not viewModelScope, it's already cancelled when called from onCleared.
        ProcessLifecycleOwner.get().lifecycleScope.launchIO {
            VideoCacheProxy.clearFinishedEpisodes(sourceId, listOf(episode.url))
        }
    }

    fun updateCastProgress(position: Float) {
        _pos.update { position }
    }

    fun resumeFromCast() {
        val lastPosition = _pos.value

        logcat { "Reanudando el video local desde: $lastPosition segundos" }

        if (lastPosition > 0) {
            seekTo(lastPosition.toInt()) // Mueve el reproductor local a la última posición
        }
    }
    fun stopHttpServer() {
        val server = (currentSource.value as? AnimeHttpSource)?.server
            ?: return

        if (server.isRunning()) {
            server.stop()
        }
    }

    // ====== OLD ======

    val incognitoMode: Boolean by lazy { runBlocking { getIncognitoState.await(currentAnime.value?.source) } }
    private val downloadAheadAmount = downloadPreferences.autoDownloadWhileWatching.get()

    internal val relativeTime = uiPreferences.relativeTime.get()
    internal val dateFormat = UiPreferences.dateFormat(uiPreferences.dateFormat.get())

    /**
     * The position in the current video. Used to restore from process kill.
     */
    private var episodePosition = savedState.get<Long>("episode_position")
        set(value) {
            savedState["episode_position"] = value
            field = value
        }

    /**
     * The current video's quality index. Used to restore from process kill.
     */
    private var qualityIndex = savedState.get<Pair<Int, Int>>("quality_index") ?: Pair(-1, -1)
        set(value) {
            savedState["quality_index"] = value
            field = value
        }

    /**
     * The episode id of the currently loaded episode. Used to restore from process kill.
     */
    private var episodeId = savedState.get<Long>("episode_id") ?: -1L
        set(value) {
            savedState["episode_id"] = value
            field = value
        }

    private var episodeToDownload: AnimeDownload? = null

    private fun filterEpisodeList(episodes: List<Episode>): List<Episode> {
        val anime = currentAnime.value ?: return episodes
        val selectedEpisode = episodes.find { it.id == episodeId }
            ?: error("Requested episode of id $episodeId not found in episode list")

        val episodesForPlayer = episodes.filterNot {
            (
                anime.unseenFilterRaw == Anime.EPISODE_SHOW_SEEN &&
                    !it.seen
                ) ||
                (
                    anime.unseenFilterRaw == Anime.EPISODE_SHOW_UNSEEN &&
                        it.seen
                    ) ||
                (
                    anime.downloadedFilterRaw == Anime.EPISODE_SHOW_DOWNLOADED &&
                        !downloadManager.isEpisodeDownloaded(
                            it.name,
                            it.scanlator,
                            it.url,
                            anime.title,
                            anime.source,
                        )
                    ) ||
                (
                    anime.downloadedFilterRaw == Anime.EPISODE_SHOW_NOT_DOWNLOADED &&
                        downloadManager.isEpisodeDownloaded(
                            it.name,
                            it.scanlator,
                            it.url,
                            anime.title,
                            anime.source,
                        )
                    ) ||
                (
                    anime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_BOOKMARKED &&
                        !it.bookmark
                    ) ||
                (
                    anime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_NOT_BOOKMARKED &&
                        it.bookmark
                    ) ||
                (
                    anime.fillermarkedFilterRaw == Anime.EPISODE_SHOW_FILLERMARKED &&
                        !it.fillermark
                    ) ||
                (
                    anime.fillermarkedFilterRaw == Anime.EPISODE_SHOW_NOT_FILLERMARKED &&
                        it.fillermark
                    )
        }.toMutableList()

        if (episodesForPlayer.all { it.id != episodeId }) {
            episodesForPlayer += listOf(selectedEpisode)
        }

        return episodesForPlayer
    }

    fun getCurrentEpisodeIndex(): Int {
        return currentPlaylist.value.indexOfFirst { currentEpisode.value?.id == it.id }
    }

    private fun getAdjacentEpisodeId(previous: Boolean): Long {
        val newIndex = if (previous) getCurrentEpisodeIndex() - 1 else getCurrentEpisodeIndex() + 1

        return when {
            previous && getCurrentEpisodeIndex() == 0 -> -1L
            !previous && currentPlaylist.value.lastIndex == getCurrentEpisodeIndex() -> -1L
            else -> currentPlaylist.value.getOrNull(newIndex)?.id ?: -1L
        }
    }

    fun updateHasNextEpisode(value: Boolean) {
        _hasNextEpisode.update { _ -> value }
    }

    fun updateHasPreviousEpisode(value: Boolean) {
        _hasPreviousEpisode.update { _ -> value }
    }

    fun showEpisodeListDialog() {
        if (currentAnime.value != null) {
            showDialog(Dialogs.EpisodeList)
        }
    }

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active episode.
     */
    fun onSaveInstanceStateNonConfigurationChange() {
        if (isNetworkStreamSession) return
        val currentEpisode = currentEpisode.value ?: return
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(currentEpisode)
        }
    }

    // ====== Initialize anime, episode, hoster, and video list ======

    fun updateIsLoadingHosters(value: Boolean) {
        _isLoadingHosters.update { _ -> value }
    }

    /**
     * Whether this viewModel is initialized with the correct episode.
     */
    private fun needsInit(animeId: Long, episodeId: Long): Boolean {
        return currentAnime.value?.id != animeId || currentEpisode.value?.id != episodeId
    }

    data class InitResult(
        val hosterList: List<Hoster>?,
        val videoIndex: Pair<Int, Int>,
        val position: Long?,
    )

    private var currentHosterList: List<Hoster>? = null
    private var isNetworkStreamSession: Boolean = false

    class ExceptionWithStringResource(
        message: String,
        val stringResource: StringResource,
    ) : Exception(message)

    suspend fun init(
        animeId: Long,
        initialEpisodeId: Long,
        hostList: String,
        hostIndex: Int,
        vidIndex: Int,
    ): Pair<InitResult, Result<Boolean>> {
        val defaultResult = InitResult(currentHosterList, qualityIndex, null)
        isNetworkStreamSession = false
        if (!needsInit(animeId, initialEpisodeId)) return Pair(defaultResult, Result.success(true))
        return try {
            val anime = getAnime.await(animeId)
            if (anime != null) {
                _currentAnime.update { _ -> anime }
                animeTitle.update { _ -> anime.title }
                sourceManager.isInitialized.first { it }
                episodeId = initialEpisodeId

                val buttons = getCustomButtons.getAll()
                setCustomButtons(buttons)

                checkTrackers(anime)

                updateEpisodeList(initEpisodeList(anime))

                val episode = currentPlaylist.value.first { it.id == episodeId }
                val source = sourceManager.getOrStub(anime.source)

                _currentEpisode.update { _ -> episode }
                _currentSource.update { _ -> source }
                audioTarget = resolveAudioTarget(anime)

                updateEpisode(episode)

                _hasPreviousEpisode.update { _ -> getCurrentEpisodeIndex() != 0 }
                _hasNextEpisode.update { _ -> getCurrentEpisodeIndex() != currentPlaylist.value.size - 1 }

                // Write to mpv table
                val parentTitle = anime.parentId?.let { getAnime.await(it)?.title } ?: ""
                mpv.setPropertyString("user-data/current-anime/anime-title", anime.title)
                mpv.setPropertyString("user-data/current-anime/parent-title", parentTitle)
                mpv.setPropertyInt("user-data/current-anime/intro-length", getAnimeSkipIntroLength())
                mpv.setPropertyString(
                    "user-data/current-anime/category",
                    getAnimeCategories.await(anime.id).joinToString {
                        it.name
                    },
                )

                val currentEp = currentEpisode.value
                    ?: throw ExceptionWithStringResource("No episode loaded", AYMR.strings.no_episode_loaded)
                if (hostList.isNotBlank()) {
                    currentHosterList = hostList.toHosterList().ifEmpty {
                        currentHosterList = null
                        throw ExceptionWithStringResource(
                            "Hoster selected from empty list",
                            AYMR.strings.select_hoster_from_empty_list,
                        )
                    }
                    qualityIndex = Pair(hostIndex, vidIndex)
                } else {
                    EpisodeLoader.getHosters(context, currentEp.toDomainEpisode()!!, anime, source)
                        .takeIf { it.isNotEmpty() }
                        ?.also { currentHosterList = it }
                        ?: run {
                            currentHosterList = null
                            throw ExceptionWithStringResource("Hoster list is empty", AYMR.strings.no_hosters)
                        }
                }

                val result = InitResult(
                    hosterList = currentHosterList,
                    videoIndex = qualityIndex,
                    position = episodePosition,
                )
                Pair(result, Result.success(true))
            } else {
                // Unlikely but okay
                Pair(defaultResult, Result.success(false))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to initialize the player" }
            Pair(defaultResult, Result.failure(e))
        }
    }

    private suspend fun resolveAudioTarget(anime: Anime): AudioLanguageTarget? {
        return try {
            getAnimeAudioLanguage.await(anime)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Could not resolve the audio language of ${anime.title}" }
            null
        }
    }

    suspend fun prepareNetworkStream(request: NetworkStreamRequest) {
        cancelHosterVideoLinksJob()
        isNetworkStreamSession = true
        audioTarget = null
        isLoading.update { false }
        updateIsLoadingEpisode(false)
        updateIsLoadingHosters(false)

        val fallbackTitle = request.title.ifBlank { request.url }
        val anime = Anime.create().copy(
            id = NETWORK_STREAM_ANIME_ID,
            source = NetworkStreamSource.id,
            ogTitle = fallbackTitle,
        )
        val episode = EpisodeImpl().apply {
            id = NETWORK_STREAM_EPISODE_ID
            anime_id = NETWORK_STREAM_ANIME_ID
            url = request.url
            name = fallbackTitle
            episode_number = 1f
            date_upload = System.currentTimeMillis()
        }

        val video = request.toVideo()
        val hoster = Hoster(
            hosterUrl = request.url,
            hosterName = Hoster.NO_HOSTER_LIST,
            videoList = listOf(video),
        )

        currentHosterList = listOf(hoster)
        qualityIndex = Pair(0, 0)
        episodeId = -1L
        episodePosition = 0L

        animeTitle.update { fallbackTitle }
        mediaTitle.update { fallbackTitle }
        _currentAnime.update { anime }
        _currentEpisode.update { episode }
        _currentPlaylist.update { listOf(episode) }
        _currentSource.update { NetworkStreamSource }
        _isEpisodeOnline.update { true }
        updateHasNextEpisode(false)
        updateHasPreviousEpisode(false)

        _hosterList.update { currentHosterList ?: emptyList() }
        _hosterExpandedList.update { listOf(true) }
        _hosterState.update {
            listOf(
                HosterState.Ready(
                    name = hoster.hosterName,
                    videoList = listOf(video),
                    videoState = listOf(Video.State.READY),
                ),
            )
        }
        _selectedHosterVideoIndex.update { Pair(0, 0) }
        _currentVideo.update { video }

        eventChannel.trySend(Event.SetVideo(video))
    }

    private fun updateEpisode(episode: Episode) {
        mediaTitle.update { _ -> episode.name }
        _isEpisodeOnline.update { _ -> isEpisodeOnline() == true }
        mpv.setPropertyDouble("user-data/current-anime/episode-number", episode.episode_number.toDouble())
    }

    private fun initEpisodeList(anime: Anime): List<Episode> {
        val episodes = runBlocking { getEpisodesByAnimeId.await(anime.id) }

        return episodes
            .sortedWith(getEpisodeSort(anime, sortDescending = false))
            .run {
                if (basePreferences.downloadedOnly.get()) {
                    filterDownloadedEpisodes(anime, downloadCache)
                } else {
                    this
                }
            }
            .map { it.toDbEpisode() }
    }

    private var hasTrackers: Boolean = false
    private val checkTrackers: (Anime) -> Unit = { anime ->
        val tracks = runBlocking { getTracks.await(anime.id) }
        hasTrackers = tracks.isNotEmpty()
    }

    private var getHosterVideoLinksJob: Job? = null

    fun cancelHosterVideoLinksJob() {
        getHosterVideoLinksJob?.cancel()
    }

    /**
     * Extracts the hosters' videos and starts playback as soon as possible.
     *
     * Hosters are extracted in priority order, [EXTRACTION_PERMITS] at a time, and playback starts
     * with the first one that has a playable video once all hosters before it have settled. When
     * autoplaying, no more hosters are extracted once that many are loading or have a playable
     * video; the rest stay pending until a fallback needs them or the qualities sheet is opened.
     * [hosterIndex] and [videoIndex] select a specific video (e.g. picked in the episode options
     * dialog), or are `-1` to let the order decide. With [autoPlay] off the list is only loaded,
     * leaving the current playback untouched.
     */
    fun loadHosters(
        source: AnimeSource,
        hosterList: List<Hoster>,
        hosterIndex: Int,
        videoIndex: Int,
        autoPlay: Boolean = true,
    ) {
        val requestedVideo = (hosterIndex to videoIndex).takeIf { hosterIndex != -1 }
            ?: lastPlayedVideo(source, hosterList).takeIf { autoPlay }

        _hosterList.update { _ -> hosterList }
        _hosterExpandedList.update { _ ->
            List(hosterList.size) { true }
        }

        getHosterVideoLinksJob?.cancel()
        getHosterVideoLinksJob = viewModelScope.launchIO {
            _hosterState.update { _ -> hosterList.map(::initialHosterState) }
            cachedHosterIndices.clear()
            cachedHosterIndices.addAll(
                hosterList.indices.filter { EpisodeLoader.hasCachedVideos(context, hosterList[it], source) },
            )
            val requestedHosterIdx = requestedVideo?.first
                ?.takeIf { _hosterState.value.getOrNull(it) is HosterState.Idle }
            pendingHosterIndices.clear()
            pendingHosterIndices.addAll(
                hosterList.indices.filter {
                    _hosterState.value[it] is HosterState.Idle &&
                        (!hosterList[it].lazy || it == requestedHosterIdx)
                },
            )
            // A channel hands out hosters in priority order; launching one coroutine per hoster
            // wouldn't, as they race for the permits. A hoster picked by the user goes first.
            val pendingHosters = Channel<Int>(Channel.UNLIMITED)
            pendingHosterIndices.sortedBy { if (it == requestedHosterIdx) -1 else it }
                .forEach { pendingHosters.trySend(it) }
            pendingHosters.close()

            try {
                coroutineScope {
                    List(EXTRACTION_PERMITS) {
                        async {
                            for (hosterIdx in pendingHosters) {
                                if (autoPlay && hasEnoughCandidates()) continue
                                loadPendingHoster(source, hosterIdx)
                                // Before taking the next hoster, so it sees whether playback started.
                                if (autoPlay) startPlaybackIfReady(source, requestedVideo)
                            }
                        }
                    }.awaitAll()
                    // Covers hosters that were already loaded and lists where nothing is playable.
                    if (autoPlay) startPlaybackIfReady(source, requestedVideo)
                }
            } catch (e: CancellationException) {
                _hosterState.update { _ ->
                    hosterList.map { HosterState.Idle(it.hosterName) }
                }

                throw e
            }
        }
    }

    fun setIsStopped(value: Boolean) {
        _isStopped.update { _ -> value }
    }

    private fun initialHosterState(hoster: Hoster): HosterState {
        val videoList = hoster.videoList
        return when {
            hoster.lazy || videoList == null -> HosterState.Idle(hoster.hosterName)
            else -> HosterState.Ready(hoster.hosterName, videoList, List(videoList.size) { Video.State.QUEUE })
        }
    }

    private val videoSelectionMutex = Mutex()

    // Hosters not extracted yet, waiting for their turn or left for later; see [loadHosters].
    private val pendingHosterIndices = ConcurrentHashMap.newKeySet<Int>()

    // Loading hosters count too: they are likely to end up playable.
    private fun hasEnoughCandidates(): Boolean {
        val candidates = _hosterState.value.count { state ->
            state is HosterState.Loading ||
                (
                    state is HosterState.Ready &&
                        state.videoState.any { it == Video.State.QUEUE || it == Video.State.READY }
                    )
        }
        return candidates >= EXTRACTION_PERMITS
    }

    /**
     * Extracts the first pending hoster in priority order.
     *
     * @return false if there was no pending hoster left.
     */
    private suspend fun loadNextPendingHoster(source: AnimeSource): Boolean {
        val hosterIdx = pendingHosterIndices.minOrNull() ?: return false
        loadPendingHoster(source, hosterIdx)
        return true
    }

    private suspend fun loadPendingHoster(source: AnimeSource, hosterIdx: Int) {
        // Removing claims the hoster, so it's never extracted twice.
        if (!pendingHosterIndices.remove(hosterIdx)) return
        val hoster = hosterList.value.getOrNull(hosterIdx) ?: return
        _hosterState.updateAt(hosterIdx, HosterState.Loading(hoster.hosterName))
        _hosterState.updateAt(hosterIdx, EpisodeLoader.loadHosterVideos(context, source, hoster))
        restoreSelection(hosterIdx)
    }

    // A list reloaded under a playing video loses its indices; the video is marked again once
    // its hoster is loaded, so the sheet keeps showing what is playing.
    private var selectionToRestore: LastPlayedVideo? = null

    private fun restoreSelection(hosterIdx: Int) {
        val target = selectionToRestore ?: return
        val hoster = _hosterState.value.getOrNull(hosterIdx) as? HosterState.Ready ?: return
        if (hoster.name != target.hosterName) return
        val videoIdx = hoster.videoList.indexOfFirst { it.videoTitle == target.videoTitle }
        if (videoIdx == -1) return
        selectionToRestore = null
        _selectedHosterVideoIndex.compareAndSet(Pair(-1, -1), Pair(hosterIdx, videoIdx))
    }

    private fun selectedVideo(): LastPlayedVideo? {
        val (hosterIdx, videoIdx) = _selectedHosterVideoIndex.value
        val hoster = _hosterState.value.getOrNull(hosterIdx) as? HosterState.Ready ?: return null
        val video = hoster.videoList.getOrNull(videoIdx) ?: return null
        return LastPlayedVideo(hoster.name, video.videoTitle)
    }

    private fun loadAllPendingHosters() {
        val source = currentSource.value ?: return
        pendingHosterIndices.toList().forEach { hosterIdx ->
            viewModelScope.launchIO { loadPendingHoster(source, hosterIdx) }
        }
    }

    // Hosters whose videos came from the cache. Their links may have expired, so on a playback
    // error they get one fresh extraction before moving on to the next hoster.
    private val cachedHosterIndices = ConcurrentHashMap.newKeySet<Int>()

    // The video is matched by title once its hoster is loaded, since indices change between loads.
    // A hoster disabled since then is left to the usual order.
    private fun lastPlayedVideo(source: AnimeSource, hosterList: List<Hoster>): Pair<Int, Int>? {
        if (!playerPreferences.rememberLastVideo().get()) return null
        val episodeUrl = currentEpisode.value?.url ?: return null
        val lastPlayed = LastPlayedVideoStore.shared.get(source.id, episodeUrl) ?: return null
        val hosterIdx = hosterList.indexOfFirst { it.hosterName == lastPlayed.hosterName && !it.lazy }
        if (hosterIdx == -1) return null
        rememberedVideoTitle = lastPlayed.videoTitle
        return hosterIdx to REMEMBERED_VIDEO
    }

    private var rememberedVideoTitle: String? = null

    private fun resolveRememberedVideo(requested: Pair<Int, Int>): Pair<Int, Int> {
        if (requested.second != REMEMBERED_VIDEO) return requested
        val hoster = _hosterState.value.getOrNull(requested.first) as? HosterState.Ready ?: return requested
        return requested.first to hoster.videoList.indexOfFirst { it.videoTitle == rememberedVideoTitle }
    }

    private suspend fun startPlaybackIfReady(source: AnimeSource, requestedVideo: Pair<Int, Int>?) {
        videoSelectionMutex.withLock {
            if (_selectedHosterVideoIndex.value.first != -1) return
            if (requestedVideo != null && isHosterUnsettled(requestedVideo.first)) return

            val next = requestedVideo?.let(::resolveRememberedVideo)?.takeIf { isPlayable(it) }
                ?: HosterLoader.selectNextInOrder(_hosterState.value)
                ?: return
            playInOrder(source, next)
        }
    }

    // The video picked by the user is waited for; only once its hoster fails does the order decide.
    // An idle hoster counts as unsettled since loadHosters always queues the requested one.
    private fun isHosterUnsettled(hosterIdx: Int): Boolean {
        val state = _hosterState.value.getOrNull(hosterIdx)
        return state is HosterState.Loading || state is HosterState.Idle
    }

    private fun isPlayable(index: Pair<Int, Int>): Boolean {
        val hoster = _hosterState.value.getOrNull(index.first) as? HosterState.Ready ?: return false
        return hoster.videoState.getOrNull(index.second) == Video.State.QUEUE
    }

    /**
     * Plays the video at [start], falling back to the next one in hoster order every time a video
     * can't be resolved, and extracting pending hosters when nothing else is left. Stops early
     * when the next candidate depends on a hoster still loading; that hoster resumes the search
     * once it finishes.
     */
    private suspend fun playInOrder(source: AnimeSource, start: Pair<Int, Int>) {
        var next: Pair<Int, Int>? = start
        while (next != null) {
            if (next.first == -1) {
                if (!loadNextPendingHoster(source)) return onNoAvailableVideos()
            } else if (loadVideo(source, next.first, next.second)) {
                return
            }
            next = HosterLoader.selectNextInOrder(_hosterState.value)
        }
    }

    private fun onNoAvailableVideos() {
        updateIsLoadingEpisode(false)
        eventChannel.trySend(Event.ShowToast(context.stringResource(AYMR.strings.no_available_videos)))
    }

    /**
     * Resolves and plays a video, marking it in the hoster state.
     *
     * @return false if the video couldn't be resolved, in which case it's marked as failed and
     * the previous selection is restored.
     */
    private suspend fun loadVideo(source: AnimeSource?, hosterIndex: Int, videoIndex: Int): Boolean {
        val hosterState = _hosterState.value.getOrNull(hosterIndex) as? HosterState.Ready ?: return false
        val video = hosterState.videoList.getOrNull(videoIndex) ?: return false
        val isResolved = hosterState.videoState[videoIndex] == Video.State.READY
        updateIsLoadingEpisode(true)

        val oldSelectedIndex = _selectedHosterVideoIndex.value
        _selectedHosterVideoIndex.update { _ -> Pair(hosterIndex, videoIndex) }
        updateVideoState(hosterIndex, videoIndex, video, Video.State.LOAD_VIDEO)

        // Pause until everything has loaded
        updatePausedState()
        pause()

        val resolvedVideo = if (isResolved) video else HosterLoader.getResolvedVideo(source, video)

        if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
            updateVideoState(hosterIndex, videoIndex, video, Video.State.ERROR)
            _selectedHosterVideoIndex.update { _ -> oldSelectedIndex }
            return false
        }

        updateVideoState(hosterIndex, videoIndex, resolvedVideo, Video.State.READY)
        _currentVideo.update { _ -> resolvedVideo }

        qualityIndex = Pair(hosterIndex, videoIndex)

        viewModelScope.launchIO {
            loadThumbnails(resolvedVideo, source)
        }

        eventChannel.trySend(Event.SetVideo(resolvedVideo))
        return true
    }

    fun updateVideo(video: Video) {
        _currentVideo.update { _ -> video }
    }

    /** Key under which the video cache stores [video], stable when its URL changes. */
    fun videoCacheKey(video: Video): String? {
        val source = currentSource.value as? AnimeHttpSource ?: return null
        val episodeUrl = currentEpisode.value?.url ?: return null
        val hoster = _hosterState.value.getOrNull(_selectedHosterVideoIndex.value.first) ?: return null
        return VideoCacheProxy.cacheKey(source.id, episodeUrl, hoster.name, video.videoTitle)
    }

    /** Called once mpv opened the current video, which is then remembered for the episode. */
    fun onFileLoaded() {
        if (!playerPreferences.rememberLastVideo().get()) return
        val source = currentSource.value as? AnimeHttpSource ?: return
        val episodeUrl = currentEpisode.value?.url ?: return
        val video = selectedVideo() ?: return
        LastPlayedVideoStore.shared.put(source.id, episodeUrl, video)
    }

    // Reads the latest state inside the update: hosters finish loading concurrently, so a snapshot
    // taken earlier would overwrite their results.
    private fun updateVideoState(hosterIndex: Int, videoIndex: Int, video: Video, state: Video.State) {
        _hosterState.update { states ->
            val hoster = states.getOrNull(hosterIndex) as? HosterState.Ready ?: return@update states
            states.toMutableList().apply {
                this[hosterIndex] = hoster.getChangedAt(videoIndex, video, state)
            }
        }
    }

    /**
     * Identifies the video currently handed to mpv. A broken HLS playlist makes mpv retry every
     * segment as a separate file, so a single failure arrives as a burst of errors that may still
     * be delivered after the next video was loaded.
     */
    @Volatile
    var playbackId = 0
        private set
    private var failedPlaybackId = -1

    /**
     * Called by the activity right before it hands a video to mpv, so errors reported for the
     * previous file can be told apart from the new one.
     */
    fun onVideoHandedToPlayer() {
        playbackId++
    }

    /**
     * Called when mpv fails to open or play a video. Only the first error of the current video is
     * handled: it's marked as failed, its cached links are dropped and, if enabled, playback
     * continues with the next video in order.
     */
    fun onPlaybackError(playbackId: Int, message: String) {
        if (playbackId != this.playbackId || playbackId == failedPlaybackId) return
        failedPlaybackId = playbackId
        eventChannel.trySend(Event.ShowToast(message))

        val (hosterIdx, videoIdx) = _selectedHosterVideoIndex.value
        val source = currentSource.value ?: return
        val failedVideo = (_hosterState.value.getOrNull(hosterIdx) as? HosterState.Ready)
            ?.videoList?.getOrNull(videoIdx)

        // Nothing is selected when the list was reloaded under a video that was already playing.
        if (failedVideo != null) {
            updateVideoState(hosterIdx, videoIdx, failedVideo, Video.State.ERROR)
            hosterList.value.getOrNull(hosterIdx)?.let { EpisodeLoader.invalidateCache(context, it, source) }
        }

        if (!playerPreferences.switchOnFailure().get()) {
            updateIsLoadingEpisode(false)
            setIsStopped(true)
            return
        }

        viewModelScope.launchIO {
            videoSelectionMutex.withLock {
                _selectedHosterVideoIndex.update { _ -> Pair(-1, -1) }
                if (failedVideo != null && cachedHosterIndices.remove(hosterIdx)) {
                    if (retryWithFreshLinks(source, hosterIdx, failedVideo)) return@launchIO
                }
                val next = HosterLoader.selectNextInOrder(_hosterState.value) ?: return@launchIO
                if (next.first != -1) {
                    val nextName = _hosterState.value[next.first].name
                    eventChannel.trySend(
                        Event.ShowToast(context.stringResource(AYMR.strings.player_hoster_switching, nextName)),
                    )
                }
                playInOrder(source, next)
            }
        }
    }

    private suspend fun retryWithFreshLinks(source: AnimeSource, hosterIdx: Int, failedVideo: Video): Boolean {
        val hoster = hosterList.value.getOrNull(hosterIdx) ?: return false
        val refreshed = EpisodeLoader.loadHosterVideos(context, source, hoster, force = true, useCache = false)
        _hosterState.updateAt(hosterIdx, refreshed)

        val videoIdx = (refreshed as? HosterState.Ready)
            ?.videoList?.indexOfFirst { it.videoTitle == failedVideo.videoTitle }
            ?: -1
        return videoIdx != -1 && loadVideo(source, hosterIdx, videoIdx)
    }

    /**
     * Fetches the hosters of the current episode again, bypassing the cache.
     */
    fun reloadHostersWithoutCache() {
        reloadHosters(useCache = false)
    }

    /**
     * Saves the hoster order and the disabled hosters picked in the qualities sheet and applies
     * them to the current episode. Hosters of other episodes and sources keep their saved order.
     *
     * @param hosterNames every hoster name of the episode, highest priority first.
     */
    fun updateHosterPriority(hosterNames: List<String>, disabledNames: Set<String>) {
        val priority = playerPreferences.hosterPriority()
        val disabled = playerPreferences.disabledHosters()
        val savedOrder = HosterPriority.parseNames(priority.get())
        val savedDisabled = HosterPriority.parseNames(disabled.get())
        priority.set(HosterPriority.mergeOrder(savedOrder, hosterNames).joinToString(", "))
        disabled.set(
            HosterPriority.mergeDisabled(savedDisabled, hosterNames, disabledNames).joinToString(", "),
        )
        reloadHosters(useCache = true)
    }

    // A video that is playing fine keeps playing; otherwise the first working video of the new
    // list is started.
    private fun reloadHosters(useCache: Boolean) {
        val episode = currentEpisode.value?.toDomainEpisode() ?: return
        val anime = currentAnime.value ?: return
        val source = currentSource.value ?: return
        val keepPlayback = currentVideo.value != null && !isLoadingEpisode.value
        selectionToRestore = if (keepPlayback) selectedVideo() else null

        if (!useCache) EpisodeLoader.invalidateCache(context, episode, source)
        cancelHosterVideoLinksJob()
        updateIsLoadingHosters(true)

        viewModelScope.launchIO {
            val hosters = try {
                EpisodeLoader.getHosters(context, episode, anime, source, useCache = useCache)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Error reloading hosters" }
                emptyList()
            }
            updateIsLoadingHosters(false)

            if (hosters.isEmpty()) {
                eventChannel.trySend(Event.ShowToast(context.stringResource(AYMR.strings.no_hosters)))
                return@launchIO
            }

            currentHosterList = hosters
            // Indices of the old list don't apply to the new one.
            _selectedHosterVideoIndex.update { _ -> Pair(-1, -1) }
            loadHosters(source, hosters, hosterIndex = -1, videoIndex = -1, autoPlay = !keepPlayback)
        }
    }

    fun onVideoClicked(hosterIndex: Int, videoIndex: Int) {
        val hosterState = _hosterState.value.getOrNull(hosterIndex) as? HosterState.Ready ?: return
        if (videoIndex !in hosterState.videoList.indices) return

        // Failed videos stay clickable so they can be retried.
        viewModelScope.launchIO {
            val success = loadVideo(currentSource.value, hosterIndex, videoIndex)
            if (success) {
                if (sheetShown.value == Sheets.QualityTracks) {
                    dismissSheet()
                }
            } else {
                updateIsLoadingEpisode(false)
            }
        }
    }

    /**
     * Queues the current episode for download using the chosen video instead of letting the
     * downloader pick one.
     */
    fun onVideoDownloadClicked(hosterIndex: Int, videoIndex: Int) {
        val anime = currentAnime.value ?: return
        val episode = currentEpisode.value?.toDomainEpisode() ?: return
        val video = (_hosterState.value.getOrNull(hosterIndex) as? HosterState.Ready)
            ?.videoList?.getOrNull(videoIndex)
            ?: return

        // The downloader keeps a single download per episode and ignores the episode while it's
        // queued, so a failed download has to leave the queue before another video can be tried.
        val queuedDownload = downloadManager.getQueuedDownloadOrNull(episode.id)
        when {
            queuedDownload?.status == AnimeDownload.State.ERROR -> {
                downloadManager.cancelQueuedDownloads(listOf(queuedDownload))
            }

            queuedDownload != null -> {
                eventChannel.trySend(Event.ShowToast(context.stringResource(AYMR.strings.player_download_in_progress)))
                return
            }

            downloadManager.isEpisodeDownloaded(
                episode.name,
                episode.scanlator,
                episode.url,
                anime.title,
                anime.source,
            ) -> {
                eventChannel.trySend(Event.ShowToast(context.stringResource(AYMR.strings.player_download_already_done)))
                return
            }
        }

        viewModelScope.launchIO {
            val resolvedVideo = HosterLoader.getResolvedVideo(currentSource.value, video)
            if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
                eventChannel.trySend(Event.ShowToast(context.stringResource(AYMR.strings.no_available_videos)))
                return@launchIO
            }
            downloadManager.downloadEpisodes(anime, listOf(episode), video = resolvedVideo)
        }
    }

    fun cancelEpisodeDownload() {
        val episodeId = currentEpisode.value?.id ?: return
        val download = downloadManager.getQueuedDownloadOrNull(episodeId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(download))
    }

    fun onHosterClicked(index: Int) {
        val state = hosterState.value[index]
        when {
            state.canReload -> {
                pendingHosterIndices.remove(index)
                val hoster = hosterList.value[index]
                _hosterState.updateAt(index, HosterState.Loading(hoster.hosterName))

                viewModelScope.launchIO {
                    val hosterState = EpisodeLoader.loadHosterVideos(
                        context = context,
                        source = currentSource.value!!,
                        hoster = hoster,
                        force = true,
                    )
                    _hosterState.updateAt(index, hosterState)
                }
            }

            state is HosterState.Ready -> {
                _hosterExpandedList.updateAt(index, !_hosterExpandedList.value[index])
            }
        }
    }

    suspend fun loadThumbnails(video: Video, source: AnimeSource?) {
        if (source is AnimeHttpSource) {
            try {
                val thumbInfo = source.getVideoThumbnails(video)
                if (thumbInfo != null) {
                    thumbnailInfo.update { _ ->
                        ThumbnailInfo(
                            tileInfo = thumbInfo.tileInfo.sortedBy { it.timeMs },
                            imageTileUrls = thumbInfo.imageTileUrls,
                        )
                    }

                    // Preload first 2 tilemaps
                    thumbInfo.imageTileUrls.take(2).forEachIndexed { index, tileUrl ->
                        val bitmap = source.getImageTile(tileUrl)
                        if (bitmap != null) {
                            thumbnailTileCache[index] = bitmap
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to fetch thumbnails" }
            }
        }
    }

    private fun <T> MutableStateFlow<List<T>>.updateAt(index: Int, newValue: T) {
        this.update { values ->
            values.toMutableList().apply {
                this[index] = newValue
            }
        }
    }

    data class EpisodeLoadResult(
        val hosterList: List<Hoster>?,
        val episodeTitle: String,
        val source: AnimeSource,
    )

    suspend fun loadEpisode(episodeId: Long?): EpisodeLoadResult? {
        val anime = currentAnime.value ?: return null
        val source = sourceManager.getOrStub(anime.source)

        val chosenEpisode = currentPlaylist.value.firstOrNull { ep -> ep.id == episodeId } ?: return null

        currentEpisode.value?.takeIf { it.id != chosenEpisode.id }?.let(::clearVideoCacheIfSeen)
        _currentEpisode.update { _ -> chosenEpisode }
        updateEpisode(chosenEpisode)

        return withIOContext {
            try {
                val currentEpisode =
                    currentEpisode.value
                        ?: throw ExceptionWithStringResource("No episode loaded", AYMR.strings.no_episode_loaded)
                currentHosterList = EpisodeLoader.getHosters(
                    context,
                    currentEpisode.toDomainEpisode()!!,
                    anime,
                    source,
                )

                this@PlayerViewModel.episodeId = currentEpisode.id!!
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Keeping the previous list would play the last episode's hosters under this one.
                currentHosterList = null
                logcat(LogPriority.ERROR, e) { e.message ?: "Error getting links" }
            }

            EpisodeLoadResult(
                hosterList = currentHosterList,
                episodeTitle = anime.title + " - " + chosenEpisode.name,
                source = source,
            )
        }
    }

    /**
     * Called every time a second is reached in the player. Used to mark the flag of episode being
     * seen, update tracking services, enqueue downloaded episode deletion and download next episode.
     */
    private fun onSecondReached(position: Int, duration: Int) {
        if (isLoadingEpisode.value) return
        val currentEp = currentEpisode.value ?: return
        if (episodeId == -1L) return
        if (duration == 0) return

        val seconds = position * 1000L
        val totalSeconds = duration * 1000L
        // Save last second seen and mark as seen if needed
        currentEp.last_second_seen = seconds
        currentEp.total_seconds = totalSeconds

        episodePosition = seconds

        val progress = playerPreferences.progressPreference().get()
        val shouldTrack = !incognitoMode || hasTrackers
        if (seconds >= totalSeconds * progress && shouldTrack) {
            viewModelScope.launchNonCancellable {
                updateEpisodeProgressOnComplete(currentEp)
            }
        }

        saveWatchingProgress(currentEp)

        val inDownloadRange = seconds.toDouble() / totalSeconds > 0.35
        if (inDownloadRange) {
            downloadNextEpisodes()
        }
        if (seconds.toDouble() / totalSeconds > PRELOAD_NEXT_EPISODE_PROGRESS) {
            preloadNextEpisode()
        }
    }

    private var preloadedEpisodeId = -1L

    private fun preloadNextEpisode() {
        if (!playerPreferences.preloadNextEpisode().get()) return
        val nextId = getAdjacentEpisodeId(previous = false).takeIf { it != -1L } ?: return
        if (nextId == preloadedEpisodeId) return
        preloadedEpisodeId = nextId

        val nextEpisode = currentPlaylist.value.firstOrNull { it.id == nextId }?.toDomainEpisode() ?: return
        val anime = currentAnime.value ?: return
        val source = currentSource.value ?: return
        viewModelScope.launchIO {
            try {
                NextEpisodePreloader.preload(nextEpisode, anime, source)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Couldn't preload the next episode" }
            }
        }
    }

    private suspend fun updateEpisodeProgressOnComplete(currentEp: Episode) {
        if (isNetworkStreamSession) return
        currentEp.seen = true
        updateTrackEpisodeSeen(currentEp)
        deleteEpisodeIfNeeded(currentEp)

        val markDuplicateAsSeen = libraryPreferences.markDuplicateSeenEpisodeAsSeen.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_EPISODE_SEEN_EXISTING)
        if (!markDuplicateAsSeen) return

        val duplicateUnseenEpisodes = currentPlaylist.value
            .mapNotNull { episode ->
                if (
                    !episode.seen &&
                    episode.isRecognizedNumber &&
                    episode.episode_number == currentEp.episode_number
                ) {
                    EpisodeUpdate(id = episode.id!!, seen = true)
                } else {
                    null
                }
            }
        updateEpisode.awaitAll(duplicateUnseenEpisodes)
    }

    private fun downloadNextEpisodes() {
        if (isNetworkStreamSession || downloadAheadAmount == 0) return
        val anime = currentAnime.value ?: return

        // Only download ahead if current + next episode is already downloaded too to avoid jank
        if (getCurrentEpisodeIndex() == currentPlaylist.value.lastIndex) return
        val currentEpisode = currentEpisode.value ?: return

        val nextEpisode = currentPlaylist.value[getCurrentEpisodeIndex() + 1]
        val episodesAreDownloaded =
            EpisodeLoader.isDownload(context, currentEpisode.toDomainEpisode()!!, anime) &&
                EpisodeLoader.isDownload(context, nextEpisode.toDomainEpisode()!!, anime)

        viewModelScope.launchIO {
            if (!episodesAreDownloaded) {
                return@launchIO
            }
            val episodesToDownload = getNextEpisodes.await(anime.id, nextEpisode.id!!)
                .take(downloadAheadAmount)
            downloadManager.downloadEpisodes(anime, episodesToDownload)
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last episode actually exists.
     * If both conditions are satisfied enqueues episode for delete
     * @param chosenEpisode current episode, which is going to be marked as seen.
     */
    private fun deleteEpisodeIfNeeded(chosenEpisode: Episode) {
        if (isNetworkStreamSession) return
        // Determine which episode should be deleted and enqueue
        val currentEpisodePosition = currentPlaylist.value.indexOf(chosenEpisode)
        val removeAfterSeenSlots = downloadPreferences.removeAfterReadSlots.get()
        val episodeToDelete = currentPlaylist.value.getOrNull(
            currentEpisodePosition - removeAfterSeenSlots,
        )
        // If episode is completely seen no need to download it
        episodeToDownload = null

        // Check if deleting option is enabled and episode exists
        if (removeAfterSeenSlots != -1 && episodeToDelete != null) {
            enqueueDeleteSeenEpisodes(episodeToDelete)
        }
    }

    fun saveCurrentEpisodeWatchingProgress() {
        currentEpisode.value?.let { saveWatchingProgress(it) }
    }

    /**
     * Called when episode is changed in player or when activity is paused.
     */
    private fun saveWatchingProgress(episode: Episode) {
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(episode)
            saveEpisodeHistory(episode)
        }
    }

    /**
     * Saves this [episode] progress (last second seen and whether it's seen).
     * If incognito mode isn't on or has at least 1 tracker
     */
    private suspend fun saveEpisodeProgress(episode: Episode) {
        if (isNetworkStreamSession) return
        if (!incognitoMode || hasTrackers) {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episode.id!!,
                    seen = episode.seen,
                    bookmark = episode.bookmark,
                    fillermark = episode.fillermark,
                    lastSecondSeen = episode.last_second_seen,
                    totalSeconds = episode.total_seconds,
                ),
            )
        }
    }

    /**
     * Saves this [episode] last seen history if incognito mode isn't on.
     */
    private suspend fun saveEpisodeHistory(episode: Episode) {
        if (isNetworkStreamSession) return
        if (!incognitoMode) {
            val episodeId = episode.id!!
            val seenAt = Date()
            upsertHistory.await(
                AnimeHistoryUpdate(episodeId, seenAt),
            )
        }
    }

    /**
     * Bookmarks the currently active episode.
     */
    fun bookmarkEpisode(episodeId: Long?, bookmarked: Boolean) {
        if (isNetworkStreamSession) return
        viewModelScope.launchNonCancellable {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episodeId!!,
                    bookmark = bookmarked,
                ),
            )
        }
    }

    /**
     * Fillermarks the currently active episode.
     */
    fun fillermarkEpisode(episodeId: Long?, fillermarked: Boolean) {
        if (isNetworkStreamSession) return
        viewModelScope.launchNonCancellable {
            updateEpisode.await(
                EpisodeUpdate(
                    id = episodeId!!,
                    fillermark = fillermarked,
                ),
            )
        }
    }

    fun takeScreenshot(cachePath: String, showSubtitles: Boolean): InputStream? {
        val filename = cachePath + "/${System.currentTimeMillis()}_mpv_screenshot_tmp.png"
        val subtitleFlag = if (showSubtitles) "subtitles" else "video"

        mpv.command("screenshot-to-file", filename, subtitleFlag)
        val tempFile = File(filename).takeIf { it.exists() } ?: return null
        val newFile = File("$cachePath/mpv_screenshot.png")

        newFile.delete()
        tempFile.renameTo(newFile)
        return newFile.takeIf { it.exists() }?.inputStream()
    }

    /**
     * Saves the screenshot on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(imageStream: () -> InputStream, timePos: Int?) {
        val anime = currentAnime.value ?: return

        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(anime, seconds) ?: return

        // Pictures directory.
        val relativePath = DiskUtil.buildValidFilename(anime.title)

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Pictures(relativePath),
                    ),
                )
                notifier.onComplete(uri)
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
            } catch (e: Throwable) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    /**
     * Shares the screenshot and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(imageStream: () -> InputStream, timePos: Int?) {
        val anime = currentAnime.value ?: return

        val destDir = context.cacheImageDir

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(anime, seconds) ?: return

        try {
            viewModelScope.launchIO {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(Event.ShareImage(uri, seconds))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    /**
     * Sets the screenshot as art and notifies the UI of the result.
     */
    fun setAsArt(artType: ArtType, imageStream: () -> InputStream) {
        val anime = currentAnime.value ?: return
        val episode = currentEpisode.value ?: return

        viewModelScope.launchNonCancellable {
            val result = try {
                when (artType) {
                    ArtType.Cover -> anime.editCover(
                        localAnimeCoverManager,
                        imageStream(),
                        updateAnime,
                        coverCache,
                    )

                    ArtType.Background -> anime.editBackground(
                        localAnimeBackgroundManager,
                        imageStream(),
                        updateAnime,
                        backgroundCache,
                    )

                    ArtType.Thumbnail -> episode.editThumbnail(
                        anime,
                        localEpisodeThumbnailManager,
                        imageStream(),
                    )
                }

                if (anime.isLocal() || anime.favorite) {
                    SetAsArt.Success
                } else {
                    SetAsArt.AddToLibraryFirst
                }
            } catch (e: Exception) {
                SetAsArt.Error
            }
            eventChannel.send(Event.SetArtResult(result, artType))
        }
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    private fun updateTrackEpisodeSeen(episode: Episode) {
        if (basePreferences.incognitoMode.get() || !hasTrackers) return
        if (!trackPreferences.autoUpdateTrack.get()) return

        val anime = currentAnime.value ?: return

        viewModelScope.launchNonCancellable {
            trackEpisode.await(context, anime.id, episode.episode_number.toDouble())
        }
    }

    /**
     * Enqueues this [episode] to be deleted when [deletePendingEpisodes] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteSeenEpisodes(episode: Episode) {
        if (!episode.seen) return
        val anime = currentAnime.value ?: return
        viewModelScope.launchNonCancellable {
            downloadManager.enqueueEpisodesToDelete(listOf(episode.toDomainEpisode()!!), anime)
        }
    }

    /**
     * Deletes all the pending episodes. This operation will run in a background thread and errors
     * are ignored.
     */
    fun deletePendingEpisodes() {
        viewModelScope.launchNonCancellable {
            downloadManager.deletePendingEpisodes()
        }
    }

    /**
     * Returns the skipIntroLength used by this anime or the default one.
     */
    fun getAnimeSkipIntroLength(): Int {
        val default = gesturePreferences.defaultIntroLength().get()
        val anime = currentAnime.value ?: return default
        val skipIntroLength = anime.skipIntroLength
        val skipIntroDisable = anime.skipIntroDisable
        return when {
            skipIntroDisable -> 0
            skipIntroLength <= 0 -> default
            else -> anime.skipIntroLength
        }
    }

    /**
     * Updates the skipIntroLength for the open anime.
     */
    fun setAnimeSkipIntroLength(skipIntroLength: Long) {
        val anime = currentAnime.value ?: return
        if (!anime.favorite) return
        // Skip unnecessary database operation
        if (skipIntroLength == getAnimeSkipIntroLength().toLong()) return
        viewModelScope.launchIO {
            setAnimeViewerFlags.awaitSetSkipIntroLength(anime.id, skipIntroLength)
            _currentAnime.update { _ -> getAnime.await(anime.id) }
        }
    }

    /**
     * Generate a filename for the given [anime] and [timePos]
     */
    private fun generateFilename(
        anime: Anime,
        timePos: String,
    ): String? {
        val episode = currentEpisode.value ?: return null
        val filenameSuffix = " - $timePos"
        return DiskUtil.buildValidFilename(
            "${anime.title} - ${episode.name}".takeBytes(
                DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
            ),
        ) +
            filenameSuffix
    }

    /**
     * Returns the response of the AniSkipApi for this episode.
     * just works if tracking is enabled.
     */
    suspend fun aniSkipResponse(playerDuration: Int?): List<TimeStamp>? {
        val animeId = currentAnime.value?.id ?: return null
        var malId: Long?
        val episodeNumber = currentEpisode.value?.episode_number?.toInt() ?: return null
        if (getTracks.await(animeId).isEmpty()) {
            logcat { "AniSkip: No tracks found for anime $animeId" }
            return null
        }

        getTracks.await(animeId).map { track ->
            val tracker = trackerManager.get(track.trackerId)
            malId = when (tracker) {
                is MyAnimeList -> track.remoteId
                is Anilist -> AniSkipApi(json).getMalIdFromAL(track.remoteId)
                else -> null
            }
            val duration = playerDuration ?: return null
            return malId?.let {
                AniSkipApi(json).getResult(it.toInt(), episodeNumber, duration.toLong())
            }
        }
        return null
    }

    val introSkipEnabled = playerPreferences.enableSkipIntro().get()
    private val autoSkip = playerPreferences.autoSkipIntro().get()
    private val netflixStyle = playerPreferences.enableNetflixStyleIntroSkip().get()

    private val defaultWaitingTime = playerPreferences.waitingTimeIntroSkip().get()
    var waitingSkipIntro = defaultWaitingTime

    fun setChapter(position: Float) {
        getCurrentChapter(position)?.let { (chapterIndex, chapter) ->
            if (currentChapter.value != chapter) {
                _currentChapter.update { _ -> chapter }
            }

            if (!introSkipEnabled) {
                return
            }

            if (chapter.chapterType == ChapterType.Other) {
                _skipIntroText.update { _ -> null }
                waitingSkipIntro = defaultWaitingTime
            } else {
                val nextChapterPos = chapters.value.getOrNull(chapterIndex + 1)?.start ?: pos.value

                if (netflixStyle) {
                    // show a toast with the seconds before the skip
                    if (waitingSkipIntro == defaultWaitingTime) {
                        eventChannel.trySend(
                            Event.ShowToast(
                                "Skip Intro: ${context.stringResource(
                                    AYMR.strings.player_aniskip_dontskip_toast,
                                    chapter.name,
                                    waitingSkipIntro,
                                )}",
                            ),
                        )
                    }
                    showSkipIntroButton(chapter, nextChapterPos, waitingSkipIntro)
                    waitingSkipIntro--
                } else if (autoSkip) {
                    seekToWithText(
                        seekValue = nextChapterPos.toInt(),
                        text = context.stringResource(AYMR.strings.player_intro_skipped, chapter.name),
                    )
                } else {
                    updateSkipIntroButton(chapter.chapterType)
                }
            }
        }
    }

    private fun updateSkipIntroButton(chapterType: ChapterType) {
        val skipButtonString = chapterType.getStringRes()

        _skipIntroText.update { _ ->
            skipButtonString?.let {
                context.stringResource(
                    AYMR.strings.player_skip_action,
                    context.stringResource(skipButtonString),
                )
            }
        }
    }

    private fun showSkipIntroButton(chapter: IndexedSegment, nextChapterPos: Float, waitingTime: Int) {
        if (waitingTime > -1) {
            if (waitingTime > 0) {
                _skipIntroText.update { _ -> context.stringResource(AYMR.strings.player_aniskip_dontskip) }
            } else {
                seekToWithText(
                    seekValue = nextChapterPos.toInt(),
                    text = context.stringResource(AYMR.strings.player_aniskip_skip, chapter.name),
                )
            }
        } else {
            // when waitingTime is -1, it means that the user cancelled the skip
            updateSkipIntroButton(chapter.chapterType)
        }
    }

    fun onSkipIntro() {
        getCurrentChapter()?.let { (chapterIndex, chapter) ->
            // this stops the counter
            if (waitingSkipIntro > 0 && netflixStyle) {
                waitingSkipIntro = -1
                return
            }

            val nextChapterPos = chapters.value.getOrNull(chapterIndex + 1)?.start ?: pos.value

            seekToWithText(
                seekValue = nextChapterPos.toInt(),
                text = context.stringResource(AYMR.strings.player_aniskip_skip, chapter.name),
            )
        }
    }

    private fun getCurrentChapter(position: Float? = null): IndexedValue<IndexedSegment>? {
        return chapters.value.withIndex()
            .filter { it.value.start <= (position ?: pos.value) }
            .maxByOrNull { it.value.start }
    }

    fun setPrimaryCustomButtonTitle(button: CustomButton) {
        _primaryButtonTitle.update { _ -> button.name }
    }

    sealed class Event {
        data class SetArtResult(val result: SetAsArt, val artType: ArtType) : Event()
        data class SavedImage(val result: SaveImageResult) : Event()
        data class ShareImage(val uri: Uri, val seconds: String) : Event()
        data class ShowToast(val text: String) : Event()
        data class ChangeEpisode(val episodeId: Long, val autoPlay: Boolean) : Event()
        data class SetVideo(val video: Video) : Event()
        data class SetKeyboardVisibility(val visible: Boolean?) : Event()
    }
}

fun CustomButton.execute(mpv: MPV) {
    mpv.command("script-message", "call_button_$id")
}

fun CustomButton.executeLongPress(mpv: MPV) {
    mpv.command("script-message", "call_button_${id}_long")
}

fun Float.normalize(inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
    return (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
}
