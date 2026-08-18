package eu.kanade.tachiyomi.ui.browse.anime.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.anime.AnimeSourcesFilterScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeSourcesFilterScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<AnimeSourcesFilterViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state is AnimeSourcesFilterViewModel.State.Loading) {
            LoadingScreen()
            return
        }

        if (state is AnimeSourcesFilterViewModel.State.Error) {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                context.toast(MR.strings.internal_error)
                navigator.pop()
            }
            return
        }

        val successState = state as AnimeSourcesFilterViewModel.State.Success

        AnimeSourcesFilterScreen(
            navigateUp = navigator::pop,
            state = successState,
            onClickLanguage = viewModel::toggleLanguage,
            onClickSource = viewModel::toggleSource,
        )
    }
}
