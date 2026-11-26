package eu.kanade.tachiyomi.ui.storage.anime

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.presentation.more.storage.StorageItem
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadSizes
import eu.kanade.tachiyomi.data.download.anime.AnimeStorageCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import kotlin.random.Random

/**
 * The anime tab of the storage screen: what the anime downloads take, by show, and cleanup.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeStorageViewModel(
    downloadCache: AnimeDownloadCache,
    private val sizes: AnimeDownloadSizes,
    private val cleaner: AnimeStorageCleaner,
    private val downloadManager: AnimeDownloadManager,
    private val provider: AnimeDownloadProvider,
    private val sourceManager: AnimeSourceManager,
    getLibraryAnime: GetLibraryAnime,
    getCategories: GetAnimeCategories,
    getVisibleCategories: GetVisibleAnimeCategories,
    libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow(State())

    val cleanup: StateFlow<CleanupState?>
        field = MutableStateFlow<CleanupState?>(null)

    private val selectedCategory = MutableStateFlow(ALL_CATEGORY)

    init {
        // Downloads added or deleted anywhere: measure the new ones
        downloadCache.changes
            .onEach { sizes.refresh() }
            .launchIn(viewModelScope)

        val categories = if (libraryPreferences.hideHiddenCategoriesSettings.get()) {
            getVisibleCategories.subscribe()
        } else {
            getCategories.subscribe()
        }

        // Matching directories with anime only needs redoing when directories or the library change
        val matched = combine(
            sizes.state.map { state -> state.shows.map { it.sourceIds to it.dirName } }.distinctUntilChanged(),
            getLibraryAnime.subscribe(),
        ) { _, _ -> }
            .mapLatest {
                cleaner.matchDownloads(sizes.state.value.shows)
                    .associate { (it.show.sourceIds to it.show.dirName) to (it.anime to it.categoryIds) }
            }

        combine(
            sizes.state,
            matched,
            categories,
            selectedCategory,
            downloadPreferences.animeDownloadStorageLimitGb.changes(),
        ) { sizeState, matches, categories, selected, limitGb ->
            val rows = sizeState.shows.map { show ->
                val (anime, categoryIds) = matches[show.sourceIds to show.dirName] ?: (null to emptyList())
                Row(show, anime, categoryIds)
            }
            val visible = rows
                .filter { selected.id == ALL_CATEGORY.id || selected.id in it.categoryIds }
                .sortedByDescending { it.show.size }
            State(
                loading = false,
                selectedCategory = selected,
                categories = listOf(ALL_CATEGORY) + categories,
                rows = visible,
                usedBytes = sizeState.totalSize,
                freeBytes = provider.getAvailableSpace(),
                volumeBytes = provider.getTotalSpace(),
                measuring = sizeState.measuring,
                measured = sizeState.measured,
                toMeasure = sizeState.toMeasure,
                limitBytes = limitGb * AnimeStorageCleaner.BYTES_PER_GB,
                limitApplied = limitGb > 0 && downloadPreferences.animeDownloadStorageLimitAppliedGb.get() == limitGb,
            )
        }
            .flowOn(Dispatchers.IO)
            .onEach { state.value = it }
            .launchIn(viewModelScope)

        sizes.refresh()
    }

    fun setSelectedCategory(category: Category) {
        selectedCategory.update { category }
    }

    fun recalculate() {
        sizes.refresh(force = true)
    }

    /** Deletes every download of the show in [row]. */
    fun deleteShow(row: Row) {
        viewModelScope.launchNonCancellable {
            val anime = row.anime
            val source = anime?.let { sourceManager.get(it.source) }
            if (anime != null && source != null) {
                downloadManager.deleteAnime(anime, source)
            } else {
                downloadManager.deleteDownloadedEntries(row.show, entryNames = null)
            }
        }
    }

    fun openCleanup() {
        cleanup.value = CleanupState.Loading
        viewModelScope.launchIO {
            cleanup.value = CleanupState.Ready(cleaner.preview())
        }
    }

    fun applyCleanup() {
        val ready = cleanup.value as? CleanupState.Ready ?: return
        cleanup.value = null
        cleaner.apply(ready.preview)
    }

    fun dismissCleanup() {
        cleanup.value = null
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val selectedCategory: Category = ALL_CATEGORY,
        val categories: List<Category> = emptyList(),
        val rows: List<Row> = emptyList(),
        val usedBytes: Long = 0,
        val freeBytes: Long = -1,
        val volumeBytes: Long = -1,
        val measuring: Boolean = false,
        val measured: Int = 0,
        val toMeasure: Int = 0,
        val limitBytes: Long = 0,
        val limitApplied: Boolean = false,
    ) {
        /** Over the storage limit, but it isn't enforced until its cleanup has been reviewed. */
        val needsLimitReview: Boolean
            get() = limitBytes > 0 && !limitApplied && usedBytes > limitBytes
    }

    @Immutable
    data class Row(
        val show: AnimeDownloadSizes.Show,
        val anime: Anime?,
        val categoryIds: List<Long>,
    ) {
        /** Stable id for the list: the anime's, or one made up from the directory. */
        val id: Long = anime?.id ?: -(show.dirName.hashCode().toLong() and 0x7fffffff) - 1

        val title: String
            get() = anime?.title ?: show.dirName

        val item: StorageItem
            get() {
                val random = Random(id)
                return StorageItem(
                    id = id,
                    title = title,
                    size = show.size,
                    thumbnail = anime?.thumbnailUrl,
                    entriesCount = show.entries.size,
                    color = Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)),
                )
            }
    }

    sealed interface CleanupState {
        data object Loading : CleanupState

        data class Ready(val preview: AnimeStorageCleaner.CleanupPreview) : CleanupState
    }

    companion object {
        /** A dummy category used to display all entries irrespective of the category. */
        val ALL_CATEGORY = Category(
            id = -1L,
            name = "All",
            order = 0L,
            flags = 0L,
            hidden = false,
        )
    }
}
