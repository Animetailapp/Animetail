package eu.kanade.tachiyomi.ui.stats.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.stats.AnimeStatsScreenContent
import eu.kanade.presentation.more.stats.StatsScreenState
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun animeStatsTab(): TabContent {
    val navigator = LocalNavigator.current

    val viewModel = metroViewModel<AnimeStatsViewModel>()
    val state by viewModel.state.collectAsState()

    if (state is StatsScreenState.Loading) {
        LoadingScreen()
    }

    return TabContent(
        titleRes = AYMR.strings.label_anime,
        content = { contentPadding, _ ->

            if (state is StatsScreenState.Loading) {
                LoadingScreen()
            } else {
                AnimeStatsScreenContent(
                    state = state as StatsScreenState.SuccessAnime,
                    paddingValues = contentPadding,
                )
            }
        },
        navigateUp = navigator::pop,
    )
}
