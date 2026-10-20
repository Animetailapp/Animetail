package eu.kanade.tachiyomi.ui.download.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import eu.kanade.presentation.download.DownloadQueueList
import eu.kanade.tachiyomi.ui.download.DownloadQueueEntry
import eu.kanade.tachiyomi.ui.download.DownloadQueueSection
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun DownloadQueueScreen(
    contentPadding: PaddingValues,
    screenModel: MangaDownloadQueueViewModel,
    downloadList: List<DownloadQueueSection>,
    nestedScrollConnection: NestedScrollConnection,
) {
    if (downloadList.isEmpty()) {
        EmptyScreen(
            stringRes = MR.strings.information_no_downloads,
            modifier = Modifier.padding(contentPadding),
        )
    } else {
        DownloadQueueList(
            sections = downloadList,
            lazyListState = rememberLazyListState(),
            contentPadding = contentPadding,
            onReorder = { entries -> screenModel.reorder(entries.map { (it as DownloadQueueEntry.Manga).download }) },
            onMove = { entry, toTop -> screenModel.move((entry as DownloadQueueEntry.Manga).download, toTop) },
            onMoveSeries = { entry, toTop ->
                screenModel.moveSeries((entry as DownloadQueueEntry.Manga).download, toTop)
            },
            onCancel = { screenModel.cancel(listOf((it as DownloadQueueEntry.Manga).download)) },
            onCancelSeries = { screenModel.cancelSeries((it as DownloadQueueEntry.Manga).download) },
            modifier = Modifier.nestedScroll(nestedScrollConnection),
        )
    }
}
