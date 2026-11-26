package eu.kanade.presentation.entries.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.widget.ListPreferenceDialog
import eu.kanade.tachiyomi.ui.player.settings.DefaultAudioLanguage
import eu.kanade.tachiyomi.ui.player.utils.LanguageTags
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Picks the audio language of one anime: the global default, its original language or a
 * specific language from [LanguageTags.COMMON_AUDIO_LANGUAGES] and the preferred languages.
 */
@Composable
fun AnimeAudioLanguageDialog(
    current: String,
    originalLanguage: String?,
    globalDefault: DefaultAudioLanguage,
    preferredLanguages: List<String>,
    onValueChanged: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val originalLabel = originalLanguage?.let {
        stringResource(TLMR.strings.audio_language_original_detected, LanguageTags.displayName(it))
    } ?: stringResource(TLMR.strings.audio_language_original)
    val defaultLabel = stringResource(
        TLMR.strings.audio_language_default,
        when (globalDefault) {
            DefaultAudioLanguage.Original -> originalLabel

            DefaultAudioLanguage.PreferredLanguages ->
                stringResource(TLMR.strings.pref_player_audio_default_language_preferred)
        },
    )
    val languages = remember(current, preferredLanguages) {
        val explicit = listOfNotNull(
            current.takeUnless { it == Anime.AUDIO_LANGUAGE_DEFAULT || it == Anime.AUDIO_LANGUAGE_ORIGINAL },
        )
        (preferredLanguages + LanguageTags.COMMON_AUDIO_LANGUAGES + explicit).distinct()
    }
    val entries = buildMap {
        put(Anime.AUDIO_LANGUAGE_DEFAULT, defaultLabel)
        put(Anime.AUDIO_LANGUAGE_ORIGINAL, originalLabel)
        languages.forEach { put(it, LanguageTags.displayName(it)) }
    }

    ListPreferenceDialog(
        title = stringResource(TLMR.strings.action_audio_language),
        message = stringResource(TLMR.strings.audio_language_dialog_message),
        value = current,
        entries = entries,
        onValueChange = onValueChanged,
        onDismissRequest = onDismissRequest,
    )
}
