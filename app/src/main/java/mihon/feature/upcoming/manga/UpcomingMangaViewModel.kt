package mihon.feature.upcoming.manga

import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexedNotNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.core.util.insertSeparatorsReversed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import mihon.domain.upcoming.manga.interactor.GetUpcomingManga
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class UpcomingMangaViewModel(
    private val getUpcomingManga: GetUpcomingManga = Injekt.get(),
    val getCategories: GetMangaCategories = Injekt.get(),
    val upcomingPreferences: UpcomingPreferences = Injekt.get(),
) : ViewModel() {

    val excludedCategories = upcomingPreferences.mangaFilterExcludedCategories
    val includedCategories = upcomingPreferences.mangaFilterIncludedCategories

    private val selectedYearMonth = MutableStateFlow(
        value = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .yearMonth,
    )

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val hasActiveFilters = getUpcomingItemPreferenceFlow()
        .map { prefs ->
            listOf(
                prefs.filterIncludedCategories,
                prefs.filterExcludedCategories,
            )
                .any { it.isNotEmpty() }
        }
        .distinctUntilChanged()

    private val upcoming = getUpcomingItemPreferenceFlow()
        .distinctUntilChanged()
        .flatMapLatest { prefs ->
            getUpcomingManga.subscribe(
                excludedCategories = prefs.filterExcludedCategories,
                includedCategories = prefs.filterIncludedCategories,
            )
                .distinctUntilChanged()
                .map { items -> items.toUpcomingMangaUIModels() }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())

    val state: StateFlow<State> = combine(
        upcoming,
        selectedYearMonth,
        dialog,
        hasActiveFilters,
    ) { upcoming, selectedYearMonth, dialog, hasActiveFilters ->
        State(
            selectedYearMonth = selectedYearMonth,
            items = upcoming,
            events = upcoming.toEvents(),
            headerIndexes = upcoming.getHeaderIndexes(),
            hasActiveFilters = hasActiveFilters,
            dialog = dialog,
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5.seconds),
            State(selectedYearMonth = selectedYearMonth.value),
        )

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
        selectedYearMonth.update { yearMonth }
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
        dialog.update { null }
    }

    fun showFilterDialog() {
        dialog.update { Dialog.FilterSheet }
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
        val selectedYearMonth: YearMonth,
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
