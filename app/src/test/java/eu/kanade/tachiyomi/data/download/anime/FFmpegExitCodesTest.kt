package eu.kanade.tachiyomi.data.download.anime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class FFmpegExitCodesTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("cases")
    fun `explains FFmpeg exit codes`(code: Int, text: String?) {
        assertEquals(text, ffmpegExitCodeText(code))
    }

    companion object {
        // The values FFmpeg's AVERROR_* macros expand to
        @JvmStatic
        fun cases() = listOf(
            Arguments.of(-1094995529, "Invalid data found when processing input"),
            Arguments.of(-541478725, "End of file"),
            Arguments.of(-858797304, "Server returned 403 Forbidden"),
            Arguments.of(-875574520, "Server returned 404 Not Found"),
            Arguments.of(-28, "No space left on device"),
            Arguments.of(1, null),
        )
    }
}
