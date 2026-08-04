package mihon.feature.upcoming.manga

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
import mihon.domain.upcoming.manga.interactor.GetUpcomingManga
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock

class UpcomingMangaViewModel(
    private val getUpcomingManga: GetUpcomingManga = Injekt.get(),
    val getCategories: GetMangaCategories = Injekt.get(),
    val upcomingPreferences: UpcomingPreferences = Injekt.get(),
) : StateViewModel<UpcomingMangaViewModel.State>(State()) {

    val excludedCategories = upcomingPreferences.mangaFilterExcludedCategories
    val includedCategories = upcomingPreferences.mangaFilterIncludedCategories

    init {
        viewModelScope.launchIO {
            getUpcomingItemPreferenceFlow()
                .distinctUntilChanged()
                .flatMapLatest { prefs ->
                    getUpcomingManga.subscribe(
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
                        val upcomingItems = items.toUpcomingMangaUIModels()
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

    private fun List<Manga>.toUpcomingMangaUIModels(): List<UpcomingMangaUIModel> {
        var mangaCount = 0
        return fastMap { UpcomingMangaUIModel.Item(it) }
            .insertSeparatorsReversed { before, after ->
                if (after != null) mangaCount++

                val beforeDate = before?.manga
                    ?.expectedNextUpdate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())
                    ?.date
                val afterDate = after?.manga
                    ?.expectedNextUpdate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())
                    ?.date

                if (beforeDate != afterDate && afterDate != null) {
                    UpcomingMangaUIModel.Header(afterDate, mangaCount).also { mangaCount = 0 }
                } else {
                    null
                }
            }
    }

    private fun List<UpcomingMangaUIModel>.toEvents(): Map<LocalDate, Int> {
        return filterIsInstance<UpcomingMangaUIModel.Header>()
            .associate { it.date to it.mangaCount }
    }

    private fun List<UpcomingMangaUIModel>.getHeaderIndexes(): Map<LocalDate, Int> {
        return fastMapIndexedNotNull { index, upcomingUIModel ->
            if (upcomingUIModel is UpcomingMangaUIModel.Header) {
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
            upcomingPreferences.mangaFilterExcludedCategories.changes(),
            upcomingPreferences.mangaFilterIncludedCategories.changes(),
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
        val items: List<UpcomingMangaUIModel> = listOf(),
        val events: Map<LocalDate, Int> = mapOf(),
        val headerIndexes: Map<LocalDate, Int> = mapOf(),
        val hasActiveFilters: Boolean = false,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object FilterSheet : Dialog
    }
}
