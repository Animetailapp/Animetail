package eu.kanade.tachiyomi.ui.download.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.download.DownloadQueueList
import eu.kanade.tachiyomi.ui.download.DownloadQueueEntry
import eu.kanade.tachiyomi.ui.download.DownloadQueueSection
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
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
            onRetry = { viewModel.retry((it as DownloadQueueEntry.Anime).download) },
            onRetryWithOtherVideo = { viewModel.showOtherVideos((it as DownloadQueueEntry.Anime).download) },
        )
    }

    val otherVideos by viewModel.otherVideos.collectAsStateWithLifecycle()
    otherVideos?.let {
        OtherVideosDialog(
            otherVideos = it,
            onSelect = viewModel::retryWithVideo,
            onDismissRequest = viewModel::dismissOtherVideos,
        )
    }
}

@Composable
private fun OtherVideosDialog(
    otherVideos: AnimeDownloadQueueViewModel.OtherVideos,
    onSelect: (AnimeDownloadQueueViewModel.OtherVideo) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(TLMR.strings.action_retry_with_other_video)) },
        text = {
            val videos = otherVideos.videos
            when {
                videos == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                videos.isEmpty() -> Text(otherVideos.error ?: stringResource(TLMR.strings.download_no_other_videos))

                else -> Column {
                    Text(
                        text = otherVideos.download.episode.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LazyColumn {
                        items(videos) { video ->
                            Text(
                                text = video.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(video) }
                                    .padding(vertical = MaterialTheme.padding.medium),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
