package eu.kanade.tachiyomi.ui.updates.anime

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.updates.anime.AnimeUpdatesUiModel
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class AnimeUpdatesViewModel(
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val downloadCache: AnimeDownloadCache = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val getUpdates: GetAnimeUpdates = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getEpisode: GetEpisode = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    downloadPreferences: DownloadPreferences = Injekt.get(),
) : ViewModel() {

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events: Flow<Event> = _events.receiveAsFlow()

    val lastUpdated by libraryPreferences.lastUpdatedTimestamp.asState(viewModelScope)

    val useExternalDownloader = downloadPreferences.useExternalDownloader.get()

    // First and last selected index in list
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedEpisodeIds = MutableStateFlow(emptySet<Long>())

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val downloadStates = MutableStateFlow(emptyMap<Long, DownloadProgress>())

    init {
        viewModelScope.launchIO {
            merge(downloadManager.statusFlow(), downloadManager.progressFlow())
                .catch { logcat(LogPriority.ERROR, it) }
                .collect(this@AnimeUpdatesViewModel::updateDownloadState)
        }
    }

    private fun updateDownloadState(download: AnimeDownload) {
        val episodeId = download.episode.id
        downloadStates.update {
            if (download.status == AnimeDownload.State.NOT_DOWNLOADED ||
                download.status == AnimeDownload.State.DOWNLOADED
            ) {
                it - episodeId
            } else {
                it + (episodeId to DownloadProgress(download.status, download.progress))
            }
        }
    }

    private val updateItems = combine(
        getUpdates.subscribe(
            Clock.System.now().minus(3, DateTimeUnit.MONTH, TimeZone.currentSystemDefault()),
        ).distinctUntilChanged(),
        downloadCache.changes,
        downloadManager.queueState,
    ) { updates, _, _ ->
        updates.toUpdateItems()
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    val state: StateFlow<State> = combine(
        updateItems,
        selectedEpisodeIds,
        downloadStates,
        dialog,
    ) { items, selectedIds, downloads, dialog ->
        State(
            isLoading = items == null,
            items = items.orEmpty().map { item ->
                val download = downloads[item.update.episodeId]
                item.copy(
                    selected = item.update.episodeId in selectedIds,
                    downloadStateProvider = if (download != null) {
                        { download.status }
                    } else {
                        item.downloadStateProvider
                    },
                    downloadProgressProvider = if (download != null) {
                        { download.progress }
                    } else {
                        item.downloadProgressProvider
                    },
                )
            },
            dialog = dialog,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    private fun List<AnimeUpdatesWithRelations>.toUpdateItems(): List<AnimeUpdatesItem> {
        return this
            .map { update ->
                val activeDownload = downloadManager.getQueuedDownloadOrNull(update.episodeId)
                val downloaded = downloadManager.isEpisodeDownloaded(
                    update.episodeName,
                    update.scanlator,
                    update.episodeUrl,
                    update.animeTitle,
                    update.sourceId,
                )
                val downloadState = when {
                    activeDownload != null -> activeDownload.status
                    downloaded -> AnimeDownload.State.DOWNLOADED
                    else -> AnimeDownload.State.NOT_DOWNLOADED
                }
                AnimeUpdatesItem(
                    update = update,
                    downloadStateProvider = { downloadState },
                    downloadProgressProvider = { activeDownload?.progress ?: 0 },
                    selected = false,
                    // AM (FILE_SIZE) -->
                    fileSize = null,
                    // <-- AM (FILE_SIZE)
                )
            }
    }

    fun updateLibrary(): Boolean {
        val started = AnimeLibraryUpdateJob.startNow(Injekt.get<Application>())
        viewModelScope.launch {
            _events.send(Event.LibraryUpdateTriggered(started))
        }
        return started
    }

    fun downloadEpisodes(items: List<AnimeUpdatesItem>, action: EpisodeDownloadAction) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            when (action) {
                EpisodeDownloadAction.START -> {
                    downloadEpisodes(items)
                    if (items.any { it.downloadStateProvider() == AnimeDownload.State.ERROR }) {
                        downloadManager.startDownloads()
                    }
                }

                EpisodeDownloadAction.START_NOW -> {
                    val episodeId = items.singleOrNull()?.update?.episodeId ?: return@launch
                    startDownloadingNow(episodeId)
                }

                EpisodeDownloadAction.CANCEL -> {
                    val episodeId = items.singleOrNull()?.update?.episodeId ?: return@launch
                    cancelDownload(episodeId)
                }

                EpisodeDownloadAction.DELETE -> {
                    deleteEpisodes(items)
                }

                EpisodeDownloadAction.SHOW_QUALITIES -> {
                    val update = items.singleOrNull()?.update ?: return@launch
                    showQualitiesDialog(update)
                }
            }
            toggleAllSelection(false)
        }
    }

    private suspend fun startDownloadingNow(episodeId: Long) {
        downloadManager.startDownloadNow(episodeId)
    }

    private fun cancelDownload(episodeId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(episodeId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = AnimeDownload.State.NOT_DOWNLOADED })
    }

    /**
     * Mark the selected updates list as seen/unseen.
     * @param updates the list of selected updates.
     * @param seen whether to mark episodes as seen or unseen.
     */
    fun markUpdatesSeen(updates: List<AnimeUpdatesItem>, seen: Boolean) {
        viewModelScope.launchIO {
            setSeenStatus.await(
                seen = seen,
                episodes = updates
                    .mapNotNull { getEpisode.await(it.update.episodeId) }
                    .toTypedArray(),
            )
        }
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of episodes.
     * @param updates the list of episodes to bookmark.
     */
    fun bookmarkUpdates(updates: List<AnimeUpdatesItem>, bookmark: Boolean) {
        viewModelScope.launchIO {
            updates
                .filterNot { it.update.bookmark == bookmark }
                .map { EpisodeUpdate(id = it.update.episodeId, bookmark = bookmark) }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Fillermarks the given list of episodes.
     * @param updates the list of episodes to fillermark.
     */
    fun fillermarkUpdates(updates: List<AnimeUpdatesItem>, fillermark: Boolean) {
        viewModelScope.launchIO {
            updates
                .filterNot { it.update.fillermark == fillermark }
                .map { EpisodeUpdate(id = it.update.episodeId, fillermark = fillermark) }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Downloads the given list of episodes with the manager.
     * @param updatesItem the list of episodes to download.
     */
    private fun downloadEpisodes(updatesItem: List<AnimeUpdatesItem>, alt: Boolean = false) {
        viewModelScope.launchNonCancellable {
            val groupedUpdates = updatesItem.groupBy { it.update.animeId }.values
            for (updates in groupedUpdates) {
                val animeId = updates.first().update.animeId
                val anime = getAnime.await(animeId) ?: continue
                // Don't download if source isn't available
                sourceManager.get(anime.source) ?: continue
                val episodes = updates.mapNotNull { getEpisode.await(it.update.episodeId) }
                downloadManager.downloadEpisodes(anime, episodes, true, alt)
            }
        }
    }

    /**
     * Delete selected episodes
     *
     * @param updatesItem list of episodes
     */
    fun deleteEpisodes(updatesItem: List<AnimeUpdatesItem>) {
        viewModelScope.launchNonCancellable {
            updatesItem
                .groupBy { it.update.animeId }
                .entries
                .forEach { (animeId, updates) ->
                    val anime = getAnime.await(animeId) ?: return@forEach
                    val source = sourceManager.get(anime.source) ?: return@forEach
                    val episodes = updates.mapNotNull { getEpisode.await(it.update.episodeId) }
                    downloadManager.deleteEpisodes(episodes, anime, source)
                }
        }
        toggleAllSelection(false)
    }

    fun showConfirmDeleteEpisodes(updatesItem: List<AnimeUpdatesItem>) {
        setDialog(Dialog.DeleteConfirmation(updatesItem))
    }

    private fun showQualitiesDialog(update: AnimeUpdatesWithRelations) {
        setDialog(
            Dialog.ShowQualities(
                update.episodeName,
                update.episodeId,
                update.animeId,
                update.sourceId,
            ),
        )
    }

    fun toggleSelection(
        item: AnimeUpdatesItem,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        val items = state.value.items
        val selectedIndex = items.indexOfFirst { it.update.episodeId == item.update.episodeId }
        if (selectedIndex < 0) return

        val currentSelection = selectedEpisodeIds.value
        if ((item.update.episodeId in currentSelection) == selected) return

        val firstSelection = items.none { it.selected }
        val newSelection = currentSelection.toHashSet()
        newSelection.addOrRemove(item.update.episodeId, selected)

        if (selected && (userSelected || true) && fromLongPress) {
            if (firstSelection) {
                selectedPositions[0] = selectedIndex
                selectedPositions[1] = selectedIndex
            } else {
                val range: IntRange
                if (selectedIndex < selectedPositions[0]) {
                    range = selectedIndex + 1..<selectedPositions[0]
                    selectedPositions[0] = selectedIndex
                } else if (selectedIndex > selectedPositions[1]) {
                    range = (selectedPositions[1] + 1)..<selectedIndex
                    selectedPositions[1] = selectedIndex
                } else {
                    range = IntRange.EMPTY
                }

                range.forEach { newSelection.add(items[it].update.episodeId) }
            }
        } else if (!fromLongPress) {
            if (!selected) {
                if (selectedIndex == selectedPositions[0]) {
                    selectedPositions[0] = items.indexOfFirst { it.update.episodeId in newSelection }
                } else if (selectedIndex == selectedPositions[1]) {
                    selectedPositions[1] = items.indexOfLast { it.update.episodeId in newSelection }
                }
            } else {
                if (selectedIndex < selectedPositions[0]) {
                    selectedPositions[0] = selectedIndex
                } else if (selectedIndex > selectedPositions[1]) {
                    selectedPositions[1] = selectedIndex
                }
            }
        }

        selectedEpisodeIds.update { newSelection }
    }

    fun toggleAllSelection(selected: Boolean) {
        val ids = if (selected) state.value.items.map { it.update.episodeId }.toSet() else emptySet()
        selectedEpisodeIds.update { ids }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun invertSelection() {
        val current = selectedEpisodeIds.value
        val ids = state.value.items
            .map { it.update.episodeId }
            .filterNot { it in current }
            .toSet()
        selectedEpisodeIds.update { ids }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun setDialog(dialog: Dialog?) {
        this.dialog.update { dialog }
    }

    fun resetNewUpdatesCount() {
        libraryPreferences.newAnimeUpdatesCount.set(0)
    }

    private data class DownloadProgress(val status: AnimeDownload.State, val progress: Int)

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: List<AnimeUpdatesItem> = listOf(),
        val dialog: Dialog? = null,
    ) {
        val selected = items.filter { it.selected }
        val selectionMode = selected.isNotEmpty()

        fun getUiModel(): List<AnimeUpdatesUiModel> {
            return items
                .map { AnimeUpdatesUiModel.Item(it) }
                .insertSeparators { before, after ->
                    val beforeDate = before?.item?.update?.dateFetch?.toLocalDate()
                    val afterDate = after?.item?.update?.dateFetch?.toLocalDate()
                    when {
                        beforeDate != afterDate && afterDate != null -> AnimeUpdatesUiModel.Header(afterDate)

                        // Return null to avoid adding a separator between two items.
                        else -> null
                    }
                }
        }
    }

    sealed interface Dialog {
        data class DeleteConfirmation(val toDelete: List<AnimeUpdatesItem>) : Dialog
        data class ShowQualities(
            val episodeTitle: String,
            val episodeId: Long,
            val animeId: Long,
            val sourceId: Long,
        ) : Dialog
    }

    sealed interface Event {
        data object InternalError : Event
        data class LibraryUpdateTriggered(val started: Boolean) : Event
    }
}

@Immutable
data class AnimeUpdatesItem(
    val update: AnimeUpdatesWithRelations,
    val downloadStateProvider: () -> AnimeDownload.State,
    val downloadProgressProvider: () -> Int,
    val selected: Boolean = false,
    // AM (FILE_SIZE) -->
    var fileSize: Long?,
    // <-- AM (FILE_SIZE)
)
