package eu.kanade.tachiyomi.data.download.anime

import com.hippo.unifile.UniFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

/**
 * The downloader only finalizes an episode, and stops trying other videos, when its directory holds
 * one finished video. A failed attempt leaves its `.tmp` file behind, which must not count.
 */
class FinishedVideoTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("cases")
    fun `counts only finished videos`(files: List<String>, finished: Boolean, @TempDir dir: File) {
        files.forEach { File(dir, it).writeText("video") }

        assertEquals(finished, UniFile.fromFile(dir)!!.hasSingleFinishedVideo())
    }

    companion object {
        @JvmStatic
        fun cases() = listOf(
            Arguments.of(emptyList<String>(), false),
            Arguments.of(listOf("Episode 1.tmp"), false),
            Arguments.of(listOf("Episode 1.mkv"), true),
            Arguments.of(listOf("Episode 1.mkv", "Episode 1.tmp"), true),
            Arguments.of(listOf("Episode 1.mp4"), true),
            Arguments.of(listOf("Episode 1.mkv", "Episode 1 (2).mkv"), false),
            // Only the extension marks a partial download.
            Arguments.of(listOf("Episode tmp.mkv"), true),
        )
    }
}
