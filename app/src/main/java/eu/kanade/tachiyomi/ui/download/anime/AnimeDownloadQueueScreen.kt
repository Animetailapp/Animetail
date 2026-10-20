package eu.kanade.tachiyomi.ui.download.anime

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
fun AnimeDownloadQueueScreen(
    contentPadding: PaddingValues,
    viewModel: AnimeDownloadQueueViewModel,
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
            onReorder = { entries -> viewModel.reorder(entries.map { (it as DownloadQueueEntry.Anime).download }) },
            onMove = { entry, toTop -> viewModel.move((entry as DownloadQueueEntry.Anime).download, toTop) },
            onMoveSeries = { entry, toTop ->
                viewModel.moveSeries((entry as DownloadQueueEntry.Anime).download, toTop)
            },
            onCancel = { viewModel.cancel(listOf((it as DownloadQueueEntry.Anime).download)) },
            onCancelSeries = { viewModel.cancelSeries((it as DownloadQueueEntry.Anime).download) },
            modifier = Modifier.nestedScroll(nestedScrollConnection),
        )
    }
}
