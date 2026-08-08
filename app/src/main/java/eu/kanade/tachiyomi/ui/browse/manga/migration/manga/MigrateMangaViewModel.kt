package eu.kanade.tachiyomi.ui.browse.manga.migration.manga

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.kanade.tachiyomi.source.MangaSource
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
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class MigrateMangaViewModel(
    private val sourceId: Long,
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val getFavorites: GetMangaFavorites = Injekt.get(),
) : ViewModel() {

    companion object {
        val SOURCE_ID_KEY = CreationExtras.Key<Long>()

        val Factory = viewModelFactory {
            initializer {
                MigrateMangaViewModel(
                    sourceId = get(SOURCE_ID_KEY)!!,
                )
            }
        }
    }

    private val _events: Channel<MigrationMangaEvent> = Channel()
    val events: Flow<MigrationMangaEvent> = _events.receiveAsFlow()

    private val source by lazy { sourceManager.getOrStub(sourceId) }

    private val selection = MutableStateFlow(emptySet<Long>())

    private val favorites = getFavorites.subscribe(sourceId)
        .catch {
            logcat(LogPriority.ERROR, it)
            _events.send(MigrationMangaEvent.FailedFetchingFavorites)
            emit(listOf())
        }
        .map { manga ->
            manga.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }

    val state: StateFlow<State> = combine(
        favorites,
        selection,
    ) { titleList, selection ->
        State(source = source, selectedMangaIds = selection, titleList = titleList)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    fun toggleSelection(manga: Manga) {
        selection.update { selection ->
            if (manga.id in selection) {
                selection - manga.id
            } else {
                selection + manga.id
            }
        }
    }

    fun selectAll() {
        selection.update { state.value.titles.map { it.id }.toSet() }
    }

    fun clearSelection() {
        selection.update { emptySet() }
    }

    @Immutable
    data class State(
        val source: MangaSource? = null,
        private val titleList: List<Manga>? = null,
        val selectedMangaIds: Set<Long> = emptySet(),
    ) {

        val titles: List<Manga>
            get() = titleList ?: listOf()

        val isLoading: Boolean
            get() = source == null || titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()
    }
}

sealed interface MigrationMangaEvent {
    data object FailedFetchingFavorites : MigrationMangaEvent
}
