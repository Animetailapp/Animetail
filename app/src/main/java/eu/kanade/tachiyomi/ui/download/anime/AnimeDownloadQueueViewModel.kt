package eu.kanade.tachiyomi.ui.download.anime

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.anime.videoLabel
import eu.kanade.tachiyomi.ui.download.DownloadQueueEntry
import eu.kanade.tachiyomi.ui.download.DownloadQueueSection
import eu.kanade.tachiyomi.ui.download.reconcileQueueOrder
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeDownloadQueueViewModel(
    private val context: Context,
    private val downloadManager: AnimeDownloadManager,
) : ViewModel() {
    val composeState: StateFlow<List<DownloadQueueSection>> = downloadManager.queueState
        .map { downloads ->
            downloads.groupBy { it.source }.map { (source, entries) ->
                DownloadQueueSection(source.id, source.name, entries.map(DownloadQueueEntry::Anime))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())

    val isDownloaderRunning = downloadManager.isDownloaderRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), false)

    /** Continue: failed downloads are retried too. */
    fun startDownloads() = downloadManager.retryFailedAndStartDownloads()

    fun retry(download: AnimeDownload) = downloadManager.retryDownloads(listOf(download))

    private val _otherVideos = MutableStateFlow<OtherVideos?>(null)

    /** The "Retry with another video" dialog, while it's open. */
    val otherVideos: StateFlow<OtherVideos?> = _otherVideos.asStateFlow()
    private var otherVideosJob: Job? = null

    /**
     * Lists the episode's videos for the "Retry with another video" dialog, loading every hoster,
     * lazy ones too. The videos that already failed for this download are left out.
     */
    fun showOtherVideos(download: AnimeDownload) {
        _otherVideos.value = OtherVideos(download, videos = null)
        otherVideosJob?.cancel()
        otherVideosJob = viewModelScope.launchIO {
            val result = try {
                val failed = download.failure?.failedVideos.orEmpty()
                val hosters = EpisodeLoader.getHosters(context, download.episode, download.anime, download.source)
                val videos = hosters
                    .map { async { EpisodeLoader.loadHosterVideos(context, download.source, it, force = true) } }
                    .awaitAll()
                    .filterIsInstance<HosterState.Ready>()
                    .flatMap { hoster ->
                        hoster.videoList.map { OtherVideo(videoLabel(hoster.name, it.videoTitle), it) }
                    }
                    .filterNot { it.label in failed }
                OtherVideos(download, videos = videos)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Could not list the videos of ${download.episode.name}" }
                OtherVideos(download, videos = emptyList(), error = e.message ?: e.javaClass.simpleName)
            }
            _otherVideos.update { current -> if (current?.download == download) result else current }
        }
    }

    fun retryWithVideo(video: OtherVideo) {
        val download = _otherVideos.value?.download ?: return
        dismissOtherVideos()
        // A copy: the download updates its video, which is shared with the hoster cache
        downloadManager.retryDownloads(listOf(download), video.video.copy())
    }

    fun dismissOtherVideos() {
        otherVideosJob?.cancel()
        _otherVideos.value = null
    }

    data class OtherVideos(
        val download: AnimeDownload,
        /** Null while loading. */
        val videos: List<OtherVideo>?,
        val error: String? = null,
    )

    data class OtherVideo(val label: String, val video: Video)

    fun pauseDownloads() = downloadManager.pauseDownloads()

    fun clearQueue() = downloadManager.clearQueue()

    fun reorder(downloads: List<AnimeDownload>) {
        val current = downloadManager.queueState.value
        val reordered = reconcileQueueOrder(downloads, current) { it.episode.id }
        if (reordered != current) downloadManager.reorderQueue(reordered)
    }

    fun cancel(downloads: List<AnimeDownload>) = downloadManager.cancelQueuedDownloads(downloads)

    fun move(download: AnimeDownload, toTop: Boolean) {
        reorder(
            composeState.value.flatMap { section ->
                val downloads = section.downloads.map { (it as DownloadQueueEntry.Anime).download }
                if (download in downloads) {
                    val others = downloads - download
                    if (toTop) listOf(download) + others else others + download
                } else {
                    downloads
                }
            },
        )
    }

    fun moveSeries(download: AnimeDownload, toTop: Boolean) {
        val (selected, others) = composeState.value.flatMap { section ->
            section.downloads.map { (it as DownloadQueueEntry.Anime).download }
        }.partition { it.anime.id == download.anime.id }
        reorder(if (toTop) selected + others else others + selected)
    }

    fun cancelSeries(download: AnimeDownload) {
        cancel(downloadManager.queueState.value.filter { it.anime.id == download.anime.id })
    }

    fun <R : Comparable<R>> reorderComposeQueue(selector: (AnimeDownload) -> R, reverse: Boolean = false) {
        reorder(
            composeState.value.flatMap { section ->
                section.downloads.map { (it as DownloadQueueEntry.Anime).download }
                    .sortedBy(selector).let { if (reverse) it.reversed() else it }
            },
        )
    }
}
