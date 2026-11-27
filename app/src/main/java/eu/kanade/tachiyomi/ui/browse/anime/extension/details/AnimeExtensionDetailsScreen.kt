package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.anime.AnimeExtensionDetailsScreen
import eu.kanade.presentation.browse.anime.PrivateAnimeExtensionUninstallDialog
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import mihon.core.navigation.AnimeSourcePreferencesRoute
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun AnimeExtensionDetailsScreen(pkgName: String) {
    val viewModel =
        assistedMetroViewModel<AnimeExtensionDetailsViewModel, AnimeExtensionDetailsViewModel.Factory> {
            create(pkgName = pkgName)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val navigator = LocalNavigator.current
    var showUninstallDialog by remember { mutableStateOf(false) }

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
                onClickSourcePreferences = { navigator.push(AnimeSourcePreferencesRoute(it)) },
                onClickEnableAll = { viewModel.toggleSources(true) },
                onClickDisableAll = { viewModel.toggleSources(false) },
                onClickClearCookies = viewModel::clearCookies,
                onClickUninstall = {
                    if (context.isPackageInstalled(state.extension.pkgName)) {
                        viewModel.uninstallExtension()
                    } else {
                        showUninstallDialog = true
                    }
                },
                onClickSource = viewModel::toggleSource,
                onClickIncognito = viewModel::toggleIncognito,
            )

            if (showUninstallDialog) {
                PrivateAnimeExtensionUninstallDialog(
                    extension = state.extension,
                    onConfirm = viewModel::uninstallExtension,
                    onDismissRequest = {
                        showUninstallDialog = false
                    },
                )
            }
        }
    }
}
