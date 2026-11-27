package eu.kanade.tachiyomi.ui.entries.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.entries.manga.MangaNotesScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaNotesRoute
import mihon.core.navigation.MangaRoute
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.UpdateMangaNotes
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun MangaNotesScreen(mangaId: Long) {
    val navigator = LocalNavigator.current

    val viewModel = assistedMetroViewModel<MangaNotesViewModel, MangaNotesViewModel.Factory> {
        create(mangaId = mangaId)
    }
    val mangaMissing = viewModel.mangaMissing.collectAsState().value
    LaunchedEffect(mangaMissing) {
        if (mangaMissing) {
            navigator.popTo { it != MangaNotesRoute(mangaId) && (it as? MangaRoute)?.mangaId != mangaId }
        }
    }

    val state = viewModel.state.collectAsState().value
    if (state == null) {
        LoadingScreen()
        return
    }

    MangaNotesScreen(
        state = state,
        navigateUp = navigator::pop,
        onUpdate = viewModel::updateNotes,
    )
}

@AssistedInject
class MangaNotesViewModel(
    @Assisted private val mangaId: Long,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val getManga: GetManga,
    private val updateMangaNotes: UpdateMangaNotes,
) : ViewModel() {

    val state: StateFlow<MangaNotesScreen.State?>
        field = MutableStateFlow<MangaNotesScreen.State?>(null)

    val mangaMissing: StateFlow<Boolean>
        field = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val manga = getManga.await(mangaId) ?: run {
                mangaMissing.value = true
                return@launch
            }
            state.value = MangaNotesScreen.State(manga.id, manga.title, manga.notes)
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): MangaNotesViewModel
    }

    fun updateNotes(content: String) {
        if (content == state.value?.notes) return

        state.update {
            it?.copy(notes = content)
        }

        // On Main so the debounced saves and the one on leaving are written in order
        appScope.launch(Dispatchers.Main.immediate) {
            updateMangaNotes(mangaId, content)
        }
    }
}

object MangaNotesScreen {
    @Immutable
    data class State(
        val mangaId: Long,
        val mangaTitle: String,
        val notes: String,
    )
}
