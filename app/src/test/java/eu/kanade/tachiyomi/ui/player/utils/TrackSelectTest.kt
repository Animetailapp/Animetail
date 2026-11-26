package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.ui.player.PlayerViewModel.VideoTrack
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * Containers and extensions tag the same language in several ways. The preferred audio and
 * subtitle languages must find their track whichever spelling it uses, and must not pick a track
 * in another language.
 */
class TrackSelectTest {

    data class Case(
        val description: String,
        val preferred: String,
        val tracks: List<VideoTrack>,
        val expectedId: Int?,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `picks the audio track in the preferred language`(case: Case) {
        val store = storeWith("pref_audio_lang", case.preferred)
        val trackSelect = TrackSelect(SubtitlePreferences(store), AudioPreferences(store))

        val chosen = trackSelect.getPreferredTrackIndex(listOf(OFF) + case.tracks, subtitle = false)

        assertEquals(case.expectedId, chosen?.id, case.description)
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `picks the subtitle track in the preferred language`(case: Case) {
        val store = storeWith("pref_subtitle_lang", case.preferred)
        val trackSelect = TrackSelect(SubtitlePreferences(store), AudioPreferences(store))

        val chosen = trackSelect.getPreferredTrackIndex(listOf(OFF) + case.tracks)

        assertEquals(case.expectedId, chosen?.id, case.description)
    }

    @Test
    fun `the show's audio languages replace the preferred languages`() {
        val trackSelect = TrackSelect(
            SubtitlePreferences(storeWith("pref_audio_lang", "en")),
            AudioPreferences(storeWith("pref_audio_lang", "en")),
        )
        val tracks = listOf(OFF, VideoTrack(1, "", "eng"), VideoTrack(2, "", "kor"), VideoTrack(3, "", "jpn"))

        assertEquals(
            2,
            trackSelect.getPreferredTrackIndex(tracks, subtitle = false, audioLanguages = listOf("ko", "en"))?.id,
        )
        // Falls through the show's list in order when its first language has no track
        assertEquals(
            3,
            trackSelect.getPreferredTrackIndex(tracks, subtitle = false, audioLanguages = listOf("zh", "ja"))?.id,
        )
        assertEquals(1, trackSelect.getPreferredTrackIndex(tracks, subtitle = false)?.id)
    }

    private fun storeWith(key: String, value: String) =
        InMemoryPreferenceStore(sequenceOf(InMemoryPreferenceStore.InMemoryPreference(key, value, "")))

    companion object {
        private val OFF = VideoTrack(-1, "Off", null)

        @JvmStatic
        fun cases() = listOf(
            Case("ISO 639-2 tags", "ja", listOf(VideoTrack(1, "", "eng"), VideoTrack(2, "", "jpn")), 2),
            Case("ISO 639-1 with a region", "ja", listOf(VideoTrack(1, "", "en-US"), VideoTrack(2, "", "ja-JP")), 2),
            Case("region with an underscore", "ja", listOf(VideoTrack(1, "", "en_US"), VideoTrack(2, "", "ja_JP")), 2),
            Case(
                "non-standard 'jp' from fansub releases",
                "ja",
                listOf(VideoTrack(1, "", "en"), VideoTrack(2, "", "jp")),
                2,
            ),
            Case(
                "tag spelled as the English name",
                "ja",
                listOf(VideoTrack(1, "", "English"), VideoTrack(2, "", "Japanese")),
                2,
            ),
            Case("ISO 639-2/B tag", "de", listOf(VideoTrack(1, "", "eng"), VideoTrack(2, "", "ger")), 2),
            Case(
                "preference written as ISO 639-2",
                "jpn",
                listOf(VideoTrack(1, "", "eng"), VideoTrack(2, "", "ja")),
                2,
            ),
            Case(
                "language only in the track title",
                "ja",
                listOf(VideoTrack(1, "English", null), VideoTrack(2, "Japanese", null)),
                2,
            ),
            Case(
                "first preferred language that has a track wins",
                "ko,en",
                listOf(VideoTrack(1, "", "jpn"), VideoTrack(2, "", "eng")),
                2,
            ),
            Case(
                "no track in the language: no pick",
                "ja",
                listOf(VideoTrack(1, "", "eng"), VideoTrack(2, "", "fre")),
                null,
            ),
            Case("a tag that only contains the code doesn't match", "ja", listOf(VideoTrack(1, "", "ninja")), null),
            Case("unknown tags don't match", "ja", listOf(VideoTrack(1, "", "und"), VideoTrack(2, "", "")), null),
        )
    }
}
