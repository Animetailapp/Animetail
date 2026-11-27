package eu.kanade.tachiyomi.ui.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.currentNavigationStyle
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.home.TopLevelRoute
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.updates.anime.animeUpdatesTab
import eu.kanade.tachiyomi.ui.updates.manga.mangaUpdatesTab
import kotlinx.collections.immutable.persistentListOf
import mihon.core.navigation.DownloadQueueRoute
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.MR

@Composable
fun UpdatesTab() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val fromMore = currentNavigationStyle() == NavStyle.MOVE_UPDATES_TO_MORE
    // KMK -->
    val feedScreenModel = metroViewModel<FeedScreenModel>()
    // KMK <--

    TabbedScreen(
        titleRes = MR.strings.label_recent_updates,
        tabs = persistentListOf(
            animeUpdatesTab(context, fromMore),
            mangaUpdatesTab(context, fromMore),
        ),
        feedScreenModel = feedScreenModel,
    )

    ResultEffect<Unit>(resultKey = TopLevelRoute.Updates.reselectEventKey) {
        navigator.push(DownloadQueueRoute)
    }

    LaunchedEffect(Unit) {
        (context as? MainActivity)?.ready = true
    }
}
