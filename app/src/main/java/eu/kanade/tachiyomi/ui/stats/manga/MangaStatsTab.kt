package eu.kanade.tachiyomi.ui.stats.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.stats.MangaStatsScreenContent
import eu.kanade.presentation.more.stats.StatsScreenState
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun mangaStatsTab(): TabContent {
    val navigator = LocalNavigator.current

    val viewModel = metroViewModel<MangaStatsViewModel>()
    val state by viewModel.state.collectAsState()

    if (state is StatsScreenState.Loading) {
        LoadingScreen()
    }

    return TabContent(
        titleRes = AYMR.strings.label_manga,
        content = { contentPadding, _ ->

            if (state is StatsScreenState.Loading) {
                LoadingScreen()
            } else {
                MangaStatsScreenContent(
                    state = state as StatsScreenState.SuccessManga,
                    paddingValues = contentPadding,
                )
            }
        },
        navigateUp = navigator::pop,
    )
}
