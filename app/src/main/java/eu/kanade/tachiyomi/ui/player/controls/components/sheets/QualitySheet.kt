package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.loader.HosterPriority
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

sealed class HosterState(open val name: String) {
    data class Idle(override val name: String) : HosterState(name)
    data class Loading(override val name: String) : HosterState(name)
    data class Error(override val name: String) : HosterState(name)
    data class Ready(
        override val name: String,
        val videoList: List<Video>,
        val videoState: List<Video.State>,
    ) : HosterState(name)

    /** Whether tapping the hoster extracts it again; a hoster that returned no videos is worth retrying. */
    val canReload: Boolean
        get() = this is Idle || this is Error || (this is Ready && videoList.isEmpty())
}

/**
 * Names the hoster priority is edited with: the hosters, or for an extension without hosters the
 * servers named in its video titles.
 */
fun List<HosterState>.priorityNames(): List<String> {
    val placeholder = singleOrNull()?.takeIf { it.name == Hoster.NO_HOSTER_LIST } ?: return map { it.name }
    return (placeholder as? HosterState.Ready)?.videoList.orEmpty()
        .map { HosterPriority.serverName(it.videoTitle) }
        .distinctBy { it.lowercase() }
}

fun HosterState.Ready.getChangedAt(index: Int, newVideo: Video, newState: Video.State): HosterState.Ready {
    return HosterState.Ready(
        name = this.name,
        videoList = this.videoList.mapIndexed { idx, video ->
            if (idx == index) newVideo else video
        },
        videoState = this.videoState.mapIndexed { idx, state ->
            if (idx == index) newState else state
        },
    )
}

@Composable
fun QualitySheet(
    isLoadingHosters: Boolean,
    hosterState: List<HosterState>,
    expandedState: List<Boolean>,
    selectedVideoIndex: Pair<Int, Int>,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    onDownloadVideo: (Int, Int) -> Unit,
    videoDownload: VideoDownloadState?,
    onCancelDownload: () -> Unit,
    onReload: () -> Unit,
    onEditPriority: () -> Unit,
    displayHosters: Pair<Boolean, Boolean>,
    onDismissRequest: () -> Unit,
    dismissSheet: Boolean,
    modifier: Modifier = Modifier,
) {
    PlayerSheet(
        onDismissRequest = {
            onDismissRequest()
        },
        dismissEvent = dismissSheet,
        modifier = modifier,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = MaterialTheme.padding.medium,
                        start = MaterialTheme.padding.medium,
                        end = MaterialTheme.padding.small,
                        bottom = MaterialTheme.padding.extraSmall,
                    ),
            ) {
                Text(
                    text = stringResource(AYMR.strings.player_sheets_qualities_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                if (hosterState.priorityNames().isNotEmpty()) {
                    IconButton(onClick = onEditPriority, enabled = !isLoadingHosters) {
                        Icon(
                            imageVector = Icons.Outlined.SwapVert,
                            contentDescription = stringResource(AYMR.strings.pref_hosters_priority),
                        )
                    }
                }
                IconButton(onClick = onReload, enabled = !isLoadingHosters) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(AYMR.strings.player_hoster_reload),
                    )
                }
            }

            if (videoDownload != null) {
                VideoDownloadStatus(
                    download = videoDownload,
                    onCancel = onCancelDownload,
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                )
            }
            val canDownload = videoDownload?.isActive != true

            AnimatedVisibility(
                visible = isLoadingHosters,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            ) {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.padding.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            val qualitySheetPadding = PaddingValues(
                bottom = MaterialTheme.padding.medium,
                start = MaterialTheme.padding.medium,
                end = MaterialTheme.padding.medium,
            )

            AnimatedVisibility(
                visible = !isLoadingHosters,
                enter = fadeIn() +
                    slideInVertically(
                        initialOffsetY = { it / 2 },
                    ),
                exit = fadeOut() +
                    slideOutVertically(
                        targetOffsetY = { it / 2 },
                    ),
            ) {
                if (hosterState.size == 1 &&
                    hosterState.first().name == Hoster.NO_HOSTER_LIST &&
                    hosterState.first() is HosterState.Ready
                ) {
                    QualitySheetVideoContent(
                        videoList = (hosterState.first() as HosterState.Ready).videoList,
                        videoState = (hosterState.first() as HosterState.Ready).videoState,
                        selectedVideoIndex = selectedVideoIndex.second,
                        onClickVideo = onClickVideo,
                        onDownloadVideo = onDownloadVideo,
                        downloadEnabled = canDownload,
                        modifier = modifier.padding(paddingValues = qualitySheetPadding),
                    )
                } else {
                    QualitySheetHosterContent(
                        hosterState = hosterState,
                        expandedState = expandedState,
                        selectedVideoIndex = selectedVideoIndex,
                        onClickHoster = onClickHoster,
                        onClickVideo = onClickVideo,
                        onDownloadVideo = onDownloadVideo,
                        downloadEnabled = canDownload,
                        displayHosters = displayHosters,
                        modifier = modifier.padding(paddingValues = qualitySheetPadding),
                    )
                }
            }
        }
    }
}

