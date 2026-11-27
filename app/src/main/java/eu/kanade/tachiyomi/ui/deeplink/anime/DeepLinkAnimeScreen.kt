package eu.kanade.tachiyomi.ui.deeplink.anime

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import mihon.core.navigation.AnimeRoute
import mihon.core.navigation.GlobalAnimeSearchRoute
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun DeepLinkAnimeScreen(query: String = "") {
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    val screenModel =
        assistedMetroViewModel<DeepLinkAnimeScreenModel, DeepLinkAnimeScreenModel.Factory> { create(query = query) }
    val state by screenModel.state.collectAsState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.action_search_hint),
                navigateUp = navigator::pop,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        when (state) {
            is DeepLinkAnimeScreenModel.State.Loading -> {
                LoadingScreen(Modifier.padding(contentPadding))
            }

            is DeepLinkAnimeScreenModel.State.NoResults -> {
                LaunchedEffect(state) {
                    navigator.replace(GlobalAnimeSearchRoute(query))
                }
            }

            is DeepLinkAnimeScreenModel.State.Result -> {
                val resultState = state as DeepLinkAnimeScreenModel.State.Result
                LaunchedEffect(resultState) {
                    if (resultState.episodeId == null) {
                        navigator.replace(
                            AnimeRoute(
                                resultState.anime.id,
                                true,
                            ),
                        )
                    } else {
                        navigator.pop()
                        PlayerActivity.newIntent(
                            context,
                            resultState.anime.id,
                            resultState.episodeId,
                        ).also(context::startActivity)
                    }
                }
            }
        }
    }
}
