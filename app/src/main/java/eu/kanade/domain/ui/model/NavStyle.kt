package eu.kanade.domain.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.home.TopLevelRoute
import mihon.app.di.appGraph
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun currentNavigationStyle(): NavStyle = LocalContext.current.appGraph.uiPreferences.navStyle.collectAsState().value

enum class NavStyle(
    val titleRes: StringResource,
    val moreTab: TopLevelRoute,
) {
    MOVE_MANGA_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_manga, moreTab = TopLevelRoute.MangaLibrary),
    MOVE_UPDATES_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_updates, moreTab = TopLevelRoute.Updates),
    MOVE_HISTORY_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_history, moreTab = TopLevelRoute.History),
    MOVE_BROWSE_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_browse, moreTab = TopLevelRoute.Browse),
    ;

    val moreIcon: ImageVector
        @Composable
        get() = when (this) {
            MOVE_MANGA_TO_MORE -> Icons.Outlined.CollectionsBookmark
            MOVE_UPDATES_TO_MORE -> ImageVector.vectorResource(id = R.drawable.ic_updates_outline_24dp)
            MOVE_HISTORY_TO_MORE -> Icons.Outlined.History
            MOVE_BROWSE_TO_MORE -> Icons.Outlined.Explore
        }

    val tabs: List<TopLevelRoute>
        get() {
            return mutableListOf(
                TopLevelRoute.Home,
                TopLevelRoute.AnimeLibrary,
                TopLevelRoute.MangaLibrary,
                TopLevelRoute.Updates,
                TopLevelRoute.History,
                TopLevelRoute.Browse,
                TopLevelRoute.More,
            ).apply { remove(this@NavStyle.moreTab) }
        }
}
