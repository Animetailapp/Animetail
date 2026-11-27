package eu.kanade.tachiyomi.ui.browse.manga.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.manga.MangaSourcesFilterScreen
import eu.kanade.tachiyomi.util.system.toast
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun MangaSourcesFilterScreen() {
    val navigator = LocalNavigator.current
    val viewModel = metroViewModel<MangaSourcesFilterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state is MangaSourcesFilterViewModel.State.Loading) {
        LoadingScreen()
        return
    }

    if (state is MangaSourcesFilterViewModel.State.Error) {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            context.toast(MR.strings.internal_error)
            navigator.pop()
        }
        return
    }

    val successState = state as MangaSourcesFilterViewModel.State.Success

    MangaSourcesFilterScreen(
        navigateUp = navigator::pop,
        state = successState,
        onClickLanguage = viewModel::toggleLanguage,
        onClickSource = viewModel::toggleSource,
    )
}
