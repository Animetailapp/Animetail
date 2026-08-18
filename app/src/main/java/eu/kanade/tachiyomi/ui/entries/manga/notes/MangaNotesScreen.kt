package eu.kanade.tachiyomi.ui.entries.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.entries.manga.MangaNotesScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.entries.manga.interactor.UpdateMangaNotes

class MangaNotesScreen(
    private val mangaId: Long,
    private val mangaTitle: String,
    private val mangaNotes: String,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = assistedMetroViewModel<Model, Model.Factory> {
            create(mangaId = mangaId, mangaTitle = mangaTitle, mangaNotes = mangaNotes)
        }
        val state by viewModel.state.collectAsState()

        MangaNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = viewModel::updateNotes,
        )
    }

    @AssistedInject
    class Model(
        @Assisted private val mangaId: Long,
        @Assisted mangaTitle: String,
        @Assisted mangaNotes: String,
        private val updateMangaNotes: UpdateMangaNotes,
    ) : ViewModel() {

        val state: StateFlow<State>
            field = MutableStateFlow<State>(State(mangaId, mangaTitle, mangaNotes))

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(mangaId: Long, mangaTitle: String, mangaNotes: String): Model
        }

        fun updateNotes(content: String) {
            if (content == state.value.notes) return

            state.update {
                it.copy(notes = content)
            }

            viewModelScope.launchNonCancellable {
                updateMangaNotes(mangaId, content)
            }
        }
    }

    @Immutable
    data class State(
        val mangaId: Long,
        val mangaTitle: String,
        val notes: String,
    )
}
