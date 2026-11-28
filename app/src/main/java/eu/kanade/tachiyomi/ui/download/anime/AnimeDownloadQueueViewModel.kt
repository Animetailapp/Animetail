package eu.kanade.tachiyomi.ui.download.anime

import android.os.SystemClock
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
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

    /** Smoothed rates of the downloads transferring, by episode id; only touched by [queueProgress]. */
    private val estimates = HashMap<Long, TransferEstimate>()

    private data class QueueItem(
        val download: AnimeDownload,
        val status: AnimeDownload.State,
        val active: Boolean,
        val transfer: AnimeDownload.Transfer?,
    )

    private val queueItems = downloadManager.queueState.flatMapLatest { downloads ->
        if (downloads.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                downloads.map { download ->
                    combine(download.statusFlow, download.activeFlow, download.transferFlow) { s, a, t ->
                        QueueItem(download, status = s, active = a, transfer = t)
                    }
                },
            ) { it.toList() }
        }
    }

    /** Ticks so a download that stops reporting shows as stalled. */
    private val clock = flow {
        while (true) {
            emit(SystemClock.elapsedRealtime())
            delay(1.seconds)
        }
    }

    /** What each row and the header of the queue show; recomputed on every progress report. */
    val queueProgress: StateFlow<QueueProgress> = combine(
        queueItems,
        downloadManager.isDownloaderRunning,
        clock,
    ) { items, running, now ->
        val states = items.associate { item ->
            val id = item.download.episode.id
            val estimate = item.transfer?.let { sample ->
                val previous = estimates[id]
                if (previous?.sample == sample) previous else TransferEstimator.update(previous, sample)
            }
            if (estimate == null) estimates.remove(id) else estimates[id] = estimate
            id to AnimeDownloadItemState.of(
                status = item.status,
                active = item.active,
                estimate = estimate,
                downloaderRunning = running,
                nowMs = now,
            )
        }
        estimates.keys.retainAll(states.keys)
        QueueProgress(states, AnimeDownloadQueueSummary.of(states.values, running))
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), QueueProgress())

    data class QueueProgress(
        val items: Map<Long, AnimeDownloadItemState> = emptyMap(),
        val summary: AnimeDownloadQueueSummary? = null,
    )

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
