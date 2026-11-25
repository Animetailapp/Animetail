package eu.kanade.tachiyomi.ui.player.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LanguageTagsTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
        "ja, ja",
        "JA, ja",
        "jpn, ja",
        "ja-JP, ja",
        "ja_JP, ja",
        "jp, ja",
        "Japanese, ja",
        "' en ', en",
        "eng, en",
        "'Spanish (Spain)', es",
        "es-419, es",
        "ger, de",
        "deu, de",
        "fre, fr",
        "chi, zh",
        "zh-Hans, zh",
        "'', ",
        "und, ",
        "xx, ",
        "ninja, ",
        "Commentary, ",
    )
    fun `normalizes language spellings to ISO 639-1`(tag: String?, expected: String?) {
        assertEquals(expected, LanguageTags.normalize(tag))
    }
}
