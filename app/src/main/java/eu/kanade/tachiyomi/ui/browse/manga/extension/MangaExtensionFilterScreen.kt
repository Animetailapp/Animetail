package eu.kanade.tachiyomi.ui.browse.manga.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.manga.MangaExtensionFilterScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

class MangaExtensionFilterScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<MangaExtensionFilterViewModel>()
        val state by viewModel.state.collectAsState()

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
}

