package eu.kanade.tachiyomi.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import eu.kanade.presentation.util.rememberAnimatedVectorPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseShowExtensionsEvent
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibrarySearchEvent
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibrarySearchEvent
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.player.CastManager
import eu.kanade.tachiyomi.ui.player.cast.CastMiniController
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import mihon.app.di.appGraph
import mihon.core.navigation.AnimeRoute
import mihon.core.navigation.DownloadQueueRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.Navigator
import mihon.core.navigation.NavigatorDisplay
import mihon.core.navigation.rememberNavigator
import mihon.presentation.core.util.isTabletUi
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Serializable
sealed interface TopLevelRoute : NavKey {

    val reselectEventKey: String
        get() = "TabReselectEvent:$this"

    @Composable
    fun isEnabled(): Boolean = true

    @Composable
    fun options(isSelected: Boolean): TabOptions

    @Serializable
    data object Home : TopLevelRoute {
        @Composable
        override fun isEnabled(): Boolean {
            val context = LocalContext.current
            val uiPreferences = remember { context.appGraph.uiPreferences }
            val showHomeTab by uiPreferences.showHomeTab.collectAsState()
            return showHomeTab
        }

        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            return TabOptions(
                title = stringResource(MR.strings.label_home),
                icon = rememberVectorPainter(Icons.Outlined.Home),
            )
        }
    }

    @Serializable
    data object AnimeLibrary : TopLevelRoute {
        @OptIn(ExperimentalAnimationGraphicsApi::class)
        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_animelibrary_leave)
            return TabOptions(
                title = stringResource(AYMR.strings.label_anime_library),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    }

    @Serializable
    data object MangaLibrary : TopLevelRoute {
        @OptIn(ExperimentalAnimationGraphicsApi::class)
        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                title = stringResource(AYMR.strings.label_manga_library),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    }

    @Serializable
    data object Updates : TopLevelRoute {
        @Composable
        override fun isEnabled(): Boolean {
            val context = LocalContext.current
            val showNavUpdates by remember { context.appGraph.uiPreferences.showNavUpdates }.collectAsState()
            return showNavUpdates
        }

        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    }

    @Serializable
    data object History : TopLevelRoute {
        @Composable
        override fun isEnabled(): Boolean {
            val context = LocalContext.current
            val showNavHistory by remember { context.appGraph.uiPreferences.showNavHistory }.collectAsState()
            return showNavHistory
        }

        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                title = stringResource(MR.strings.history),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    }

    @Serializable
    data object Browse : TopLevelRoute {
        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    }

    @Serializable
    data object More : TopLevelRoute {
        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            val enter = AnimatedImageVector.animatedVectorResource(R.drawable.anim_more_enter)
            val exit = AnimatedImageVector.animatedVectorResource(R.drawable.animated_more_exit)
            return TabOptions(
                title = stringResource(MR.strings.label_more),
                icon = rememberAnimatedVectorPainter(enter, exit, isSelected),
            )
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val appGraph = remember(context) { context.appGraph }
    val uiPreferences = appGraph.uiPreferences
    val navStyle by uiPreferences.navStyle.collectAsState()
    val navigator = LocalNavigator.current
    val resultEventBus = LocalResultEventBus.current
    val alwaysShowLabel by uiPreferences.bottomBarLabels.collectAsState()

    val activity = context as? ComponentActivity
    val preferences = appGraph.preferenceStore
    val castManager = remember { CastManager(activity!!, preferences) }

    LaunchedEffect(Unit) {
        castManager.startDeviceDiscovery()
        castManager.reconnect()
    }

    val showHomeTab by uiPreferences.showHomeTab.collectAsState()
    val startScreenPref by uiPreferences.startScreen.collectAsState()

    val effectiveStartTab = remember(showHomeTab, startScreenPref, navStyle) {
        val configuredTab = startScreenPref.tab
        val resolved = if (configuredTab == TopLevelRoute.Home && !showHomeTab) {
            TopLevelRoute.AnimeLibrary
        } else {
            configuredTab
        }
        if (resolved == navStyle.moreTab) {
            if (showHomeTab) TopLevelRoute.Home else TopLevelRoute.AnimeLibrary
        } else {
            resolved
        }
    }

    val tabNavigator = rememberNavigator(effectiveStartTab)
    var bottomNavVisible by remember { mutableStateOf(true) }

    ResultEffect<ShowBottomNavEvent> {
        bottomNavVisible = it.visible
    }

    val tabletUi = isTabletUi()
    Row(modifier = Modifier.fillMaxSize()) {
        if (tabletUi) {
            NavigationRail {
                navStyle.tabs
                    .fastFilter { it.isEnabled() }
                    .fastForEach {
                        NavigationRailItem(it, tabNavigator, alwaysShowLabel)
                    }
            }
        }
        Scaffold(
            bottomBar = {
                if (!tabletUi) {
                    Column {
                        val isConnected =
                            castManager.castState.collectAsState().value == CastManager.CastState.CONNECTED
                        AnimatedVisibility(
                            visible = isConnected,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            CastMiniController(
                                castManager = castManager,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }

                        AnimatedVisibility(
                            visible = bottomNavVisible && tabNavigator.current != navStyle.moreTab,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            NavigationBar {
                                navStyle.tabs
                                    .fastFilter { it.isEnabled() }
                                    .fastForEach {
                                        NavigationBarItem(it, tabNavigator, alwaysShowLabel)
                                    }
                            }
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding),
            ) {
                val tabStateHolder = rememberSaveableStateHolder()
                NavigatorDisplay(
                    navigator = tabNavigator,
                    entryDecorators = listOf(
                        remember(tabStateHolder) {
                            NavEntryDecorator { entry ->
                                tabStateHolder.SaveableStateProvider(entry.contentKey) { entry.Content() }
                            }
                        },
                    ),
                    transitionSpec = { spec },
                    popTransitionSpec = { spec },
                    predictivePopTransitionSpec = { spec },
                    entryProvider = entryProvider {
                        entry<TopLevelRoute.Home> {
                            HomeTab()
                        }
                        entry<TopLevelRoute.AnimeLibrary> {
                            AnimeLibraryTab()
                        }
                        entry<TopLevelRoute.MangaLibrary> {
                            MangaLibraryTab()
                        }
                        entry<TopLevelRoute.Updates> {
                            UpdatesTab()
                        }
                        entry<TopLevelRoute.History> {
                            HistoriesTab()
                        }
                        entry<TopLevelRoute.Browse> {
                            BrowseTab()
                        }
                        entry<TopLevelRoute.More> {
                            MoreTab()
                        }
                    },
                )
            }
        }
    }

    ResultEffect<TabEvent> {
        val route = when (it) {
            is TabEvent.Home -> if (showHomeTab) TopLevelRoute.Home else TopLevelRoute.AnimeLibrary

            is TabEvent.AnimeLibrary -> {
                if (it.query != null) resultEventBus.sendResult(AnimeLibrarySearchEvent(it.query))
                TopLevelRoute.AnimeLibrary
            }

            is TabEvent.MangaLibrary -> {
                if (it.query != null) resultEventBus.sendResult(MangaLibrarySearchEvent(it.query))
                TopLevelRoute.MangaLibrary
            }

            TabEvent.Updates -> TopLevelRoute.Updates

            TabEvent.History -> TopLevelRoute.History

            is TabEvent.Browse -> {
                if (it.toExtensions) resultEventBus.sendResult(BrowseShowExtensionsEvent(isAnime = it.anime))
                TopLevelRoute.Browse
            }

            is TabEvent.More -> TopLevelRoute.More
        }
        tabNavigator.replaceAll(route)

        if (it is TabEvent.AnimeLibrary && it.animeIdToOpen != null) {
            navigator.push(AnimeRoute(it.animeIdToOpen))
        }
        if (it is TabEvent.MangaLibrary && it.mangaIdToOpen != null) {
            navigator.push(MangaRoute(it.mangaIdToOpen))
        }
        if (it is TabEvent.More && it.toDownloads) {
            navigator.push(DownloadQueueRoute)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.NavigationBarItem(
    tab: TopLevelRoute,
    tabNavigator: Navigator,
    alwaysShowLabel: Boolean,
) {
    val resultEventBus = LocalResultEventBus.current
    val selected = tabNavigator.current == tab
    val options = tab.options(selected)
    val onClick = {
        if (!selected) {
            tabNavigator.replaceAll(tab)
        } else {
            resultEventBus.sendResult(
                resultKey = tab.reselectEventKey,
                result = Unit,
            )
        }
    }
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                if (!selected) {
                    tabNavigator.replaceAll(tab)
                }
                resultEventBus.sendResult(
                    resultKey = tab.reselectEventKey,
                    result = Unit,
                )
            },
        ),
        icon = { NavigationIconItem(tab, options) },
        label = {
            Text(
                text = options.title,
                style = MaterialTheme.typography.labelMedium,
                fontSize = TextUnit(11f, TextUnitType.Sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        alwaysShowLabel = alwaysShowLabel,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavigationRailItem(
    tab: TopLevelRoute,
    tabNavigator: Navigator,
    alwaysShowLabel: Boolean,
) {
    val resultEventBus = LocalResultEventBus.current
    val selected = tabNavigator.current == tab
    val options = tab.options(selected)
    val onClick = {
        if (!selected) {
            tabNavigator.replaceAll(tab)
        } else {
            resultEventBus.sendResult(
                resultKey = tab.reselectEventKey,
                result = Unit,
            )
        }
    }
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                if (!selected) {
                    tabNavigator.replaceAll(tab)
                }
                resultEventBus.sendResult(
                    resultKey = tab.reselectEventKey,
                    result = Unit,
                )
            },
        ),
        icon = { NavigationIconItem(tab, options) },
        label = {
            Text(
                text = options.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        alwaysShowLabel = alwaysShowLabel,
    )
}

@Composable
private fun NavigationIconItem(
    tab: TopLevelRoute,
    options: TabOptions,
) {
    val context = LocalContext.current
    BadgedBox(
        badge = {
            when (tab) {
                TopLevelRoute.Updates -> {
                    val count by produceState(initialValue = 0) {
                        val pref = context.appGraph.libraryPreferences
                        combine(
                            pref.newAnimeUpdatesCount.changes(),
                            pref.newMangaUpdatesCount.changes(),
                        ) { countAnime, countManga -> countAnime + countManga }
                            .collectLatest { value = if (pref.newShowUpdatesCount.get()) it else 0 }
                    }
                    if (count > 0) {
                        Badge {
                            val desc = pluralStringResource(
                                MR.plurals.notification_chapters_generic,
                                count = count,
                                count,
                            )
                            Text(
                                text = count.toString(),
                                modifier = Modifier.semantics { contentDescription = desc },
                            )
                        }
                    }
                }

                TopLevelRoute.Browse -> {
                    val count by produceState(initialValue = 0) {
                        val pref = context.appGraph.sourcePreferences
                        combine(
                            pref.extensionUpdatesCount.changes(),
                            pref.animeExtensionUpdatesCount.changes(),
                        ) { extCount, animeExtCount -> extCount + animeExtCount }
                            .collectLatest { value = it }
                    }
                    if (count > 0) {
                        Badge {
                            val desc = pluralStringResource(
                                MR.plurals.update_check_notification_ext_updates,
                                count = count,
                                count,
                            )
                            Text(
                                text = count.toString(),
                                modifier = Modifier.semantics { contentDescription = desc },
                            )
                        }
                    }
                }

                else -> {}
            }
        },
    ) {
        Icon(
            painter = options.icon,
            contentDescription = options.title,
            // TODO: https://issuetracker.google.com/u/0/issues/316327367
            tint = LocalContentColor.current,
        )
    }
}

sealed interface TabEvent {
    data object Home : TabEvent
    data class AnimeLibrary(val animeIdToOpen: Long? = null, val query: String? = null) : TabEvent
    data class MangaLibrary(val mangaIdToOpen: Long? = null, val query: String? = null) : TabEvent
    data object Updates : TabEvent
    data object History : TabEvent
    data class Browse(val toExtensions: Boolean = false, val anime: Boolean = false) : TabEvent
    data class More(val toDownloads: Boolean) : TabEvent
}

data class ShowBottomNavEvent(val visible: Boolean)

data class TabOptions(
    val title: String,
    val icon: Painter,
)

private const val TAB_FADE_DURATION = 200

private val spec = materialFadeThroughIn(
    initialScale = 1f,
    durationMillis = TAB_FADE_DURATION,
) togetherWith materialFadeThroughOut(durationMillis = TAB_FADE_DURATION)
