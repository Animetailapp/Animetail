package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.ui.player.PlayerViewModel.VideoTrack

/**
 * Keeps subtitles in step with the audio for sources that ship one subtitle track per audio track
 * of the same subtitle language, e.g. "English (Japanese Audio)" and "English Dub": with English
 * audio selected, "English Dub" (which follows the dubbed lines) is the one to show.
 *
 * Only acts when the track names say which audio they're for ("<Language> Audio" or
 * "<Language> Dub"); plain "English" subtitles are never switched.
 */
object SubtitlesForAudio {

    /**
     * The subtitle track to switch to from [currentSubtitleId] now that [audio] plays, or null to
     * keep the current one.
     */
    fun subtitleFor(subtitles: List<VideoTrack>, currentSubtitleId: Int, audio: VideoTrack): VideoTrack? {
        val current = subtitles.firstOrNull { it.id == currentSubtitleId } ?: return null
        val currentFor = audioLanguagesNamed(current.name)
        // The current track must be one made for a particular audio track
        if (currentFor.isEmpty()) return null

        val audioLanguage = LanguageTags.normalize(audio.language) ?: LanguageTags.normalize(audio.name)
            ?: return null
        if (audioLanguage in currentFor) return null

        val subtitleLanguage = subtitleLanguage(current) ?: return null
        return subtitles
            .filter { it.id != current.id && subtitleLanguage(it) == subtitleLanguage }
            .singleOrNull { audioLanguage in audioLanguagesNamed(it.name) }
    }

    /** The audio languages a subtitle track's name says it is for. */
    private fun audioLanguagesNamed(name: String): Set<String> =
        AUDIO_MENTION.findAll(name).mapNotNull { LanguageTags.normalize(it.groupValues[1]) }.toSet()

    /** The subtitles' own language: the track's language tag, else the first word of its name. */
    private fun subtitleLanguage(track: VideoTrack): String? =
        LanguageTags.normalize(track.language) ?: LanguageTags.normalize(track.name)

    /** "Japanese Audio", "English Dub", "English dubbed". */
    private val AUDIO_MENTION = Regex("""\b(\p{L}+)\s+(?:audio|dub(?:bed)?)\b""", RegexOption.IGNORE_CASE)
}
