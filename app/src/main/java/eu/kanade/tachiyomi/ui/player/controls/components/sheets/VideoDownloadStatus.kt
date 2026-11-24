package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Download of the episode open in the player, as shown in the qualities sheet.
 *
 * @property videoTitle the video being downloaded, null until the downloader picks one.
 * @property progress percentage from 0 to 100.
 */
data class VideoDownloadState(
    val videoTitle: String?,
    val status: AnimeDownload.State,
    val progress: Int,
) {
    val isActive: Boolean
        get() = status == AnimeDownload.State.QUEUE || status == AnimeDownload.State.DOWNLOADING
}

@Composable
fun VideoDownloadStatus(
    download: VideoDownloadState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DownloadProgress(download = download, modifier = Modifier.weight(1f))
        if (download.isActive) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(MR.strings.action_cancel),
                )
            }
        }
    }
}

@Composable
private fun DownloadProgress(
    download: VideoDownloadState,
    modifier: Modifier = Modifier,
) {
    val isError = download.status == AnimeDownload.State.ERROR

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        // The percentage gets its own slot so a long video title can't push it out of view.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (download.status) {
                    AnimeDownload.State.DOWNLOADING -> stringResource(AYMR.strings.player_download_progress)
                    AnimeDownload.State.ERROR -> stringResource(AYMR.strings.player_download_failed)
                    else -> stringResource(AYMR.strings.player_download_waiting)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (download.status == AnimeDownload.State.DOWNLOADING) {
                Text(
                    text = "${download.progress}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        download.videoTitle?.let { videoTitle ->
            Text(
                text = videoTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (download.status) {
            AnimeDownload.State.DOWNLOADING -> LinearProgressIndicator(
                progress = { download.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )

            AnimeDownload.State.QUEUE -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            else -> {}
        }
    }
}
