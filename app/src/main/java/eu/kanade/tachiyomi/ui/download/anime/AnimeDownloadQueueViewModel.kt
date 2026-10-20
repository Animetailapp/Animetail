package eu.kanade.tachiyomi.ui.download.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.ui.download.DownloadQueueEntry
import eu.kanade.tachiyomi.ui.download.DownloadQueueSection
import eu.kanade.tachiyomi.ui.download.reconcileQueueOrder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeDownloadQueueViewModel(
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

    fun startDownloads() = downloadManager.startDownloads()

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
