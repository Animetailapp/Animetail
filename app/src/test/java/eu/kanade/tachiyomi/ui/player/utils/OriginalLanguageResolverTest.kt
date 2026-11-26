package eu.kanade.tachiyomi.ui.player.utils

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class OriginalLanguageResolverTest {

    data class MetadataCase(
        val description: String,
        val title: String,
        val animeDescription: String?,
        val sourceLanguage: String?,
        val expected: String?,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("metadataCases")
    fun `metadata heuristics`(case: MetadataCase) {
        val resolved = OriginalLanguageResolver.fromMetadata(case.title, case.animeDescription, case.sourceLanguage)

        assertEquals(case.expected, resolved, case.description)
    }

    data class CountryCase(val country: String?, val expected: String?) {
        override fun toString() = "$country -> $expected"
    }

    @ParameterizedTest
    @MethodSource("countryCases")
    fun `AniList country of origin`(case: CountryCase) {
        assertEquals(case.expected, OriginalLanguageResolver.fromCountryOfOrigin(case.country))
    }

    @Test
    fun `extension memo declares the language, in any spelling`() {
        val memo = JsonObject(mapOf(OriginalLanguageResolver.MEMO_KEY to JsonPrimitive("jpn")))

        assertEquals("ja", OriginalLanguageResolver.fromMemo(memo))
        assertEquals(null, OriginalLanguageResolver.fromMemo(JsonObject(emptyMap())))
    }

    @Test
    fun `cache value round-trips and rejects garbage`() {
        val resolved = OriginalLanguageResolver.Resolved("ko", OriginalLanguageResolver.Origin.TRACKER)

        assertEquals(resolved, OriginalLanguageResolver.Resolved.decode(resolved.encode()))
        assertEquals(null, OriginalLanguageResolver.Resolved.decode(""))
        assertEquals(null, OriginalLanguageResolver.Resolved.decode("ja:somewhere"))
    }

    companion object {
        private const val SYNOPSIS = "A long synopsis in English that mentions nothing about scripts.\n\n"

        @JvmStatic
        fun metadataCases() = listOf(
            MetadataCase(
                "Miruro appends 'Native title:' with kana",
                "Attack on Titan",
                SYNOPSIS + "Native title: 進撃の巨人\n",
                "en",
                "ja",
            ),
            MetadataCase(
                "Miruro donghua: Han-only native title is Chinese",
                "Link Click",
                SYNOPSIS + "Native title: 时光代理人\n",
                "en",
                "zh",
            ),
            MetadataCase(
                "Hangul native title is Korean",
                "Solo Leveling Webtoon Anime",
                SYNOPSIS + "Native title: 나 혼자만 레벨업\n",
                "en",
                "ko",
            ),
            MetadataCase(
                "All-kanji Japanese title stays Chinese without other evidence - tracker data must win",
                "Gintama",
                SYNOPSIS + "Native title: 銀魂\n",
                "en",
                "zh",
            ),
            MetadataCase(
                "All-kanji native title with kana elsewhere in the description is Japanese",
                "Gintama",
                SYNOPSIS + "Native title: 銀魂\nSynonyms: ぎんたま\n",
                "en",
                "ja",
            ),
            MetadataCase(
                "StreamingUnity 'Original name:' with Japanese",
                "Frieren: Beyond Journey's End",
                "★ 8.9\nPlot\n\nOriginal name: 葬送のフリーレン\nQuality: HD",
                "en",
                "ja",
            ),
            MetadataCase(
                "StreamingUnity western title: original name equals the title, so the source language",
                "Thunderbolts*",
                "Plot\n\nOriginal name: Thunderbolts*\nQuality: HD",
                "en",
                "en",
            ),
            MetadataCase(
                "StreamingUnity Italian site uses 'Nome originale:'",
                "L'attacco dei giganti",
                "Trama\n\nNome originale: 進撃の巨人\nQualità: HD",
                "it",
                "ja",
            ),
            MetadataCase(
                "StreamingUnity with no original name prints 'null'",
                "Some Movie",
                "Plot\n\nOriginal name: null\nQuality: HD",
                "en",
                null,
            ),
            MetadataCase(
                "Latin original name different from the title says nothing",
                "Amelie",
                "Original name: Le Fabuleux Destin d'Amélie Poulain",
                "en",
                null,
            ),
            MetadataCase(
                "Markdown-style label used by several extensions",
                "Your Name.",
                "**Original Title:** 君の名は。\n",
                "en",
                "ja",
            ),
            MetadataCase(
                "Title itself in Japanese script",
                "ぼっち・ざ・ろっく！",
                null,
                "ja",
                "ja",
            ),
            MetadataCase(
                "KickAssAnime lists one East Asian language among the dubs",
                "Shingeki no Kyojin",
                SYNOPSIS + "Available Dub Languages: Japanese, English, Spanish (España)\nSeason: Spring\n",
                "en",
                "ja",
            ),
            MetadataCase(
                "KickAssAnime with Japanese and Korean dubs is ambiguous",
                "Solo Leveling",
                SYNOPSIS + "Available Dub Languages: Japanese, English, Korean\n",
                "en",
                null,
            ),
            MetadataCase(
                "Romaji title and no hints: unknown, so the caller falls back to the preferred list",
                "Sousou no Frieren",
                SYNOPSIS,
                "en",
                null,
            ),
        )

        @JvmStatic
        fun countryCases() = listOf(
            CountryCase("JP", "ja"),
            CountryCase("CN", "zh"),
            CountryCase("TW", "zh"),
            CountryCase("KR", "ko"),
            CountryCase("US", null),
            CountryCase(null, null),
        )
    }
}
