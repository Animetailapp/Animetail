package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.ui.player.PlayerViewModel.VideoTrack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class SubtitlesForAudioTest {

    data class Case(
        val description: String,
        val subtitles: List<VideoTrack>,
        val currentId: Int,
        val audio: VideoTrack,
        val expectedId: Int?,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `subtitles made for an audio track follow the audio`(case: Case) {
        assertEquals(
            case.expectedId,
            SubtitlesForAudio.subtitleFor(case.subtitles, case.currentId, case.audio)?.id,
            case.description,
        )
    }

    companion object {
        private val subsForJapanese = VideoTrack(1, "English (Japanese Audio)", null)
        private val subsForDub = VideoTrack(2, "English Dub", null)
        private val perAudio = listOf(subsForJapanese, subsForDub)

        private val englishAudio = VideoTrack(2, "English", "en")
        private val japaneseAudio = VideoTrack(1, "Japanese", "ja")

        @JvmStatic
        fun cases() = listOf(
            Case(
                "English audio picked while the subtitles for the Japanese audio show",
                perAudio,
                currentId = 1,
                audio = englishAudio,
                expectedId = 2,
            ),
            Case(
                "and back to Japanese audio",
                perAudio,
                currentId = 2,
                audio = japaneseAudio,
                expectedId = 1,
            ),
            Case(
                "subtitles already match the audio",
                perAudio,
                currentId = 2,
                audio = englishAudio,
                expectedId = null,
            ),
            Case(
                "audio known only by its language tag",
                perAudio,
                currentId = 1,
                audio = VideoTrack(5, "", "eng"),
                expectedId = 2,
            ),
            Case(
                "tracks tagged by the container, with ISO 639-2 codes",
                listOf(VideoTrack(3, "Japanese Audio", "eng"), VideoTrack(4, "English Dub", "eng")),
                currentId = 3,
                audio = VideoTrack(6, "", "eng"),
                expectedId = 4,
            ),
            Case(
                "plain subtitles that say nothing about audio are never switched",
                listOf(VideoTrack(1, "English", "en"), subsForDub),
                currentId = 1,
                audio = englishAudio,
                expectedId = null,
            ),
            Case(
                "no sibling in the same subtitle language",
                listOf(subsForJapanese, VideoTrack(3, "Spanish Dub", null)),
                currentId = 1,
                audio = VideoTrack(4, "Spanish", "es"),
                expectedId = null,
            ),
            Case(
                "subtitles off: nothing to follow",
                perAudio,
                currentId = -1,
                audio = englishAudio,
                expectedId = null,
            ),
        )
    }
}
