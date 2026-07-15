package eu.kanade.tachiyomi.ui.entries.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.entries.manga.MangaNotesScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.entries.manga.interactor.UpdateMangaNotes
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaNotesScreen(
    private val mangaId: Long,
    private val mangaTitle: String,
    private val mangaNotes: String,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = viewModel<Model>(
            factory = Model.Factory,
            extras = CreationExtras {
                set(Model.MANGA_ID_KEY, mangaId)
                set(Model.MANGA_TITLE_KEY, mangaTitle)
                set(Model.MANGA_NOTES_KEY, mangaNotes)
            },
        )
        val state by viewModel.state.collectAsState()

        MangaNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = viewModel::updateNotes,
        )
    }

    private class Model(
        private val mangaId: Long,
        mangaTitle: String,
        mangaNotes: String,
        private val updateMangaNotes: UpdateMangaNotes = Injekt.get(),
    ) : StateViewModel<State>(State(mangaId, mangaTitle, mangaNotes)) {

        companion object {
            val MANGA_ID_KEY = CreationExtras.Key<Long>()
            val MANGA_TITLE_KEY = CreationExtras.Key<String>()
            val MANGA_NOTES_KEY = CreationExtras.Key<String>()

            val Factory = viewModelFactory {
                initializer {
                    Model(
                        mangaId = get(MANGA_ID_KEY)!!,
                        mangaTitle = get(MANGA_TITLE_KEY)!!,
                        mangaNotes = get(MANGA_NOTES_KEY)!!,
                    )
                }
            }
        }

        fun updateNotes(content: String) {
            if (content == state.value.notes) return

            mutableState.update {
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
