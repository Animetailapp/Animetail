package eu.kanade.tachiyomi.ui.library.manga

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.manga.interactor.SetMangaDisplayMode
import tachiyomi.domain.category.manga.interactor.SetSortModeForMangaCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class MangaLibrarySettingsViewModel(
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setMangaDisplayMode: SetMangaDisplayMode = Injekt.get(),
    private val setSortModeForCategory: SetSortModeForMangaCategory = Injekt.get(),
    trackerManager: TrackerManager = Injekt.get(),
) : ViewModel() {

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    // SY -->
    val grouping by libraryPreferences.groupMangaLibraryBy.asState(viewModelScope)

    // SY <--

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTrackedManga(id) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setMangaDisplayMode.await(mode)
    }

    fun setSort(
        category: Category?,
        mode: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction,
    ) {
        viewModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }

    // SY -->
    fun setGrouping(grouping: Int) {
        viewModelScope.launchIO {
            libraryPreferences.groupMangaLibraryBy.set(grouping)
        }
    }
    // SY <--
}
