package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
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
        val context = LocalContext.current
        val screenModel = rememberScreenModel {
            AnimeExtensionDetailsScreenModel(
                pkgName = pkgName,
                context = context,
            )
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        val navigator = LocalNavigator.currentOrThrow

        when (val state = state) {
            AnimeExtensionDetailsScreenModel.State.Loading -> LoadingScreen()

            AnimeExtensionDetailsScreenModel.State.Uninstalled -> {
                LaunchedEffect(Unit) { navigator.pop() }
                EmptyScreen(MR.strings.empty_screen)
            }

            is AnimeExtensionDetailsScreenModel.State.Success -> {
                AnimeExtensionDetailsScreen(
                    navigateUp = navigator::pop,
                    state = state,
                    onClickSourcePreferences = { navigator.push(AnimeSourcePreferencesScreen(it)) },
                    onClickEnableAll = { screenModel.toggleSources(true) },
                    onClickDisableAll = { screenModel.toggleSources(false) },
                    onClickClearCookies = screenModel::clearCookies,
                    onClickUninstall = screenModel::uninstallExtension,
                    onClickSource = screenModel::toggleSource,
                    onClickIncognito = screenModel::toggleIncognito,
                )
            }
        }
    }
}