@Composable
fun QualitySheetVideoContent(
    videoList: List<Video>,
    videoState: List<Video.State>,
    selectedVideoIndex: Int,
    onClickVideo: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    onDownloadVideo: ((Int, Int) -> Unit)? = null,
    downloadEnabled: Boolean = true,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        itemsIndexed(videoList) { videoIdx, video ->
            VideoTrack(
                video = video,
                videoState = videoState[videoIdx],
                selected = selectedVideoIndex == videoIdx,
                onClick = { onClickVideo(0, videoIdx) },
                onDownload = onDownloadVideo?.let { { it(0, videoIdx) } },
                downloadEnabled = downloadEnabled,
            )
        }
    }
}

@Composable
fun QualitySheetHosterContent(
    hosterState: List<HosterState>,
    expandedState: List<Boolean>,
    selectedVideoIndex: Pair<Int, Int>,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    displayHosters: Pair<Boolean, Boolean>,
    modifier: Modifier = Modifier,
    onDownloadVideo: ((Int, Int) -> Unit)? = null,
    downloadEnabled: Boolean = true,
) {
    val validHosters = hosterState.withIndex().filter { (_, state) ->
        state is HosterState.Idle ||
            state is HosterState.Loading ||
            (state is HosterState.Ready && state.videoList.isNotEmpty())
    }
    val failedHosters = hosterState.withIndex().filter { (_, state) ->
        state is HosterState.Error
    }
    val emptyHosters = hosterState.withIndex().filter { (_, state) ->
        state is HosterState.Ready && state.videoList.isEmpty()
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        hosterContent(
            hosters = validHosters,
            expandedState = expandedState,
            selectedVideoIndex = selectedVideoIndex,
            onClickHoster = onClickHoster,
            onClickVideo = onClickVideo,
            onDownloadVideo = onDownloadVideo,
            downloadEnabled = downloadEnabled,
        )

        if (displayHosters.first) {
            hosterContent(
                hosters = failedHosters,
                expandedState = expandedState,
                selectedVideoIndex = selectedVideoIndex,
                onClickHoster = onClickHoster,
                onClickVideo = onClickVideo,
                onDownloadVideo = onDownloadVideo,
                downloadEnabled = downloadEnabled,
            )
        }

        if (displayHosters.second) {
            hosterContent(
                hosters = emptyHosters,
                expandedState = expandedState,
                selectedVideoIndex = selectedVideoIndex,
                onClickHoster = onClickHoster,
                onClickVideo = onClickVideo,
                onDownloadVideo = onDownloadVideo,
                downloadEnabled = downloadEnabled,
            )
        }
    }
}

