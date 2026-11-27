package eu.kanade.tachiyomi.ui.download.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabContent
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun animeDownloadTab(
    nestedScrollConnection: NestedScrollConnection,
): TabContent {
    val navigator = LocalNavigator.current
    val viewModel = metroViewModel<AnimeDownloadQueueViewModel>()
    val downloadList by viewModel.composeState.collectAsStateWithLifecycle()
    val downloadCount by remember {
        derivedStateOf { downloadList.sumOf { it.downloads.size } }
    }

    return TabContent(
        titleRes = AYMR.strings.label_anime,
        searchEnabled = false,
        content = { contentPadding, _ ->
            AnimeDownloadQueueScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                downloadList = downloadList,
                nestedScrollConnection = nestedScrollConnection,
            )
        },
        badgeNumber = downloadCount,
        navigateUp = navigator::pop,
    )
}
