package eu.kanade.tachiyomi.ui.stats

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.stats.anime.animeStatsTab
import eu.kanade.tachiyomi.ui.stats.manga.mangaStatsTab
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR

@Composable
fun StatsTab() {
    val context = LocalContext.current

    val tabs = persistentListOf(
        animeStatsTab(),
        mangaStatsTab(),
    )
    val state = rememberPagerState { tabs.size }
    // KMK -->
    val feedScreenModel = metroViewModel<FeedScreenModel>()
    // KMK <--

    TabbedScreen(
        titleRes = MR.strings.label_stats,
        tabs = tabs,
        state = state,
        feedScreenModel = feedScreenModel,
    )

    LaunchedEffect(Unit) {
        (context as? MainActivity)?.ready = true
    }
}
