package eu.kanade.presentation.entries.anime

import androidx.compose.runtime.Composable
import eu.kanade.presentation.more.settings.widget.ListPreferenceDialog
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Picks whether the library update downloads the new episodes of one anime: as the download
 * settings say, always, or never.
 */
@Composable
fun AnimeAutoDownloadDialog(
    current: Long,
    isSeason: Boolean,
    onValueChanged: (Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ListPreferenceDialog(
        title = stringResource(TLMR.strings.action_auto_download_new_episodes),
        message = stringResource(
            if (isSeason) {
                TLMR.strings.auto_download_dialog_message_season
            } else {
                TLMR.strings.auto_download_dialog_message
            },
        ),
        value = current,
        entries = mapOf(
            Anime.AUTO_DOWNLOAD_DEFAULT to stringResource(TLMR.strings.auto_download_default),
            Anime.AUTO_DOWNLOAD_ALWAYS to stringResource(TLMR.strings.auto_download_always),
            Anime.AUTO_DOWNLOAD_NEVER to stringResource(TLMR.strings.auto_download_never),
        ),
        onValueChange = onValueChanged,
        onDismissRequest = onDismissRequest,
    )
}
