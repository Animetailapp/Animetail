package eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

abstract class MangaSearchViewModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val extensionManager: MangaExtensionManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow<State>(initialState)

    // Subclasses can't touch the backing field (Kotlin forbids a visibility modifier on one),
    // so state writes from them go through here.
    protected fun updateState(function: (State) -> State) {
        state.update(function)
    }

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages.get()
    private val disabledSources = sourcePreferences.disabledMangaSources.get()
    protected val pinnedSources = sourcePreferences.pinnedMangaSources.get()

    private var lastQuery: String? = null
    private var lastSourceFilter: MangaSourceFilter? = null

    protected var extensionFilter: String? = null

    private val sortComparator = { map: Map<CatalogueSource, MangaSearchItemResult> ->
        compareBy<CatalogueSource>(
            { (map[it] as? MangaSearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    init {
        viewModelScope.launch {
            preferences.globalSearchFilterState.changes().collectLatest { onlyShowHasResults ->
                state.update { it.copy(onlyShowHasResults = onlyShowHasResults) }
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    open fun getEnabledSources(): List<CatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    private fun getSelectedSources(): List<CatalogueSource> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        return extensionManager.installedExtensionsFlow.value
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .filterIsInstance<CatalogueSource>()
            .filter { it in enabledSources }
    }

    fun updateSearchQuery(query: String?) {
        state.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: MangaSourceFilter) {
        state.update { it.copy(sourceFilter = filter) }
        search()
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState.toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return
        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter

        searchJob?.cancel()
        val sources = getSelectedSources()

        // Reuse previous results if possible
        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: MangaSearchItemResult.Loading }
                    .toMap(),
            )
        } else {
            updateItems(
                sources
                    .associateWith { MangaSearchItemResult.Loading }
                    .toMap(),
            )
        }

        searchJob = viewModelScope.launchIO {
            sources.map { source ->
                async {
                    if (state.value.items[source] !is MangaSearchItemResult.Loading) {
                        return@async
                    }
                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchManga(1, query, source.getFilterList())
                        }

                        val titles = page.mangas.map {
                            networkToLocalManga.await(it.toDomainManga(source.id))
                        }

                        if (isActive) {
                            updateItem(source, MangaSearchItemResult.Success(titles))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, MangaSearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
    }

    private fun updateItems(items: Map<CatalogueSource, MangaSearchItemResult>) {
        state.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toMap(),
            )
        }
    }

    private fun updateItem(source: CatalogueSource, result: MangaSearchItemResult) {
        val newItems = state.value.items.toMutableMap().apply {
            this[source] = result
        }
        updateItems(newItems)
    }

    fun setMigrateDialog(currentId: Long, target: Manga) {
        viewModelScope.launchIO {
            val current = getManga.await(currentId) ?: return@launchIO
            state.update { it.copy(dialog = Dialog.Migrate(target, current)) }
        }
    }

    fun clearDialog() {
        state.update { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val fromSourceId: Long? = null,
        val searchQuery: String? = null,
        val sourceFilter: MangaSourceFilter = MangaSourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: Map<CatalogueSource, MangaSearchItemResult> = mapOf(),
        val dialog: Dialog? = null,
    ) {
        val progress: Int = items.count { it.value !is MangaSearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
    }

    sealed interface Dialog {
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }
}

enum class MangaSourceFilter {
    All,
    PinnedOnly,
}

sealed interface MangaSearchItemResult {
    data object Loading : MangaSearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : MangaSearchItemResult

    data class Success(
        val result: List<Manga>,
    ) : MangaSearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
