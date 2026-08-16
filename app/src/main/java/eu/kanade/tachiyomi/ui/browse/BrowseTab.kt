package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.core.preference.asState
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.migration.sources.migrateAnimeSourceTab
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.feedTab
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.mangaExtensionsTab
import eu.kanade.tachiyomi.ui.browse.manga.migration.sources.migrateMangaSourceTab
import eu.kanade.tachiyomi.ui.browse.manga.source.mangaSourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object BrowseTab : Tab {
    private fun readResolve(): Any = BrowseTab

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    // TODO: Find a way to let it open Global Anime/Manga Search depending on what Tab(e.g. Anime/Manga Source Tab) is open
    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalAnimeSearchScreen())
    }

    private enum class ExtensionTabTarget {
        ANIME,
        MANGA,
    }

    private val switchToExtensionTabChannel = Channel<ExtensionTabTarget>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToExtensionTabChannel.trySend(ExtensionTabTarget.MANGA)
    }

    fun showAnimeExtension() {
        switchToExtensionTabChannel.trySend(ExtensionTabTarget.ANIME)
    }

    @Composable
    override fun Content() {
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

        val animeExtensionsScreenModel = rememberScreenModel { AnimeExtensionsScreenModel() }
        val animeExtensionsSearchQuery by animeExtensionsScreenModel.searchQuery.collectAsStateWithLifecycle()

        val animeExtensionsTabContent = animeExtensionsTab(animeExtensionsScreenModel)
        val mangaExtensionsTabContent = mangaExtensionsTab(mangaExtensionsViewModel)

        // KMK -->
        val feedScreenModel = rememberScreenModel { FeedScreenModel() }
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
            onChangeAnimeSearchQuery = animeExtensionsScreenModel::search,
            animeExtensionsTabIndex = animeExtensionsTabIndex,
            mangaExtensionsTabIndex = mangaExtensionsTabIndex,
            // KMK -->
            feedScreenModel = feedScreenModel,
            // KMK <--
            scrollable = true,
        )
        LaunchedEffect(animeExtensionsTabIndex, mangaExtensionsTabIndex) {
            switchToExtensionTabChannel.receiveAsFlow()
                .collectLatest { target ->
                    val tabIndex = when (target) {
                        ExtensionTabTarget.ANIME -> animeExtensionsTabIndex
                        ExtensionTabTarget.MANGA -> mangaExtensionsTabIndex
                    }
                    if (tabIndex >= 0) {
                        state.scrollToPage(tabIndex)
                    }
                }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
            // AM (DISCORD) -->
            DiscordRPCService.setScreen(context, DiscordScreen.BROWSE)
            // <-- AM (DISCORD)
        }
    }
}
