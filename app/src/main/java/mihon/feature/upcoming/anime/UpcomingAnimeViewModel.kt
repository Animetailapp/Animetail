package mihon.feature.upcoming.anime

import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexedNotNull
import androidx.lifecycle.viewModelScope
import eu.kanade.core.util.insertSeparatorsReversed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import mihon.core.viewmodel.StateViewModel
import mihon.domain.upcoming.anime.interactor.GetUpcomingAnime
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock

class UpcomingAnimeViewModel(
    private val getUpcomingAnime: GetUpcomingAnime = Injekt.get(),
    val getCategories: GetAnimeCategories = Injekt.get(),
    val upcomingPreferences: UpcomingPreferences = Injekt.get(),
) : StateViewModel<UpcomingAnimeViewModel.State>(State()) {

    val excludedCategories = upcomingPreferences.animeFilterExcludedCategories
    val includedCategories = upcomingPreferences.animeFilterIncludedCategories

    init {
        viewModelScope.launchIO {
            getUpcomingItemPreferenceFlow()
                .distinctUntilChanged()
                .flatMapLatest { prefs ->
                    getUpcomingAnime.subscribe(
                        excludedCategories = prefs.filterExcludedCategories,
                        includedCategories = prefs.filterIncludedCategories,
                    )
                        .distinctUntilChanged()
                        .map { items ->
                            items to
                                (
                                    prefs.filterExcludedCategories.isNotEmpty() ||
                                        prefs.filterIncludedCategories.isNotEmpty()
                                    )
                        }
                }
                .collectLatest { (items, hasFilters) ->
                    mutableState.update { state ->
                        val upcomingItems = items.toUpcomingAnimeUIModels()
                        state.copy(
                            items = upcomingItems,
                            events = upcomingItems.toEvents(),
                            headerIndexes = upcomingItems.getHeaderIndexes(),
                            hasActiveFilters = hasFilters,
                        )
                    }
                }
        }
    }

    private fun List<Anime>.toUpcomingAnimeUIModels(): List<UpcomingAnimeUIModel> {
        var animeCount = 0
        return fastMap { UpcomingAnimeUIModel.Item(it) }
            .insertSeparatorsReversed { before, after ->
                if (after != null) animeCount++

                val beforeDate = before?.anime
                    ?.expectedNextUpdate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())
                    ?.date
                val afterDate = after?.anime
                    ?.expectedNextUpdate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())
                    ?.date

                if (beforeDate != afterDate && afterDate != null) {
                    UpcomingAnimeUIModel.Header(afterDate, animeCount).also { animeCount = 0 }
                } else {
                    null
                }
            }
            .toList()
    }

    private fun List<UpcomingAnimeUIModel>.toEvents(): Map<LocalDate, Int> {
        return filterIsInstance<UpcomingAnimeUIModel.Header>()
            .associate { it.date to it.animeCount }
    }

    private fun List<UpcomingAnimeUIModel>.getHeaderIndexes(): Map<LocalDate, Int> {
        return fastMapIndexedNotNull { index, upcomingUIModel ->
            if (upcomingUIModel is UpcomingAnimeUIModel.Header) {
                upcomingUIModel.date to index
            } else {
                null
            }
        }
            .toMap()
    }

    fun setSelectedYearMonth(yearMonth: YearMonth) {
        mutableState.update { it.copy(selectedYearMonth = yearMonth) }
    }

    private fun getUpcomingItemPreferenceFlow(): Flow<ItemPreferences> {
        return combine(
            upcomingPreferences.animeFilterExcludedCategories.changes(),
            upcomingPreferences.animeFilterIncludedCategories.changes(),
        ) { excluded, included ->
            ItemPreferences(
                filterExcludedCategories = excluded,
                filterIncludedCategories = included,
            )
        }
    }

    fun resetDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun showFilterDialog() {
        mutableState.update { it.copy(dialog = Dialog.FilterSheet) }
    }

    fun cycleCategory(category: Category) {
        when (category.id) {
            in includedCategories.get() -> {
                includedCategories.getAndSet { it - category.id }
                excludedCategories.getAndSet { it + category.id }
            }

            in excludedCategories.get() -> excludedCategories.getAndSet { it - category.id }

            else -> includedCategories.getAndSet { it + category.id }
        }
    }

    @Immutable
    private data class ItemPreferences(
        val filterExcludedCategories: List<Long>,
        val filterIncludedCategories: List<Long>,
    )

    data class State(
        val selectedYearMonth: YearMonth = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .yearMonth,
        val items: List<UpcomingAnimeUIModel> = listOf(),
        val events: Map<LocalDate, Int> = mapOf(),
        val headerIndexes: Map<LocalDate, Int> = mapOf(),
        val hasActiveFilters: Boolean = false,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object FilterSheet : Dialog
    }
}
