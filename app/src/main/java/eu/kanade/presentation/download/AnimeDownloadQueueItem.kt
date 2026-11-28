package eu.kanade.presentation.download

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadItemState
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadQueueSummary
import eu.kanade.tachiyomi.ui.download.anime.DownloadFormat
import eu.kanade.tachiyomi.ui.download.anime.DownloadProgress
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Pause
import mihon.icons.materialsymbols.rounded.Schedule
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Numbers that change every second keep their width. */
@Composable
private fun tabularBodySmall(): TextStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")

/**
 * The text of an anime download queue row: titles, progress bar and what the download is doing.
 */
@Composable
fun AnimeDownloadItemBody(
    title: String,
    episodeName: String,
    state: AnimeDownloadItemState?,
    modifier: Modifier = Modifier,
) {
    val progress = (state as? AnimeDownloadItemState.Downloading)?.progress
    val fraction = when (state) {
        is AnimeDownloadItemState.Downloading -> progress?.fraction
        AnimeDownloadItemState.Finished -> 1f
        else -> null
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (fraction != null && state is AnimeDownloadItemState.Downloading) {
                Text(
                    text = stringResource(AYMR.strings.episode_download_progress, (fraction * 100).toInt()),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
        Text(
            text = episodeName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
        DownloadProgressBar(state, fraction)
        state?.let { StatusText(it) }
    }
}

@Composable
private fun DownloadProgressBar(state: AnimeDownloadItemState?, fraction: Float?) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = MaterialTheme.padding.extraSmall)
    val stalled = (state as? AnimeDownloadItemState.Downloading)?.progress?.stalled == true
    val color = if (stalled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
    val animated by animateFloatAsState(fraction ?: 0f, label = "progress")
    when {
        fraction != null -> LinearProgressIndicator(progress = { animated }, modifier = modifier, color = color)

        // Working, but how far along can't be told: no report yet, or no known size or duration
        state is AnimeDownloadItemState.Downloading || state == AnimeDownloadItemState.Starting ->
            LinearProgressIndicator(modifier = modifier, color = color)
    }
}

@Composable
private fun StatusText(state: AnimeDownloadItemState) {
    val (text, color) = when (state) {
        AnimeDownloadItemState.Queued -> stringResource(TLMR.strings.download_state_queued) to null

        AnimeDownloadItemState.Paused -> stringResource(AYMR.strings.download_paused) to null

        AnimeDownloadItemState.Starting -> stringResource(TLMR.strings.download_state_starting) to null

        is AnimeDownloadItemState.Downloading -> state.progress?.let { progressText(it) to null }
            ?: (stringResource(TLMR.strings.download_state_preparing) to null)

        AnimeDownloadItemState.Unfinished ->
            stringResource(TLMR.strings.download_state_unfinished) to MaterialTheme.colorScheme.tertiary

        AnimeDownloadItemState.Finished ->
            stringResource(TLMR.strings.download_state_finished) to MaterialTheme.colorScheme.primary
    }
    Text(
        text = text,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = tabularBodySmall(),
    )
}

/** "123 MB of ~456 MB · 2.10 MB/s · 3m 05s left", leaving out what isn't known. */
@Composable
private fun progressText(progress: DownloadProgress): String {
    val downloaded = DownloadFormat.bytes(progress.downloadedBytes)
    val size = when {
        progress.totalBytes == null -> downloaded.takeIf { progress.downloadedBytes > 0 }

        progress.totalBytesEstimated -> stringResource(
            TLMR.strings.download_progress_size_estimated,
            downloaded,
            DownloadFormat.bytes(progress.totalBytes),
        )

        else -> stringResource(
            TLMR.strings.download_progress_size,
            downloaded,
            DownloadFormat.bytes(progress.totalBytes),
        )
    }
    val rest = if (progress.stalled) {
        listOf(stringResource(TLMR.strings.download_state_stalled))
    } else {
        listOfNotNull(
            progress.bytesPerSecond?.let(DownloadFormat::speed),
            progress.timeLeft?.let { stringResource(TLMR.strings.download_time_left, DownloadFormat.timeLeft(it)) },
        )
    }
    return (listOfNotNull(size) + rest).joinToString(SEPARATOR)
        .ifEmpty { stringResource(AYMR.strings.downloading) }
}

/** The header of the anime download queue: how many are downloading, how fast, and until when. */
@Composable
fun AnimeDownloadQueueSummaryCard(
    summary: AnimeDownloadQueueSummary,
    modifier: Modifier = Modifier,
) {
    val (icon, headline) = when {
        summary.active > 0 ->
            MaterialSymbols.Rounded.Download to
                stringResource(TLMR.strings.download_summary_active, summary.active, summary.total)

        summary.paused ->
            MaterialSymbols.Rounded.Pause to
                stringResource(TLMR.strings.download_summary_paused, summary.total)

        else -> MaterialSymbols.Rounded.Schedule to stringResource(TLMR.strings.download_summary_queued, summary.total)
    }
    val details = listOfNotNull(
        summary.bytesPerSecond?.takeIf { summary.active > 0 }?.let(DownloadFormat::speed),
        summary.timeLeft?.let { stringResource(TLMR.strings.download_summary_time_left, DownloadFormat.timeLeft(it)) },
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryIcon(icon, MaterialTheme.colorScheme.primary)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = headline, style = MaterialTheme.typography.titleSmall)
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(SEPARATOR),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tabularBodySmall(),
                    )
                }
                if (summary.unfinished > 0) {
                    Text(
                        text = stringResource(TLMR.strings.download_summary_unfinished, summary.unfinished),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryIcon(icon: ImageVector, tint: Color) {
    Icon(imageVector = icon, contentDescription = null, tint = tint)
}

private const val SEPARATOR = " · "

// Previews of each row state and the header, for screenshots

private val previewStates: List<Pair<String, AnimeDownloadItemState>> = listOf(
    "Episode 4 - Queued" to AnimeDownloadItemState.Queued,
    "Episode 6 - Finding a server" to AnimeDownloadItemState.Starting,
    "Episode 7 - Direct download, size known" to AnimeDownloadItemState.Downloading(
        DownloadProgress(
            fraction = 0.42f,
            downloadedBytes = 210_000_000,
            totalBytes = 500_000_000,
            totalBytesEstimated = false,
            bytesPerSecond = 2_400_000.0,
            timeLeft = 2.minutes + 5.seconds,
            stalled = false,
        ),
    ),
    "Episode 8 - HLS, duration based" to AnimeDownloadItemState.Downloading(
        DownloadProgress(
            fraction = 0.63f,
            downloadedBytes = 180_000_000,
            totalBytes = 285_700_000,
            totalBytesEstimated = true,
            bytesPerSecond = 1_350_000.0,
            timeLeft = 12.minutes,
            stalled = false,
        ),
    ),
    "Episode 9 - HLS, no duration" to AnimeDownloadItemState.Downloading(
        DownloadProgress(
            fraction = null,
            downloadedBytes = 54_300_000,
            totalBytes = null,
            totalBytesEstimated = false,
            bytesPerSecond = 980_000.0,
            timeLeft = null,
            stalled = false,
        ),
    ),
    "Episode 10 - Stalled" to AnimeDownloadItemState.Downloading(
        DownloadProgress(
            fraction = 0.18f,
            downloadedBytes = 61_000_000,
            totalBytes = 338_000_000,
            totalBytesEstimated = true,
            bytesPerSecond = 0.0,
            timeLeft = null,
            stalled = true,
        ),
    ),
    "Episode 11 - Paused" to AnimeDownloadItemState.Paused,
    "Episode 12 - Stopped" to AnimeDownloadItemState.Unfinished,
    "Episode 13 - Finished" to AnimeDownloadItemState.Finished,
)

@PreviewLightDark
@Composable
private fun AnimeDownloadItemBodyPreview() {
    TachiyomiPreviewTheme {
        Surface {
            Column {
                AnimeDownloadQueueSummaryCard(
                    AnimeDownloadQueueSummary(
                        total = 14,
                        active = 2,
                        unfinished = 1,
                        bytesPerSecond = 3_750_000.0,
                        timeLeft = 12.minutes,
                        paused = false,
                    ),
                )
                previewStates.forEach { (episode, state) ->
                    AnimeDownloadItemBody(
                        title = "Frieren: Beyond Journey's End",
                        episodeName = episode,
                        state = state,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AnimeDownloadQueueSummaryPreview() {
    TachiyomiPreviewTheme {
        Surface {
            Column {
                AnimeDownloadQueueSummaryCard(
                    AnimeDownloadQueueSummary(14, 0, 0, null, null, paused = true),
                )
                AnimeDownloadQueueSummaryCard(
                    AnimeDownloadQueueSummary(3, 0, 0, null, null, paused = false),
                )
            }
        }
    }
}
