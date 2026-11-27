package eu.kanade.tachiyomi.ui.browse.manga.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.manga.MangaExtensionDetailsScreen
import eu.kanade.presentation.browse.manga.PrivateMangaExtensionUninstallDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

data class MangaExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val viewModel =
            assistedMetroViewModel<MangaExtensionDetailsViewModel, MangaExtensionDetailsViewModel.Factory> {
                create(pkgName = pkgName)
            }
        val state by viewModel.state.collectAsStateWithLifecycle()

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        var showUninstallDialog by remember { mutableStateOf(false) }

        when (val state = state) {
            MangaExtensionDetailsViewModel.State.Loading -> LoadingScreen()

            MangaExtensionDetailsViewModel.State.Uninstalled -> {
                LaunchedEffect(Unit) { navigator.pop() }
                EmptyScreen(MR.strings.empty_screen)
            }

            is MangaExtensionDetailsViewModel.State.Success -> {
                MangaExtensionDetailsScreen(
                    navigateUp = navigator::pop,
                    state = state,
                    onClickSourcePreferences = { navigator.push(MangaSourcePreferencesScreen(it)) },
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
                    PrivateMangaExtensionUninstallDialog(
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
}
