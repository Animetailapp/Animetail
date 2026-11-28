package eu.kanade.tachiyomi.ui.storage.anime

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadSizes
import eu.kanade.tachiyomi.data.download.anime.AnimeStorageCleaner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.download.anime.interactor.GetAnimeDownloadPolicy
import tachiyomi.domain.download.anime.interactor.SetAnimeDownloadPolicy
import tachiyomi.domain.download.anime.model.AnimeDownloadPolicy
import tachiyomi.domain.download.anime.model.AnimeDownloadPolicyOverride
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.service.getEpisodeSort
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * The downloaded episodes of one show in the storage screen, with their sizes, and the show's
 * cleanup policy.
 */
@AssistedInject
class AnimeStorageDetailViewModel(
    @Assisted private val sourceIds: Set<Long>,
    @Assisted private val dirName: String,
    private val sizes: AnimeDownloadSizes,
    private val cleaner: AnimeStorageCleaner,
    private val downloadManager: AnimeDownloadManager,
    private val provider: AnimeDownloadProvider,
    private val sourceManager: AnimeSourceManager,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val getAnimeDownloadPolicy: GetAnimeDownloadPolicy,
    private val setAnimeDownloadPolicy: SetAnimeDownloadPolicy,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow(State())

    val policyDialog: StateFlow<PolicyDialog?>
        field = MutableStateFlow<PolicyDialog?>(null)

    private var anime: Anime? = null
    private var categoryIds: List<Long> = emptyList()
    private var episodes: List<Episode> = emptyList()
    private var previewJob: Job? = null

    init {
        sizes.refresh()
        viewModelScope.launchIO {
            val first = sizes.state.first { it.findShow() != null || (it.scanned && !it.measuring) }.findShow()
            if (first != null) {
                val downloaded = cleaner.matchDownloads(listOf(first)).single()
                anime = downloaded.anime
                categoryIds = downloaded.categoryIds
                episodes = downloaded.anime
                    ?.let { getEpisodesByAnimeId.await(it.id).sortedWith(getEpisodeSort(it, sortDescending = false)) }
                    .orEmpty()
            }
            sizes.state.collect { sizeState ->
                val show = sizeState.findShow()
                if (show == null) {
                    // Everything was deleted, or the directory is gone
                    if (!sizeState.measuring) state.update { it.copy(loading = false, show = null, gone = true) }
                } else {
                    state.update { it.copy(loading = false, anime = anime, show = show, rows = rowsOf(show)) }
                }
            }
        }
    }

    private fun AnimeDownloadSizes.State.findShow(): AnimeDownloadSizes.Show? {
        return shows.find { it.dirName == dirName && it.sourceIds == sourceIds }
    }

    private fun rowsOf(show: AnimeDownloadSizes.Show): List<EpisodeRow> {
        val entries = show.entries.associateBy { it.name }
        val matchedNames = HashSet<String>()
        val rows = episodes.mapNotNull { episode ->
            val entry = provider.getValidEpisodeDirNames(episode.name, episode.scanlator, episode.url)
                .firstNotNullOfOrNull { entries[it] } ?: return@mapNotNull null
            matchedNames += entry.name
            EpisodeRow(entry, episode)
        }
        return rows + show.entries.filter { it.name !in matchedNames }.map { EpisodeRow(it, null) }
    }

    fun deleteEpisode(row: EpisodeRow) {
        val show = state.value.show ?: return
        viewModelScope.launchNonCancellable {
            downloadManager.deleteDownloadedEntries(show, listOf(row.entry.name))
        }
    }

    fun deleteAll() {
        val show = state.value.show ?: return
        viewModelScope.launchNonCancellable {
            val anime = anime
            val source = anime?.let { sourceManager.get(it.source) }
            if (anime != null && source != null) {
                downloadManager.deleteAnime(anime, source)
                sizes.onEntriesDeleted(show.sourceIds, show.dirName, entryNames = null)
            } else {
                downloadManager.deleteDownloadedEntries(show, entryNames = null)
            }
        }
    }

    fun openPolicy() {
        val anime = anime ?: return
        viewModelScope.launchIO {
            val override = getAnimeDownloadPolicy.awaitOverride(anime.id)
                ?: AnimeDownloadPolicyOverride(anime.id, deleteAfterSeen = null, keepUnseen = null)
            val default = AnimeDownloadPolicy.resolve(getAnimeDownloadPolicy.global(), null, categoryIds)
            policyDialog.value = PolicyDialog(override = override, default = default, preview = null)
            updatePreview(override)
        }
    }

    fun changePolicy(override: AnimeDownloadPolicyOverride) {
        policyDialog.update { it?.copy(override = override, preview = null) }
        updatePreview(override)
    }

    private fun updatePreview(override: AnimeDownloadPolicyOverride) {
        val anime = anime ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launchIO {
            val preview = cleaner.previewShow(anime, state.value.show, override, categoryIds)
            policyDialog.update { dialog ->
                dialog?.takeIf { it.override == override }?.copy(preview = preview)
                    ?: dialog
            }
        }
    }

    /** Saves the policy and applies what its preview showed. */
    fun savePolicy() {
        val dialog = policyDialog.value ?: return
        val preview = dialog.preview ?: return
        policyDialog.value = null
        viewModelScope.launchNonCancellable {
            setAnimeDownloadPolicy.await(dialog.override)
            cleaner.apply(preview)
        }
    }

    fun dismissPolicy() {
        previewJob?.cancel()
        policyDialog.value = null
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val anime: Anime? = null,
        val show: AnimeDownloadSizes.Show? = null,
        val rows: List<EpisodeRow> = emptyList(),
        val gone: Boolean = false,
    )

    /**
     * A downloaded episode, and its episode in the database when it matches one.
     */
    @Immutable
    data class EpisodeRow(
        val entry: AnimeDownloadSizes.Entry,
        val episode: Episode?,
    )

    /**
     * @property default the policy the show has when following the global settings.
     * @property preview what saving [override] deletes and downloads now, null while worked out.
     */
    @Immutable
    data class PolicyDialog(
        val override: AnimeDownloadPolicyOverride,
        val default: AnimeDownloadPolicy,
        val preview: AnimeStorageCleaner.CleanupPreview?,
    )

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceIds: Set<Long>, dirName: String): AnimeStorageDetailViewModel
    }
}
