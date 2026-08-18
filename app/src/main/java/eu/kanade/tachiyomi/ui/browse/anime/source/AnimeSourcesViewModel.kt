package eu.kanade.tachiyomi.ui.browse.anime.source

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.anime.AnimeSourceUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.model.Pin
import java.util.TreeMap
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeSourcesViewModel(
    private val preferences: BasePreferences,
    private val sourcePreferences: SourcePreferences,
    private val uiPreferences: UiPreferences,
    private val getEnabledAnimeSources: GetEnabledAnimeSources,
    private val toggleSource: ToggleAnimeSource,
    private val toggleSourcePin: ToggleAnimeSourcePin,
) : ViewModel() {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()
    val useNewSourceNavigation by uiPreferences.useNewSourceNavigation.asState(viewModelScope)

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val enabledSources = getEnabledAnimeSources.subscribe()
        .catch {
            logcat(LogPriority.ERROR, it)
            _events.send(Event.FailedFetchingSources)
        }
        .map(::toSourceUiModels)

    val state: StateFlow<State> = combine(
        enabledSources,
        dialog,
        sourcePreferences.dataSaver.changes(),
    ) { items, dialog, dataSaver ->
        State(
            dialog = dialog,
            isLoading = false,
            items = items.toImmutableList(),
            dataSaverEnabled = dataSaver != SourcePreferences.DataSaver.NONE,
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    private fun toSourceUiModels(sources: List<AnimeSource>): List<AnimeSourceUiModel> {
        val map = TreeMap<String, MutableList<AnimeSource>> { d1, d2 ->
            // Sources without a lang defined will be placed at the end
            when {
                d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                d1 == "" && d2 != "" -> 1
                d2 == "" && d1 != "" -> -1
                else -> d1.compareTo(d2)
            }
        }
        val byLang = sources.groupByTo(map) {
            when {
                it.isUsedLast -> LAST_USED_KEY
                Pin.Actual in it.pin -> PINNED_KEY
                else -> it.lang
            }
        }

        return byLang.flatMap {
            listOf(
                AnimeSourceUiModel.Header(
                    it.key.removePrefix(CATEGORY_KEY_PREFIX),
                    it.value.firstOrNull()?.category != null,
                ),
                *it.value.map { source ->
                    AnimeSourceUiModel.Item(source)
                }.toTypedArray(),
            )
        }
    }

    fun toggleSource(source: AnimeSource) {
        toggleSource.await(source)
    }

    fun togglePin(source: AnimeSource) {
        toggleSourcePin.await(source)
    }

    fun showSourceDialog(source: AnimeSource) {
        dialog.update { Dialog(source) }
    }

    fun closeDialog() {
        dialog.update { null }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    data class Dialog(val source: AnimeSource)

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: ImmutableList<AnimeSourceUiModel> = persistentListOf(),
        // SY -->
        val categories: ImmutableList<String> = persistentListOf(),
        val showPin: Boolean = true,
        val showLatest: Boolean = false,
        val dataSaverEnabled: Boolean = false,
        // SY <--
        // KMK -->
        val searchQuery: String? = null,
        val nsfwOnly: Boolean = false,
        // KMK <--
    ) {
        val isEmpty = items.isEmpty()
    }

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"

        // SY -->
        const val CATEGORY_KEY_PREFIX = "category-"
        // SY <--
    }
}
