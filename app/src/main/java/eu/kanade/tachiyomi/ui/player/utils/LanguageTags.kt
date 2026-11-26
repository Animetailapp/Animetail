package eu.kanade.tachiyomi.ui.player.utils

import java.util.Locale
import java.util.MissingResourceException

/**
 * Normalizes the language spellings found in containers, extensions and preferences to an
 * ISO 639-1 code, so languages from different places can be compared.
 */
object LanguageTags {

    /** Non-standard codes commonly found in anime releases. */
    private val ALIASES = mapOf(
        "jp" to "ja",
    )

    /** ISO 639-2/B codes, which [Locale.getISO3Language] doesn't return (it returns the /T code). */
    private val ISO_639_2_B = mapOf(
        "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "chi" to "zh", "cze" to "cs",
        "dut" to "nl", "fre" to "fr", "geo" to "ka", "ger" to "de", "gre" to "el", "ice" to "is",
        "mac" to "mk", "mao" to "mi", "may" to "ms", "per" to "fa", "rum" to "ro", "slo" to "sk",
        "tib" to "bo", "wel" to "cy",
    )

    private val ISO3_TO_ISO1: Map<String, String> by lazy {
        Locale.getISOLanguages().mapNotNull { iso1 ->
            iso3(Locale.forLanguageTag(iso1))?.let { it to iso1 }
        }.toMap() + ISO_639_2_B
    }

    private val ENGLISH_NAME_TO_ISO1: Map<String, String> by lazy {
        Locale.getISOLanguages().associateBy { iso1 ->
            Locale.forLanguageTag(iso1).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)
        }
    }

    /**
     * Returns the ISO 639-1 code for [tag], or null when it isn't a recognizable language.
     * Accepts ISO 639-1 (`ja`), ISO 639-2/T and /B (`jpn`, `fre`), BCP 47 with a region
     * (`ja-JP`, `ja_JP`), the non-standard `jp` and English names (`Japanese`, `Spanish (Spain)`).
     */
    fun normalize(tag: String?): String? {
        val trimmed = tag?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val primary = trimmed.split('-', '_', ' ', '(').first().lowercase(Locale.ROOT)
        ALIASES[primary]?.let { return it }
        return when (primary.length) {
            2 -> primary.takeIf { iso3(Locale.forLanguageTag(it)) != null }
            3 -> ISO3_TO_ISO1[primary]
            else -> ENGLISH_NAME_TO_ISO1[primary]
        }
    }

    private fun iso3(locale: Locale): String? {
        return try {
            locale.isO3Language.takeIf { it.isNotEmpty() }
        } catch (_: MissingResourceException) {
            null
        }
    }
}
