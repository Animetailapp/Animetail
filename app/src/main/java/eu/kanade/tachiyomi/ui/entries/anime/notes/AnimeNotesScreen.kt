package eu.kanade.tachiyomi.ui.entries.anime.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.entries.anime.AnimeNotesScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.entries.anime.interactor.UpdateAnimeNotes
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Serializable
class AnimeNotesScreen(
    private val animeId: Long,
    private val animeTitle: String,
    private val animeNotes: String,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            Model(
                animeId = animeId,
                animeTitle = animeTitle,
                animeNotes = animeNotes,
            )
        }
        val state by screenModel.state.collectAsState()

        AnimeNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = screenModel::updateNotes,
        )
    }

    private class Model(
        animeTitle: String,
        animeNotes: String,
        private val animeId: Long,
        private val updateAnimeNotes: UpdateAnimeNotes = Injekt.get(),
    ) : StateScreenModel<State>(State(animeId, animeTitle, animeNotes)) {

        fun updateNotes(content: String) {
            if (content == state.value.notes) return

            mutableState.update {
                it.copy(notes = content)
            }

            screenModelScope.launchNonCancellable {
                updateAnimeNotes(animeId, content)
            }
        }
    }

    @Immutable
    data class State(
        val animeId: Long,
        val animeTitle: String,
        val notes: String,
    )
}
