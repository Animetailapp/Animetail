package eu.kanade.tachiyomi.ui.browse.manga.extension

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionLanguages
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class MangaExtensionFilterViewModel(
    private val preferences: SourcePreferences = Injekt.get(),
    private val getExtensionLanguages: GetMangaExtensionLanguages = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
) : ViewModel() {

    private val _events: Channel<MangaExtensionFilterEvent> = Channel()
    val events: Flow<MangaExtensionFilterEvent> = _events.receiveAsFlow()

    val state: StateFlow<MangaExtensionFilterState> = combine(
        getExtensionLanguages.subscribe(),
        preferences.enabledLanguages.changes(),
    ) { extensionLanguages, enabledLanguages ->
        MangaExtensionFilterState.Success(
            languages = extensionLanguages.toList(),
            enabledLanguages = enabledLanguages.toSet(),
        )
    }
        .catch { throwable ->
            logcat(LogPriority.ERROR, throwable)
            _events.send(MangaExtensionFilterEvent.FailedFetchingLanguages)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), MangaExtensionFilterState.Loading)

    fun toggle(language: String) {
        toggleLanguage.await(language)
    }
}

sealed interface MangaExtensionFilterEvent {
    data object FailedFetchingLanguages : MangaExtensionFilterEvent
}

sealed interface MangaExtensionFilterState {

    @Immutable
    data object Loading : MangaExtensionFilterState

    @Immutable
    data class Success(
        val languages: List<String>,
        val enabledLanguages: Set<String> = setOf(),
    ) : MangaExtensionFilterState {

        val isEmpty: Boolean
            get() = languages.isEmpty()
    }
}
