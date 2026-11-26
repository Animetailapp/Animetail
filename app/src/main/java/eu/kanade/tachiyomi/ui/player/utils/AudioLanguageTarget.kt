package eu.kanade.tachiyomi.ui.player.utils

import tachiyomi.domain.entries.anime.model.Anime
import java.util.Locale

/**
 * The audio language the player aims for, for one show.
 *
 * @property languages normalized language codes in order of preference. Track selection and
 *   mpv's `alang` use them as-is; the first entry is the language actually asked for and the rest
 *   are the preferred languages fallback.
 * @property preferOriginal whether the first choice is "the show's original language".
 * @property originalLanguage the show's resolved original language, if known.
 * @property dubLanguage the language a bare "Dub" track name stands for: the extension's language,
 *   when it is a single language.
 */
data class AudioLanguageTarget(
    val languages: List<String>,
    val preferOriginal: Boolean,
    val originalLanguage: String?,
    val dubLanguage: String?,
) {

    /**
     * The per-anime setting to save when the user explicitly picks audio in [language]: the
     * original-language setting when it is the show's original language, else the language itself.
     */
    fun settingForLanguage(language: String): String {
        return if (language == originalLanguage) Anime.AUDIO_LANGUAGE_ORIGINAL else language
    }

    /**
     * The per-anime setting to save when the user explicitly picks an audio track named [label],
     * or null when the label doesn't say which audio it has.
     */
    fun settingForLabel(label: String): String? {
        return when (val audio = classify(label)) {
            LabelAudio.Original -> Anime.AUDIO_LANGUAGE_ORIGINAL
            is LabelAudio.Dub -> (audio.language ?: dubLanguage)?.let(::settingForLanguage)
            is LabelAudio.Language -> settingForLanguage(audio.language)
            LabelAudio.Multi, LabelAudio.Unknown -> null
        }
    }

    internal sealed interface LabelAudio {
        data object Unknown : LabelAudio
        data object Original : LabelAudio
        data object Multi : LabelAudio
        data class Dub(val language: String?) : LabelAudio
        data class Language(val language: String) : LabelAudio
    }

    companion object {
        /**
         * Builds the target for a show.
         *
         * @param animeSetting the anime's `audio_language`: [Anime.AUDIO_LANGUAGE_DEFAULT],
         *   [Anime.AUDIO_LANGUAGE_ORIGINAL] or a language tag.
         * @param originalByDefault whether the global default is the original language.
         * @param preferred the preferred languages setting, normalized.
         */
        fun build(
            animeSetting: String,
            originalByDefault: Boolean,
            preferred: List<String>,
            originalLanguage: String?,
            sourceLanguage: String?,
        ): AudioLanguageTarget {
            val explicit = animeSetting
                .takeUnless { it == Anime.AUDIO_LANGUAGE_DEFAULT || it == Anime.AUDIO_LANGUAGE_ORIGINAL }
                ?.let(LanguageTags::normalize)
            val preferOriginal = explicit == null &&
                (animeSetting == Anime.AUDIO_LANGUAGE_ORIGINAL || originalByDefault)
            val first = explicit ?: originalLanguage?.takeIf { preferOriginal }
            return AudioLanguageTarget(
                languages = (listOfNotNull(first) + preferred).distinct(),
                preferOriginal = preferOriginal,
                originalLanguage = originalLanguage,
                dubLanguage = LanguageTags.normalize(sourceLanguage),
            )
        }

        private val WORD = Regex("""[\p{L}]+""")

        /** Words meaning the original audio track, usually with subtitles. */
        private val ORIGINAL_WORDS = setOf(
            "sub", "subs", "subbed", "softsub", "hardsub", "ssub", "hsub", "raw", "original",
            "vostfr", "vose", "legendado", "subtitulado", "sottotitolato",
        )

        /** Words meaning a dubbed track, with the dub language when the word implies one. */
        private val DUB_WORDS = mapOf(
            "dub" to null,
            "dubbed" to null,
            "dubs" to null,
            "dublado" to "pt",
            "doblado" to "es",
            "latino" to "es",
            "castellano" to "es",
            "vf" to "fr",
        )

        private val MULTI_PHRASES = listOf(Regex("""\bdual[\s-]?audio\b"""), Regex("""\bmulti[\s-]?audio\b"""))

        /** Spellings of languages accepted inside labels, besides full English language names. */
        private val LABEL_LANGUAGE_CODES = mapOf("eng" to "en", "jpn" to "ja", "jap" to "ja", "ita" to "it")

        internal fun classify(label: String): LabelAudio {
            val lower = label.lowercase(Locale.ROOT)
            if (MULTI_PHRASES.any { it.containsMatchIn(lower) }) return LabelAudio.Multi

            val words = WORD.findAll(lower).map { it.value }.toList()
            val isOriginal = words.any { it in ORIGINAL_WORDS }
            val dubWord = words.firstOrNull { it in DUB_WORDS }
            val named = words.mapNotNull { word ->
                LABEL_LANGUAGE_CODES[word] ?: word.takeIf { it.length >= 4 }?.let(::languageForName)
            }.distinct()

            return when {
                // "Hardsub" next to "English" names the subtitles, not the audio
                isOriginal && dubWord == null -> LabelAudio.Original

                dubWord != null && !isOriginal -> LabelAudio.Dub(named.singleOrNull() ?: DUB_WORDS[dubWord])

                dubWord != null -> LabelAudio.Unknown

                named.size == 1 -> LabelAudio.Language(named.single())

                named.size > 1 -> LabelAudio.Multi

                else -> LabelAudio.Unknown
            }
        }

        private fun languageForName(word: String): String? {
            // Only full English names: a short word is too likely to be something else
            val code = LanguageTags.normalize(word) ?: return null
            val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)
            return code.takeIf { name.equals(word, ignoreCase = true) }
        }
    }
}
