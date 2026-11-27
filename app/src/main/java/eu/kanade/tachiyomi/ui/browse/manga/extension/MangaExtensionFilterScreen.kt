package eu.kanade.tachiyomi.ui.browse.manga.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.manga.MangaExtensionFilterScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun MangaExtensionFilterScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = metroViewModel<MangaExtensionFilterViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state is MangaExtensionFilterState.Loading) {
        LoadingScreen()
        return
    }

    val successState = state as MangaExtensionFilterState.Success

    MangaExtensionFilterScreen(
        navigateUp = navigator::pop,
        state = successState,
        onClickToggle = viewModel::toggle,
    )

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest {
            when (it) {
                MangaExtensionFilterEvent.FailedFetchingLanguages -> {
                    context.toast(MR.strings.internal_error)
                }
            }
        }
    }
}
