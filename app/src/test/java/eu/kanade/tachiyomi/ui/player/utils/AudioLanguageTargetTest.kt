package eu.kanade.tachiyomi.ui.player.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.domain.entries.anime.model.Anime

class AudioLanguageTargetTest {

    data class BuildCase(
        val description: String,
        val animeSetting: String,
        val originalByDefault: Boolean,
        val originalLanguage: String?,
        val expectedLanguages: List<String>,
        val expectedPreferOriginal: Boolean,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("buildCases")
    fun `target languages`(case: BuildCase) {
        val target = AudioLanguageTarget.build(
            animeSetting = case.animeSetting,
            originalByDefault = case.originalByDefault,
            preferred = listOf("ja", "en"),
            originalLanguage = case.originalLanguage,
            sourceLanguage = "en",
        )

        assertEquals(case.expectedLanguages, target.languages, case.description)
        assertEquals(case.expectedPreferOriginal, target.preferOriginal, case.description)
    }

    data class SaveCase(val label: String, val expected: String?) {
        override fun toString() = "'$label' -> $expected"
    }

    @ParameterizedTest
    @MethodSource("saveCases")
    fun `manual pick is saved as a per-show setting`(case: SaveCase) {
        assertEquals(case.expected, ORIGINAL_JA.settingForLabel(case.label))
    }

    companion object {
        private val ORIGINAL_JA = AudioLanguageTarget(
            languages = listOf("ja", "en"),
            preferOriginal = true,
            originalLanguage = "ja",
            dubLanguage = "en",
        )

        @JvmStatic
        fun buildCases() = listOf(
            BuildCase(
                "Global original, original known: it leads, preferred list is the fallback",
                Anime.AUDIO_LANGUAGE_DEFAULT,
                true,
                "ko",
                listOf("ko", "ja", "en"),
                true,
            ),
            BuildCase(
                "Global original, original unknown: preferred list only",
                Anime.AUDIO_LANGUAGE_DEFAULT,
                true,
                null,
                listOf("ja", "en"),
                true,
            ),
            BuildCase(
                "Global preferred list: the original language is not inserted",
                Anime.AUDIO_LANGUAGE_DEFAULT,
                false,
                "ko",
                listOf("ja", "en"),
                false,
            ),
            BuildCase(
                "Per-show original overrides a preferred-list global",
                Anime.AUDIO_LANGUAGE_ORIGINAL,
                false,
                "zh",
                listOf("zh", "ja", "en"),
                true,
            ),
            BuildCase(
                "Per-show English overrides the original language",
                "en",
                true,
                "ja",
                listOf("en", "ja"),
                false,
            ),
        )

        @JvmStatic
        fun saveCases() = listOf(
            SaveCase("Sub - kiwi - Server 1", Anime.AUDIO_LANGUAGE_ORIGINAL),
            SaveCase("Dub - kiwi - Server 1", "en"),
            SaveCase("Latino 720p", "es"),
            SaveCase("Japanese", Anime.AUDIO_LANGUAGE_ORIGINAL),
            SaveCase("1080p", null),
            SaveCase("Dual Audio 1080p", null),
        )
    }
}
