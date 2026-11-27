package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.core.preference.asState
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.migration.sources.migrateAnimeSourceTab
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.feedTab
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.mangaExtensionsTab
import eu.kanade.tachiyomi.ui.browse.manga.migration.sources.migrateMangaSourceTab
import eu.kanade.tachiyomi.ui.browse.manga.source.mangaSourcesTab
import eu.kanade.tachiyomi.ui.home.TopLevelRoute
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.core.navigation.GlobalAnimeSearchRoute
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.MR

data class BrowseShowExtensionsEvent(val isAnime: Boolean)

@Composable
fun BrowseTab() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // SY -->
    val uiPreferences = remember { context.appGraph.uiPreferences }
    val hideFeedTab by remember { uiPreferences.hideFeedTab.asState(scope) }
    val feedTabInFront by remember { uiPreferences.feedTabInFront.asState(scope) }
    // SY <--

    // Hoisted for extensions tab's search bar
    val mangaExtensionsViewModel = metroViewModel<MangaExtensionsViewModel>()
    val mangaExtensionsSearchQuery by mangaExtensionsViewModel.searchQuery.collectAsStateWithLifecycle()

    val animeExtensionsViewModel = metroViewModel<AnimeExtensionsViewModel>()
    val animeExtensionsSearchQuery by animeExtensionsViewModel.searchQuery.collectAsStateWithLifecycle()

    val animeExtensionsTabContent = animeExtensionsTab(animeExtensionsViewModel)
    val mangaExtensionsTabContent = mangaExtensionsTab(mangaExtensionsViewModel)

    // KMK -->
    val feedScreenModel = metroViewModel<FeedScreenModel>()
    // KMK <--

    val tabs = when {
        hideFeedTab ->
            listOf(
                animeSourcesTab(),
                mangaSourcesTab(),
                animeExtensionsTabContent,
                mangaExtensionsTabContent,
                migrateAnimeSourceTab(),
                migrateMangaSourceTab(),
            )

        feedTabInFront ->
            listOf(
                feedTab(
                    // KMK -->
                    feedScreenModel,
                    // KMK <--
                ),
                animeSourcesTab(),
                mangaSourcesTab(),
                animeExtensionsTabContent,
                mangaExtensionsTabContent,
                migrateAnimeSourceTab(),
                migrateMangaSourceTab(),
            )

        else ->
            listOf(
                animeSourcesTab(),
                mangaSourcesTab(),
                feedTab(
                    // KMK -->
                    feedScreenModel,
                    // KMK <--
                ),
                animeExtensionsTabContent,
                mangaExtensionsTabContent,
                migrateAnimeSourceTab(),
                migrateMangaSourceTab(),
            )
        // SY <--
    }

    val animeExtensionsTabIndex = remember(tabs, animeExtensionsTabContent) {
        tabs.indexOf(animeExtensionsTabContent)
    }
    val mangaExtensionsTabIndex = remember(tabs, mangaExtensionsTabContent) {
        tabs.indexOf(mangaExtensionsTabContent)
    }

    val state = rememberPagerState { tabs.size }

    TabbedScreen(
        titleRes = MR.strings.browse,
        tabs = tabs,
        state = state,
        mangaSearchQuery = mangaExtensionsSearchQuery,
        onChangeMangaSearchQuery = mangaExtensionsViewModel::search,
        animeSearchQuery = animeExtensionsSearchQuery,
        onChangeAnimeSearchQuery = animeExtensionsViewModel::search,
        animeExtensionsTabIndex = animeExtensionsTabIndex,
        mangaExtensionsTabIndex = mangaExtensionsTabIndex,
        // KMK -->
        feedScreenModel = feedScreenModel,
        // KMK <--
        scrollable = true,
    )

    ResultEffect<BrowseShowExtensionsEvent> { event ->
        val tabIndex = if (event.isAnime) animeExtensionsTabIndex else mangaExtensionsTabIndex
        if (tabIndex >= 0) {
            scope.launch { state.scrollToPage(tabIndex) }
        }
    }

    ResultEffect<Unit>(resultKey = TopLevelRoute.Browse.reselectEventKey) {
        navigator.push(GlobalAnimeSearchRoute())
    }

    LaunchedEffect(Unit) {
        (context as? MainActivity)?.ready = true
        // AM (DISCORD) -->
        DiscordRPCService.setScreen(context, DiscordScreen.BROWSE)
        // <-- AM (DISCORD)
    }
}
