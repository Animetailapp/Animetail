package eu.kanade.presentation.history.anime.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.DotSeparatorText
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.util.lang.toTimestampString
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

private val HistoryItemHeight = 96.dp

@Composable
fun AnimeHistoryItem(
    history: AnimeHistoryWithRelations,
    onClickCover: () -> Unit,
    onClickResume: () -> Unit,
    onClickDelete: () -> Unit,
    onClickFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClickResume)
            .height(HistoryItemHeight)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover.Book(
            modifier = Modifier.fillMaxHeight(),
            data = history.coverData,
            onClick = onClickCover,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small),
        ) {
            val textStyle = MaterialTheme.typography.bodyMedium
            Text(
                text = history.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = textStyle,
            )
            val seenAt = remember { history.seenAt?.toTimestampString() ?: "" }

            var lastSecondSeen: Long? by remember { mutableStateOf(null) }
            var totalSeconds: Long? by remember { mutableStateOf(null) }
            LaunchedEffect(history.episodeId) {
                val getEpisode: GetEpisode = Injekt.get()
                val ep = try { getEpisode.await(history.episodeId) } catch (e: Exception) { null }
                if (ep != null && !ep.seen && ep.lastSecondSeen > 0L) {
                    lastSecondSeen = ep.lastSecondSeen
                    totalSeconds = ep.totalSeconds
                }
            }

            val watchProgress: String? = lastSecondSeen?.let { last ->
                stringResource(
                    AYMR.strings.episode_progress,
                    formatProgress(last),
                    formatProgress(totalSeconds ?: 0L),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (history.episodeNumber > -1) {
                        stringResource(
                            AYMR.strings.recent_anime_time,
                            formatEpisodeNumber(history.episodeNumber),
                            seenAt,
                        )
                    } else {
                        seenAt
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (watchProgress != null) {
                    DotSeparatorText()
                    Text(
                        text = watchProgress,
                        maxLines = 1,
                        color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (!history.coverData.isAnimeFavorite) {
            IconButton(onClick = onClickFavorite) {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(MR.strings.add_to_library),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        IconButton(onClick = onClickDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatProgress(milliseconds: Long): String {
    return if (milliseconds > 3600000L) {
        String.format(
            "%d:%02d:%02d",
            TimeUnit.MILLISECONDS.toHours(milliseconds),
            TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    } else {
        String.format(
            "%d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(milliseconds),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    }
}

@PreviewLightDark
@Composable
private fun HistoryItemPreviews(
    @PreviewParameter(AnimeHistoryWithRelationsProvider::class)
    historyWithRelations: AnimeHistoryWithRelations,
) {
    TachiyomiPreviewTheme {
        Surface {
            AnimeHistoryItem(
                history = historyWithRelations,
                onClickCover = {},
                onClickResume = {},
                onClickDelete = {},
                onClickFavorite = {},
            )
        }
    }
}
