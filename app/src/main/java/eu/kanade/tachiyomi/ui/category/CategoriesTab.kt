package eu.kanade.tachiyomi.ui.category

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.category.anime.AnimeCategoryEvent
import eu.kanade.tachiyomi.ui.category.anime.AnimeCategoryScreenModel
import eu.kanade.tachiyomi.ui.category.anime.animeCategoryTab
import eu.kanade.tachiyomi.ui.category.manga.MangaCategoryEvent
import eu.kanade.tachiyomi.ui.category.manga.MangaCategoryScreenModel
import eu.kanade.tachiyomi.ui.category.manga.mangaCategoryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR

data object CategoriesShowMangaEvent

@Composable
fun CategoriesTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val animeCategoryScreenModel = metroViewModel<AnimeCategoryScreenModel>()
    val mangaCategoryScreenModel = metroViewModel<MangaCategoryScreenModel>()
    // KMK -->
    val feedScreenModel = metroViewModel<FeedScreenModel>()
    // KMK <--

    val tabs = persistentListOf(
        animeCategoryTab(),
        mangaCategoryTab(),
    )

    val state = rememberPagerState { tabs.size }

    TabbedScreen(
        titleRes = AYMR.strings.general_categories,
        tabs = tabs,
        state = state,
        // KMK -->
        feedScreenModel = feedScreenModel,
        // KMK <--
    )

    ResultEffect<CategoriesShowMangaEvent> {
        scope.launch { state.scrollToPage(1) }
    }

    LaunchedEffect(Unit) {
        (context as? MainActivity)?.ready = true
    }

    LaunchedEffect(Unit) {
        launch {
            mangaCategoryScreenModel.events.collectLatest { event ->
                if (event is MangaCategoryEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
        launch {
            animeCategoryScreenModel.events.collectLatest { event ->
                if (event is AnimeCategoryEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
