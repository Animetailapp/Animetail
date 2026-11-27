package eu.kanade.tachiyomi.ui.deeplink.manga

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import mihon.core.navigation.GlobalMangaSearchRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaRoute
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun DeepLinkMangaScreen(query: String = "") {
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    val viewModel =
        assistedMetroViewModel<DeepLinkMangaViewModel, DeepLinkMangaViewModel.Factory> { create(query = query) }
    val state by viewModel.state.collectAsState()
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
            is DeepLinkMangaViewModel.State.Loading -> {
                LoadingScreen(Modifier.padding(contentPadding))
            }

            is DeepLinkMangaViewModel.State.NoResults -> {
                LaunchedEffect(state) {
                    navigator.replace(GlobalMangaSearchRoute(query))
                }
            }

            is DeepLinkMangaViewModel.State.Result -> {
                val resultState = state as DeepLinkMangaViewModel.State.Result
                LaunchedEffect(resultState) {
                    if (resultState.chapterId == null) {
                        navigator.replace(
                            MangaRoute(
                                resultState.manga.id,
                                true,
                            ),
                        )
                    } else {
                        navigator.pop()
                        ReaderActivity.newIntent(
                            context,
                            resultState.manga.id,
                            resultState.chapterId,
                        ).also(context::startActivity)
                    }
                }
            }
        }
    }
}
