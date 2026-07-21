package mihon.feature.upcoming.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen

class UpcomingMangaScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = viewModel<UpcomingMangaViewModel>()
        val state by viewModel.state.collectAsState()

        UpcomingMangaScreenContent(
            state = state,
            setSelectedYearMonth = viewModel::setSelectedYearMonth,
            onClickUpcoming = { navigator.push(MangaScreen(it.id)) },
        )
    }
}
