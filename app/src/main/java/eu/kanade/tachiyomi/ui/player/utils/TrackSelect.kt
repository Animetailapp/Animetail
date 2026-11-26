package eu.kanade.tachiyomi.ui.player.utils

import androidx.core.os.LocaleListCompat
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.VideoTrack
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import java.util.Locale

@Inject
class TrackSelect(
    private val subtitlePreferences: SubtitlePreferences,
    private val audioPreferences: AudioPreferences,
) {

    fun getPreferredTrackIndex(tracks: List<VideoTrack>, subtitle: Boolean = true): VideoTrack? {
        val prefLangs = if (subtitle) {
            subtitlePreferences.preferredSubLanguages().get()
        } else {
            audioPreferences.preferredAudioLanguages().get()
        }.split(",").filter(String::isNotEmpty).map(String::trim)

        val whitelist = if (subtitle) {
            subtitlePreferences.subtitleWhitelist().get()
        } else {
            ""
        }.split(",").filter(String::isNotEmpty).map(String::trim)

        val blacklist = if (subtitle) {
            subtitlePreferences.subtitleBlacklist().get()
        } else {
            ""
        }.split(",").filter(String::isNotEmpty).map(String::trim)

        val locales = prefLangs.map(Locale::forLanguageTag).ifEmpty {
            listOf(LocaleListCompat.getDefault()[0]!!)
        }

        val chosenLocale = locales.firstOrNull { locale ->
            tracks.any { t -> containsLang(t, locale) }
        } ?: return null

        val filtered = tracks.withIndex()
            .filterNot { (_, track) ->
                blacklist.any { track.name.contains(it, true) }
            }
            .filter { (_, track) ->
                containsLang(track, chosenLocale)
            }

        return filtered.firstOrNull { (_, track) ->
            whitelist.any { track.name.contains(it, true) }
        }?.value ?: filtered.getOrNull(0)?.value
    }

    private fun containsLang(track: VideoTrack, locale: Locale): Boolean {
        val localName = locale.getDisplayName(locale)
        val englishName = locale.getDisplayName(Locale.ENGLISH).substringBefore(" (")
        val language = LanguageTags.normalize(locale.language)

        return track.name.contains(localName, true) ||
            track.name.contains(englishName, true) ||
            (language != null && LanguageTags.normalize(track.language) == language)
    }
}
