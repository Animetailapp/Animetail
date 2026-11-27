package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.network.HttpException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

/**
 * A failure from the video's server moves the download on to another video right away, while a
 * local failure gets the usual retries: another video wouldn't fix a full disk.
 */
class DownloadVideoFailureTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    fun `tells server failures from local ones`(description: String, error: Throwable, isVideoFailure: Boolean) {
        assertEquals(isVideoFailure, error.isVideoFailure(), description)
    }

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @MethodSource("labels")
    fun `names a video by its hoster and title`(hosterName: String, videoTitle: String, label: String) {
        assertEquals(label, videoLabel(hosterName, videoTitle))
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("reasons")
    fun `shows the cause of a failure`(error: Throwable, reason: String) {
        assertEquals(reason, error.failureReason())
    }

    companion object {
        @JvmStatic
        fun failures() = listOf(
            Arguments.of("Expired link", HttpException(410), true),
            Arguments.of("Stalled server", DownloadStalledException(3.minutes), true),
            Arguments.of(
                "Rejected input",
                FFmpegFailedException("https://x/master.m3u8: Server returned 403 Forbidden (access denied)"),
                true,
            ),
            Arguments.of("Unreadable input", FFmpegFailedException("Invalid data found when processing input"), true),
            Arguments.of("Rejected HLS segments", IncompleteVideoException("Downloaded video is incomplete"), true),
            Arguments.of("Full disk", FFmpegFailedException("Error writing trailer: No space left on device"), false),
            Arguments.of(
                "Unwritable output",
                FFmpegFailedException("/storage/x.tmp: Permission denied"),
                false,
            ),
            Arguments.of("Failed folder move", IOException("Failed to move Episode 1_tmp"), false),
        )

        @JvmStatic
        fun reasons() = listOf(
            Arguments.of(FFmpegFailedException("Server returned 404 Not Found"), "Server returned 404 Not Found"),
            Arguments.of(HttpException(503), "HTTP error 503"),
            Arguments.of(IllegalStateException(), "IllegalStateException"),
        )

        @JvmStatic
        fun labels() = listOf(
            Arguments.of("Server", "720p", "Server - 720p"),
            // Sources often repeat the hoster in the title
            Arguments.of("Voe", "Voe - 1080p", "Voe - 1080p"),
            Arguments.of(Hoster.NO_HOSTER_LIST, "1080p", "1080p"),
            Arguments.of("Server", "", "Server"),
        )
    }
}
