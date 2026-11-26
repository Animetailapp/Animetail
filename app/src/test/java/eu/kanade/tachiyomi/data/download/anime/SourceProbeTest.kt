package eu.kanade.tachiyomi.data.download.anime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class SourceProbeTest {

    data class Case(
        val description: String,
        val output: String,
        val durationSeconds: Double?,
        val variantProgramId: Int?,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `reads the duration and picks one variant`(case: Case) {
        val probe = SourceProbe.parse(case.output)

        assertEquals(case.durationSeconds, probe.durationSeconds, case.description)
        assertEquals(case.variantProgramId, probe.variantProgramId, case.description)
    }

    companion object {
        // Trimmed ffprobe output for the master playlists below, written with SourceProbe.FFPROBE_ARGS

        // Two variants with their audio muxed in: each program has its own video and audio stream
        private val MUXED_VARIANTS = """
            {
              "programs": [
                { "program_id": 0, "streams": [ { "index": 0 }, { "index": 1 } ] },
                { "program_id": 1, "streams": [ { "index": 2 }, { "index": 3 } ] }
              ],
              "streams": [
                {
                  "index": 0, "codec_type": "video", "width": 640, "height": 360,
                  "tags": { "variant_bitrate": "617674" }
                },
                { "index": 1, "codec_type": "audio", "tags": { "variant_bitrate": "617674" } },
                {
                  "index": 2, "codec_type": "video", "width": 1280, "height": 720,
                  "tags": { "variant_bitrate": "2173556" }
                },
                { "index": 3, "codec_type": "audio", "tags": { "variant_bitrate": "2173556" } }
              ],
              "format": { "duration": "1436.136000" }
            }
        """.trimIndent()

        // Two variants sharing one audio rendition, which is part of both programs
        private val SHARED_AUDIO = """
            {
              "programs": [
                { "program_id": 0, "streams": [ { "index": 0 }, { "index": 1 } ] },
                { "program_id": 1, "streams": [ { "index": 0 }, { "index": 2 } ] }
              ],
              "streams": [
                { "index": 0, "codec_type": "audio" },
                {
                  "index": 1, "codec_type": "video", "width": 1920, "height": 1080,
                  "tags": { "variant_bitrate": "4000000" }
                },
                {
                  "index": 2, "codec_type": "video", "width": 1920, "height": 1080,
                  "tags": { "variant_bitrate": "8000000" }
                }
              ],
              "format": { "duration": "30.000001" }
            }
        """.trimIndent()

        // An MP4 file: no programs
        private val SINGLE_FILE = """
            {
              "programs": [],
              "streams": [
                { "index": 0, "codec_type": "video", "width": 1280, "height": 720 },
                { "index": 1, "codec_type": "audio" }
              ],
              "format": { "duration": "300.000000" }
            }
        """.trimIndent()

        // An MPEG-TS file: one program
        private val SINGLE_PROGRAM = """
            {
              "programs": [ { "program_id": 1, "streams": [ { "index": 0 }, { "index": 1 } ] } ],
              "streams": [
                { "index": 0, "codec_type": "video", "width": 1920, "height": 1080 },
                { "index": 1, "codec_type": "audio" }
              ],
              "format": { "duration": "N/A" }
            }
        """.trimIndent()

        @JvmStatic
        fun cases() = listOf(
            Case(
                // Mapping all video and audio downloaded both variants, and so both sets of segments.
                "variants with muxed audio: the program of the 720p variant",
                output = MUXED_VARIANTS,
                durationSeconds = 1436.136,
                variantProgramId = 1,
            ),
            Case(
                // Same size: the bitrate decides. The shared audio stream must not affect the choice.
                "variants of the same size sharing an audio rendition: the higher bitrate one",
                output = SHARED_AUDIO,
                durationSeconds = 30.000001,
                variantProgramId = 1,
            ),
            Case(
                // Not a master playlist, so the download maps all video and audio as before.
                "a file without programs: no variant",
                output = SINGLE_FILE,
                durationSeconds = 300.0,
                variantProgramId = null,
            ),
            Case(
                // A single program is not a choice between variants; ffprobe also reports no duration here.
                "a file with one program: no variant, unknown duration",
                output = SINGLE_PROGRAM,
                durationSeconds = null,
                variantProgramId = null,
            ),
            Case(
                // ffprobe printed nothing: the download still runs, with indeterminate progress.
                "no output: nothing known",
                output = "",
                durationSeconds = null,
                variantProgramId = null,
            ),
        )
    }
}
