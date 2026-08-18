package eu.kanade.tachiyomi.ui.browse.anime.migration.anime

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
import eu.kanade.tachiyomi.animesource.AnimeSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class MigrateAnimeViewModel(
    @Assisted private val sourceId: Long,
    private val sourceManager: AnimeSourceManager,
    private val getFavorites: GetAnimeFavorites,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long): MigrateAnimeViewModel
    }

    private val _events: Channel<MigrationAnimeEvent> = Channel()
    val events: Flow<MigrationAnimeEvent> = _events.receiveAsFlow()

    private val source by lazy { sourceManager.getOrStub(sourceId) }

    private val selection = MutableStateFlow(emptySet<Long>())

    private val favorites = getFavorites.subscribe(sourceId)
        .catch {
            logcat(LogPriority.ERROR, it)
            _events.send(MigrationAnimeEvent.FailedFetchingFavorites)
            emit(listOf())
        }
        .map { anime ->
            anime
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                .toImmutableList()
        }

    val state: StateFlow<State> = combine(
        favorites,
        selection,
    ) { titleList, selection ->
        State(
            source = source,
            selectedAnimeIds = selection,
            titleList = titleList,
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    fun toggleSelection(anime: Anime) {
        selection.update { selection ->
            if (anime.id in selection) {
                selection - anime.id
            } else {
                selection + anime.id
            }
        }
    }

    fun selectAll() {
        selection.update { state.value.titles.map { it.id }.toSet() }
    }

    fun invertSelection() {
        selection.update {
            val allAnimeIds = state.value.titles.map { it.id }.toSet()
            allAnimeIds - it
        }
    }

    fun clearSelection() {
        selection.update { emptySet() }
    }

    @Immutable
    data class State(
        val source: AnimeSource? = null,
        private val titleList: List<Anime>? = null,
        val selectedAnimeIds: Set<Long> = emptySet(),
    ) {
        val titles: List<Anime>
            get() = titleList ?: listOf()

        val isLoading: Boolean
            get() = source == null || titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()
    }

    sealed interface MigrationAnimeEvent {
        data object FailedFetchingFavorites : MigrationAnimeEvent
    }
}
