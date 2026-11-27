package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.home.TopLevelRoute
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

enum class StartScreen(val titleRes: StringResource, val tab: TopLevelRoute) {
    HOME(MR.strings.label_home, TopLevelRoute.Home),
    ANIME(AYMR.strings.label_anime, TopLevelRoute.AnimeLibrary),
    MANGA(AYMR.strings.manga, TopLevelRoute.MangaLibrary),
    UPDATES(MR.strings.label_recent_updates, TopLevelRoute.Updates),
    HISTORY(MR.strings.label_recent_manga, TopLevelRoute.History),
    BROWSE(MR.strings.browse, TopLevelRoute.Browse),
}