internal fun LazyListScope.hosterContent(
    hosters: List<IndexedValue<HosterState>>,
    expandedState: List<Boolean>,
    selectedVideoIndex: Pair<Int, Int>,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    onDownloadVideo: ((Int, Int) -> Unit)? = null,
    downloadEnabled: Boolean = true,
) {
    hosters.forEach { (hosterIdx, hoster) ->
        val isExpanded = expandedState.getOrNull(hosterIdx) ?: false

        item {
            // Most hosters hold a single video, so a header plus one child row would only repeat it.
            if (hoster is HosterState.Ready && hoster.videoList.size == 1) {
                VideoTrack(
                    video = hoster.videoList.first(),
                    videoState = hoster.videoState.first(),
                    selected = selectedVideoIndex == Pair(hosterIdx, 0),
                    onClick = { onClickVideo(hosterIdx, 0) },
                    onDownload = onDownloadVideo?.let { { it(hosterIdx, 0) } },
                    downloadEnabled = downloadEnabled,
                    hosterName = hoster.name,
                )
                return@item
            }

            HosterTrack(
                hoster = hoster,
                selected = selectedVideoIndex.first == hosterIdx,
                isExpanded = isExpanded,
                onClick = { onClickHoster(hosterIdx) },
            )

            AnimatedVisibility(
                visible = hoster is HosterState.Ready && isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                (hoster as? HosterState.Ready)?.let {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = MaterialTheme.padding.medium),
                    ) {
                        it.videoList.forEachIndexed { videoIdx, video ->
                            VideoTrack(
                                video = video,
                                videoState = hoster.videoState[videoIdx],
                                selected = selectedVideoIndex == Pair(hosterIdx, videoIdx),
                                onClick = { onClickVideo(hosterIdx, videoIdx) },
                                onDownload = onDownloadVideo?.let { { it(hosterIdx, videoIdx) } },
                                downloadEnabled = downloadEnabled,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HosterTrack(
    hoster: HosterState,
    selected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same layout as a single-video row so names and state icons line up.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (hoster) {
                is HosterState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )

                is HosterState.Error -> Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )

                is HosterState.Idle, is HosterState.Ready -> {}
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = hoster.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = when (hoster) {
                is HosterState.Idle -> stringResource(AYMR.strings.player_hoster_tap_to_load)

                is HosterState.Error -> stringResource(AYMR.strings.player_hoster_failed)

                is HosterState.Loading -> null

                is HosterState.Ready -> pluralStringResource(
                    AYMR.plurals.hoster_video_count,
                    hoster.videoList.size,
                    hoster.videoList.size,
                )
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            val icon = when {
                hoster.canReload -> Icons.Default.Refresh

                hoster is HosterState.Ready -> {
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                }

                else -> null
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A playable video. With [hosterName] it stands for a whole single-video hoster and shows the
 * hoster name above the video title.
 */
@Composable
fun VideoTrack(
    video: Video,
    videoState: Video.State,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
    downloadEnabled: Boolean = true,
    hosterName: String? = null,
) {
    val failed = videoState == Video.State.ERROR
    val titleColor = when {
        selected -> MaterialTheme.colorScheme.primary
        failed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (hosterName != null) 56.dp else 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        VideoStateIcon(videoState = videoState, selected = selected)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = hosterName ?: video.videoTitle,
                style = if (hosterName !=
                    null
                ) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (selected) FontWeight.SemiBold else null,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hosterName != null) {
                Text(
                    text = video.videoTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (onDownload != null) {
            IconButton(onClick = onDownload, enabled = downloadEnabled) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = stringResource(MR.strings.action_download),
                )
            }
        }
    }
}

@Composable
private fun VideoStateIcon(
    videoState: Video.State,
    selected: Boolean,
) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            videoState == Video.State.LOAD_VIDEO -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )

            videoState == Video.State.ERROR -> Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )

            selected -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
