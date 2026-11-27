package eu.kanade.tachiyomi.ui.entries.anime.notes

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
import eu.kanade.presentation.entries.anime.AnimeNotesScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import mihon.core.navigation.AnimeNotesRoute
import mihon.core.navigation.AnimeRoute
import mihon.core.navigation.LocalNavigator
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.UpdateAnimeNotes
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun AnimeNotesScreen(animeId: Long) {
    val navigator = LocalNavigator.current

    val viewModel = assistedMetroViewModel<AnimeNotesViewModel, AnimeNotesViewModel.Factory> {
        create(animeId = animeId)
    }
    val animeMissing by viewModel.animeMissing.collectAsState()
    LaunchedEffect(animeMissing) {
        if (animeMissing) {
            navigator.popTo { it != AnimeNotesRoute(animeId) && (it as? AnimeRoute)?.animeId != animeId }
        }
    }

    val state by viewModel.state.collectAsState()
    if (state == null) {
        LoadingScreen()
        return
    }

    AnimeNotesScreen(
        state = state!!,
        navigateUp = navigator::pop,
        onUpdate = viewModel::updateNotes,
    )
}

@AssistedInject
class AnimeNotesViewModel(
    @Assisted private val animeId: Long,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val getAnime: GetAnime,
    private val updateAnimeNotes: UpdateAnimeNotes,
) : ViewModel() {

    private val mutableState = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = mutableState.asStateFlow()

    private val mutableAnimeMissing = MutableStateFlow(false)
    val animeMissing: StateFlow<Boolean> = mutableAnimeMissing.asStateFlow()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(animeId: Long): AnimeNotesViewModel
    }

    init {
        viewModelScope.launch {
            val anime = getAnime.await(animeId) ?: run {
                mutableAnimeMissing.value = true
                return@launch
            }
            mutableState.value = State(
                animeId = anime.id,
                animeTitle = anime.title,
                notes = anime.notes,
            )
        }
    }

    fun updateNotes(content: String) {
        val current = state.value ?: return
        if (content == current.notes) return

        mutableState.update {
            it?.copy(notes = content)
        }

        appScope.launch {
            updateAnimeNotes(animeId, content)
        }
    }

    @Immutable
    data class State(
        val animeId: Long,
        val animeTitle: String,
        val notes: String,
    )
}
