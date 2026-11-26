package eu.kanade.tachiyomi.ui.player.utils

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * Works out the original (production) language of a show from the data the app already has.
 *
 * The steps are tried in order by [eu.kanade.domain.entries.anime.interactor.GetAnimeAudioLanguage]:
 * 1. [fromMemo]: the extension declares it in `SAnime.memo` under [MEMO_KEY].
 * 2. [fromCountryOfOrigin]: the tracker's country of origin (AniList `countryOfOrigin`).
 * 3. [fromMetadata]: heuristics on the title and on the "Native title:" / "Original name:" lines
 *    extensions append to descriptions.
 */
object OriginalLanguageResolver {

    /**
     * `SAnime.memo` key an extension can set to the show's original language as a BCP 47 tag
     * (`"ja"`, `"zh"`, `"en"`). `SAnime.memo` reserves app-namespaced keys for this kind of data.
     */
    const val MEMO_KEY = "aniyomi.originalLanguage"

    enum class Origin(val key: String) {
        SOURCE("source"),
        TRACKER("tracker"),
        METADATA("metadata"),
        ;

        companion object {
            fun fromKey(key: String) = entries.firstOrNull { it.key == key }
        }
    }

    data class Resolved(val language: String, val origin: Origin) {
        /** Value stored in the anime's `original_language` column. */
        fun encode() = "$language:${origin.key}"

        companion object {
            fun decode(value: String): Resolved? {
                val language = LanguageTags.normalize(value.substringBefore(':')) ?: return null
                val origin = Origin.fromKey(value.substringAfter(':', "")) ?: return null
                return Resolved(language, origin)
            }
        }
    }

    fun fromMemo(memo: JsonObject): String? {
        val value = (memo[MEMO_KEY] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return LanguageTags.normalize(value)
    }

    /** Maps an ISO 3166-1 country of origin, as AniList reports it, to its main language. */
    fun fromCountryOfOrigin(country: String?): String? {
        return when (country?.uppercase(Locale.ROOT)) {
            "JP" -> "ja"
            "CN", "TW", "HK" -> "zh"
            "KR" -> "ko"
            else -> null
        }
    }

    /**
     * Guesses the original language from the [title] and [description] the extension provides.
     *
     * [sourceLanguage] is the extension's language; it is used when a Latin-script original title
     * is the same as the displayed title, which means the show was not retitled for that language.
     */
    fun fromMetadata(title: String, description: String?, sourceLanguage: String?): String? {
        val text = description.orEmpty()
        for (nativeTitle in nativeTitles(text)) {
            when (val script = scriptLanguage(nativeTitle)) {
                null -> if (nativeTitle.equals(title.trim().trim('*', '_').trim(), ignoreCase = true)) {
                    LanguageTags.normalize(sourceLanguage)?.let { return it }
                }

                // Japanese titles can be all kanji (銀魂); kana anywhere else settles it
                "zh" -> return if (containsKana(title) || containsKana(text)) "ja" else script

                else -> return script
            }
        }
        scriptLanguage(title)?.let { script ->
            return if (script == "zh" && containsKana(text)) "ja" else script
        }
        return fromListedLanguages(text)
    }

    /**
     * Language implied by the script of [text]: kana means Japanese, Hangul means Korean and
     * Han characters without either mean Chinese. Returns null for any other script.
     */
    fun scriptLanguage(text: String): String? {
        var han = false
        var hangul = false
        var index = 0
        while (index < text.length) {
            val cp = text.codePointAt(index)
            when (Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> return "ja"
                Character.UnicodeScript.HANGUL -> hangul = true
                Character.UnicodeScript.HAN -> han = true
                else -> {}
            }
            index += Character.charCount(cp)
        }
        return when {
            hangul -> "ko"
            han -> "zh"
            else -> null
        }
    }

    /**
     * Values of the "native/original title" lines extensions put in descriptions, e.g.
     * `Native title: 進撃の巨人` (Miruro), `Original name: 葬送のフリーレン` (StreamingUnity)
     * or `**Original Title:** ...`.
     */
    fun nativeTitles(description: String): List<String> {
        return description.lineSequence()
            .mapNotNull { line -> NATIVE_TITLE_LINE.find(line)?.groupValues?.get(1) }
            .map { it.trim().trim('*', '_').trim() }
            .filter { it.isNotEmpty() && it != "null" }
            .toList()
    }

    private fun containsKana(text: String): Boolean {
        return text.codePoints().anyMatch { cp ->
            val script = Character.UnicodeScript.of(cp)
            script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
        }
    }

    /**
     * A description listing the available audio languages (KickAssAnime: `Available Dub Languages:
     * Japanese, English`) names the original language when exactly one East Asian language is in it.
     */
    private fun fromListedLanguages(description: String): String? {
        val listed = description.lineSequence()
            .mapNotNull { line -> LANGUAGES_LINE.find(line)?.groupValues?.get(1) }
            .firstOrNull()
            ?: return null
        val eastAsian = listed.split(',', '/', '|')
            .mapNotNull { LanguageTags.normalize(it) }
            .filter { it in EAST_ASIAN }
            .distinct()
        return eastAsian.singleOrNull()
    }

    private val EAST_ASIAN = setOf("ja", "zh", "ko")

    private val NATIVE_TITLE_LINE = Regex(
        """^[\s*_\-•]*(?:(?:native|original|japanese)\s+(?:title|name)s?|(?:nome|titolo)\s+originale|""" +
            """t[ií]tulo\s+original)[\s*_]*[:：][\s*_]*(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    private val LANGUAGES_LINE = Regex(
        """^[\s*_\-•]*available\s+(?:dub\s+|audio\s+)?languages?[\s*_]*[:：](.+)$""",
        RegexOption.IGNORE_CASE,
    )
}
