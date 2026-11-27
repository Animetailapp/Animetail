package eu.kanade.tachiyomi.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.currentNavigationStyle
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.history.anime.AnimeHistoryViewModel
import eu.kanade.tachiyomi.ui.history.anime.animeHistoryTab
import eu.kanade.tachiyomi.ui.history.manga.MangaHistoryViewModel
import eu.kanade.tachiyomi.ui.history.manga.mangaHistoryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR

@Composable
fun HistoriesTab() {
    val context = LocalContext.current
    val fromMore = currentNavigationStyle() == NavStyle.MOVE_HISTORY_TO_MORE
    // Hoisted for history tab's search bar
    val mangaHistoryViewModel = metroViewModel<MangaHistoryViewModel>()
    val mangaHistoryState by mangaHistoryViewModel.state.collectAsStateWithLifecycle()
    val mangaSearchQuery = mangaHistoryState.searchQuery
    // KMK -->
    val feedScreenModel = metroViewModel<FeedScreenModel>()
    // KMK <--
    val animeHistoryViewModel = metroViewModel<AnimeHistoryViewModel>()
    val animeHistoryState by animeHistoryViewModel.state.collectAsStateWithLifecycle()
    val animeSearchQuery = animeHistoryState.searchQuery

    TabbedScreen(
        titleRes = MR.strings.label_recent_manga,
        tabs = persistentListOf(
            animeHistoryTab(context, fromMore),
            mangaHistoryTab(context, fromMore),
        ),
        mangaSearchQuery = mangaSearchQuery,
        onChangeMangaSearchQuery = mangaHistoryViewModel::updateSearchQuery,
        animeSearchQuery = animeSearchQuery,
        onChangeAnimeSearchQuery = animeHistoryViewModel::search,
        animeExtensionsTabIndex = TAB_ANIME,
        mangaExtensionsTabIndex = TAB_MANGA,
        // KMK -->
        feedScreenModel = feedScreenModel,
        // KMK <--

    )

    LaunchedEffect(Unit) {
        (context as? MainActivity)?.ready = true
    }
}

private const val TAB_ANIME = 0
private const val TAB_MANGA = 1
