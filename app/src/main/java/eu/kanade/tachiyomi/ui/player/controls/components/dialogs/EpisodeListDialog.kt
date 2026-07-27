package eu.kanade.tachiyomi.ui.player.controls.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.DotSeparatorText
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.util.lang.toRelativeString
import kotlinx.coroutines.delay
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EpisodeListDialog(
    displayMode: Long?,
    currentEpisodeIndex: Int,
    episodeList: List<Episode>,
    dateRelativeTime: Boolean,
    dateFormat: DateTimeFormatter,
    onBookmarkClicked: (Long?, Boolean) -> Unit,
    onFillermarkClicked: (Long?, Boolean) -> Unit,
    onEpisodeClicked: (Long?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val itemScrollIndex = (episodeList.size - currentEpisodeIndex) - 1
    val episodeListState = rememberLazyListState(initialFirstVisibleItemIndex = itemScrollIndex)
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Detectar si estamos en Android TV
    val isAndroidTV = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }

    // Asegurar el foco en la lista de episodios para Android TV
    LaunchedEffect(isAndroidTV) {
        if (isAndroidTV) {
            delay(300) // Incrementar el retraso para asegurar que la UI esté completamente lista
            focusRequester.requestFocus()
        }
    }

    // Intentar nuevamente después de la composición inicial para mayor seguridad
    LaunchedEffect(Unit) {
        if (isAndroidTV) {
            // Intentar enfocar después de la primera composición
            delay(500)
            focusRequester.requestFocus()
        }
    }

    PlayerDialog(
        title = stringResource(AYMR.strings.episodes),
        modifier = Modifier.fillMaxHeight(fraction = 0.8F).fillMaxWidth(fraction = 0.8F),
        onDismissRequest = onDismissRequest,
    ) {
        VerticalFastScroller(
            listState = episodeListState,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .focusRequester(focusRequester),
                state = episodeListState,
            ) {
                items(
                    items = episodeList.reversed(),
                    key = { "episode-${it.id}" },
                    contentType = { "episode" },
                ) { episode ->

                    val isCurrentEpisode = episode.id == episodeList[currentEpisodeIndex].id

                    val title = if (displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                        stringResource(
                            AYMR.strings.display_mode_episode,
                            formatEpisodeNumber(episode.episode_number.toDouble()),
                        )
                    } else {
                        episode.name
                    }

                    val date = (episode.date_upload_override.takeIf { it > 0L } ?: episode.date_upload)
                        .takeIf { it > 0L }
                        ?.let {
                            LocalDate.ofInstant(
                                Instant.ofEpochMilli(it),
                                ZoneId.systemDefault(),
                            ).toRelativeString(
                                context = context,
                                relative = dateRelativeTime,
                                dateFormat = dateFormat,
                            )
                        } ?: ""

                    EpisodeListItem(
                        episode = episode,
                        isCurrentEpisode = isCurrentEpisode,
                        title = title,
                        date = date,
                        onBookmarkClicked = onBookmarkClicked,
                        onFillermarkClicked = onFillermarkClicked,
                        onEpisodeClicked = onEpisodeClicked,
                        isAndroidTV = isAndroidTV,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeListItem(
    episode: Episode,
    isCurrentEpisode: Boolean,
    title: String,
    date: String?,
    onBookmarkClicked: (Long?, Boolean) -> Unit,
    onFillermarkClicked: (Long?, Boolean) -> Unit,
    onEpisodeClicked: (Long?) -> Unit,
    isAndroidTV: Boolean = false,
) {
    var isBookmarked by remember { mutableStateOf(episode.bookmark) }
    var isFillermarked by remember { mutableStateOf(episode.fillermark) }
    var textHeight by remember { mutableStateOf(0) }

    val defaultColor = MaterialTheme.colorScheme.onSurface
    val bookmarkAlpha = if (isBookmarked) 1f else DISABLED_ALPHA
    val bookmarkColor = if (isBookmarked) MaterialTheme.colorScheme.primary else defaultColor
    val fillermarkAlpha = if (isFillermarked) 1f else DISABLED_ALPHA
    val fillermarkColor = if (isFillermarked) MaterialTheme.colorScheme.tertiary else defaultColor

    val tvHighlightColor = Color(0xFF1976D2)

    val episodeColor = when {
        isCurrentEpisode && isAndroidTV -> tvHighlightColor
        isCurrentEpisode -> MaterialTheme.colorScheme.primary
        isBookmarked -> bookmarkColor
        isFillermarked -> fillermarkColor
        else -> defaultColor
    }
    val textAlpha = if (episode.seen) DISABLED_ALPHA else 1f
    val textWeight = if (isCurrentEpisode) FontWeight.Bold else FontWeight.Normal
    val textStyle = if (isCurrentEpisode) FontStyle.Italic else FontStyle.Normal

    val clickBookmark: (Boolean) -> Unit = { bookmarked ->
        episode.bookmark = bookmarked
        isBookmarked = bookmarked
        onBookmarkClicked(episode.id, bookmarked)
    }

    val clickFillermark: (Boolean) -> Unit = { fillermarked ->
        episode.fillermark = fillermarked
        isFillermarked = fillermarked
        onFillermarkClicked(episode.id, fillermarked)
    }

    val backgroundModifier = if (isCurrentEpisode && isAndroidTV) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = { onEpisodeClicked(episode.id) })
            .padding(vertical = MaterialTheme.padding.small)
            .background(Color(0x331976D2))
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = { onEpisodeClicked(episode.id) })
            .padding(vertical = MaterialTheme.padding.small)
    }

    Row(
        modifier = backgroundModifier,
    ) {
        IconButton(onClick = { clickBookmark(!isBookmarked) }) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = bookmarkColor,
                modifier = Modifier
                    .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp })
                    .alpha(bookmarkAlpha),
            )
        }

        IconButton(onClick = { clickFillermark(!isFillermarked) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Label,
                contentDescription = null,
                tint = fillermarkColor,
                modifier = Modifier
                    .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp })
                    .alpha(fillermarkAlpha),
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = episodeColor,
                modifier = Modifier.alpha(textAlpha),
                onTextLayout = { textHeight = it.size.height },
                fontWeight = textWeight,
                fontStyle = textStyle,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (date != null) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = episodeColor,
                        modifier = Modifier.alpha(textAlpha),
                        fontWeight = textWeight,
                        fontStyle = textStyle,
                    )
                    if (episode.scanlator != null) {
                        DotSeparatorText(
                            modifier = Modifier.alpha(textAlpha),
                        )
                    }
                }
                if (episode.scanlator != null) {
                    Text(
                        text = episode.scanlator!!,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = episodeColor,
                        modifier = Modifier.alpha(textAlpha),
                        fontWeight = textWeight,
                        fontStyle = textStyle,
                    )
                }
            }
        }
    }
}
