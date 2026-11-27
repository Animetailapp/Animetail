package eu.kanade.tachiyomi.ui.storage

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.storage.anime.animeStorageTab
import eu.kanade.tachiyomi.ui.storage.manga.mangaStorageTab
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun StorageTab() {
    val context = LocalContext.current

    val tabs = persistentListOf(
        animeStorageTab(),
        mangaStorageTab(),
    )
    val state = rememberPagerState { tabs.size }
    // KMK -->
    val feedScreenModel = metroViewModel<FeedScreenModel>()
    // KMK <--

    TabbedScreen(
        titleRes = AYMR.strings.label_storage,
        tabs = tabs,
        state = state,
        feedScreenModel = feedScreenModel,
    )

    LaunchedEffect(Unit) {
        (context as? MainActivity)?.ready = true
    }
}
