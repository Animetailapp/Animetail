package eu.kanade.tachiyomi.ui.download.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun Screen.mangaDownloadTab(
    nestedScrollConnection: NestedScrollConnection,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = metroViewModel<MangaDownloadQueueViewModel>()
    val downloadList by screenModel.composeState.collectAsStateWithLifecycle()
    val downloadCount by remember {
        derivedStateOf { downloadList.sumOf { it.downloads.size } }
    }

    return TabContent(
        titleRes = AYMR.strings.label_manga,
        searchEnabled = false,
        content = { contentPadding, _ ->
            DownloadQueueScreen(
                contentPadding = contentPadding,
                screenModel = screenModel,
                downloadList = downloadList,
                nestedScrollConnection = nestedScrollConnection,
            )
        },
        numberTitle = downloadCount,
        navigateUp = navigator::pop,
    )
}
