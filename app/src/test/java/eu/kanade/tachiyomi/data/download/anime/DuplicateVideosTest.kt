package eu.kanade.tachiyomi.data.download.anime

import com.hippo.unifile.UniFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

/**
 * An episode directory left with two copies of its video by two jobs downloading it at once must be
 * finished with one of them instead of failing every retry.
 */
class DuplicateVideosTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("cases")
    fun `keeps one copy of the video`(files: Map<String, String>, left: List<String>, @TempDir dir: File) {
        files.forEach { (name, content) -> File(dir, name).writeText(content) }

        UniFile.fromFile(dir)!!.removeDuplicateVideos("Episode 1")

        assertEquals(left.sorted(), dir.list()!!.sorted())
    }

    companion object {
        @JvmStatic
        fun cases() = listOf(
            Arguments.of(mapOf("Episode 1.mkv" to "video"), listOf("Episode 1.mkv")),
            // The episode's own file name wins over a larger copy
            Arguments.of(
                mapOf("Episode 1.mkv" to "video", "Episode 1 (1).mkv" to "longer video"),
                listOf("Episode 1.mkv"),
            ),
            Arguments.of(
                mapOf("Episode 1 (1).mkv" to "video", "Episode 1 (2).mkv" to "longer video"),
                listOf("Episode 1 (2).mkv"),
            ),
            // A partial download and other files are not copies of the video
            Arguments.of(
                mapOf("Episode 1.tmp" to "vid", "Episode 10.mkv" to "video"),
                listOf("Episode 1.tmp", "Episode 10.mkv"),
            ),
        )
    }
}
