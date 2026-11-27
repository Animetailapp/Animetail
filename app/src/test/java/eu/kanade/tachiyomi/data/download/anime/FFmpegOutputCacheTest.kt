package eu.kanade.tachiyomi.data.download.anime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FFmpegOutputCacheTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `finished video is kept for the next attempt`() {
        val cache = FFmpegOutputCache(dir)
        cache.partialFile(7).writeText("video")
        assertNull(cache.finishedFile(7))

        cache.finish(7)

        assertEquals("video", cache.finishedFile(7)?.readText())
    }

    @Test
    fun `cleaning keeps only the finished videos of queued episodes`() {
        val cache = FFmpegOutputCache(dir)
        cache.partialFile(1).writeText("finished, queued")
        cache.finish(1)
        cache.partialFile(2).writeText("finished, removed from the queue")
        cache.finish(2)
        cache.partialFile(3).writeText("interrupted")
        File(dir, "unrelated.tmp").writeText("old")

        cache.clean(keepEpisodeIds = setOf(1, 3))

        assertEquals(listOf("episode_1.mkv"), dir.list()?.toList())
    }
}
