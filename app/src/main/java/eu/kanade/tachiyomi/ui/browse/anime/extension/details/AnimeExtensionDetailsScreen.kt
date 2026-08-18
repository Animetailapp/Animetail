package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.anime.AnimeExtensionDetailsScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

data class AnimeExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val viewModel =
            assistedMetroViewModel<AnimeExtensionDetailsViewModel, AnimeExtensionDetailsViewModel.Factory> {
                create(pkgName = pkgName)
            }
        val state by viewModel.state.collectAsStateWithLifecycle()

        val navigator = LocalNavigator.currentOrThrow

        when (val state = state) {
            AnimeExtensionDetailsViewModel.State.Loading -> LoadingScreen()

            AnimeExtensionDetailsViewModel.State.Uninstalled -> {
                LaunchedEffect(Unit) { navigator.pop() }
                EmptyScreen(MR.strings.empty_screen)
            }

            is AnimeExtensionDetailsViewModel.State.Success -> {
                AnimeExtensionDetailsScreen(
                    navigateUp = navigator::pop,
                    state = state,
                    onClickSourcePreferences = { navigator.push(AnimeSourcePreferencesScreen(it)) },
                    onClickEnableAll = { viewModel.toggleSources(true) },
                    onClickDisableAll = { viewModel.toggleSources(false) },
                    onClickClearCookies = viewModel::clearCookies,
                    onClickUninstall = viewModel::uninstallExtension,
                    onClickSource = viewModel::toggleSource,
                    onClickIncognito = viewModel::toggleIncognito,
                )
            }
        }
    }
}
