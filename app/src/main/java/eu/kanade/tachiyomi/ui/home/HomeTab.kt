package eu.kanade.tachiyomi.ui.home

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.result.ResultEffect
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.home.HomeFeedScreen
import kotlinx.coroutines.channels.Channel

object HomeTab {
    val openSettingsSheetEvent = Channel<Unit>()
}

@Composable
fun HomeTab() {
    val screenModel = metroViewModel<HomeFeedScreenModel>()
    ResultEffect<Unit>(resultKey = TopLevelRoute.Home.reselectEventKey) {
        HomeTab.openSettingsSheetEvent.trySend(Unit)
    }
    HomeFeedScreen(screenModel = screenModel)
}
