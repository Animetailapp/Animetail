package eu.kanade.tachiyomi.ui.browse.anime.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.anime.AnimeExtensionFilterScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun AnimeExtensionFilterScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = metroViewModel<AnimeExtensionFilterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state is AnimeExtensionFilterState.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as AnimeExtensionFilterState.Success

    AnimeExtensionFilterScreen(
        navigateUp = navigator::pop,
        state = successState,
        onClickToggle = viewModel::toggle,
    )

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest {
            when (it) {
                AnimeExtensionFilterEvent.FailedFetchingLanguages -> {
                    context.toast(MR.strings.internal_error)
                }
            }
        }
    }
}
