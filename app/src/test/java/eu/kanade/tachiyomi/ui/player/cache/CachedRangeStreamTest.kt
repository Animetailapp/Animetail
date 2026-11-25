package eu.kanade.tachiyomi.ui.player.cache

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class CachedRangeStreamTest {

    @TempDir
    lateinit var dir: File

    private val content = ByteArray(25) { it.toByte() }
    private val openedAt = mutableListOf<Long>()

    private fun store(maxBytes: Long = Long.MAX_VALUE) = VideoCacheStore(dir, { maxBytes }, blockSize = 10)

    private fun upstream(offset: Long) = UpstreamBody(
        content.copyOfRange(offset.toInt(), content.size).inputStream(),
        offset,
    )
        .also { openedAt += offset }

    private fun read(resource: VideoCacheStore.Resource, start: Long, end: Long): ByteArray =
        CachedRangeStream(resource, content.size.toLong(), start, end, ::upstream).use { it.readBytes() }

    @Test
    fun `second read of a range comes from disk`() {
        val resource = store().resource("https://cdn.example/video.mp4")

        assertArrayEquals(content.copyOfRange(3, 25), read(resource, 3, 24))
        assertEquals(listOf(0L), openedAt)

        openedAt.clear()
        assertArrayEquals(content.copyOfRange(3, 25), read(resource, 3, 24))
        assertEquals(emptyList<Long>(), openedAt)
    }

    @Test
    fun `download resumes at the first missing block`() {
        val resource = store().resource("https://cdn.example/video.mp4")
        read(resource, 0, 9)
        openedAt.clear()

        assertArrayEquals(content.copyOfRange(5, 25), read(resource, 5, 24))
        assertEquals(listOf(10L), openedAt)
    }

    @Test
    fun `a truncated block is downloaded again`() {
        val resource = store().resource("https://cdn.example/video.mp4")
        read(resource, 0, 24)
        File(dir.listFiles()!!.single(), "1").writeBytes(ByteArray(3))
        openedAt.clear()

        assertNull(resource.readBlock(1, content.size.toLong()))
        assertArrayEquals(content.copyOfRange(10, 20), read(resource, 10, 19))
        assertEquals(listOf(10L), openedAt)
    }

    @Test
    fun `a body that ends early fails instead of returning short data`() {
        val resource = store().resource("https://cdn.example/video.mp4")
        val stream = CachedRangeStream(resource, 40, 0, 39, { UpstreamBody(content.inputStream(), it) })

        assertThrows<IOException> { stream.use { it.readBytes() } }
        assertNull(resource.readBlock(2, 40))
    }

    @Test
    fun `changing the size of a resource drops its blocks`() {
        val resource = store().resource("https://cdn.example/video.mp4")
        resource.writeInfo(CachedResourceInfo(25, "video/mp4"))
        read(resource, 0, 24)

        resource.writeInfo(CachedResourceInfo(30, "video/mp4"))

        assertNull(resource.readBlock(0, 30))
    }

    @Test
    fun `least recently used resources are evicted first`() {
        var now = 0L
        val store = VideoCacheStore(dir, { 25 }, blockSize = 10, clock = { now })
        val old = store.resource("https://cdn.example/old.mp4").apply { touch() }
        CachedRangeStream(old, 20, 0, 19, ::upstream).use { it.readBytes() }
        now = 10_000
        val recent = store.resource("https://cdn.example/recent.mp4").apply { touch() }

        CachedRangeStream(recent, 20, 0, 19, ::upstream).use { it.readBytes() }

        assertNull(old.readBlock(0, 20))
        assertArrayEquals(content.copyOfRange(0, 10), recent.readBlock(0, 20))
    }

    @Test
    fun `end of the resource is reported only when it's read to the last byte`() {
        val resource = store().resource("https://cdn.example/seg-1.ts")
        var ends = 0

        CachedRangeStream(resource, 25, 0, 9, ::upstream, onEndReached = { ends++ }).use { it.readBytes() }
        assertEquals(0, ends)

        CachedRangeStream(resource, 25, 0, 24, ::upstream, onEndReached = { ends++ }).use { it.readBytes() }
        assertEquals(1, ends)
    }

    @Test
    fun `the start of a resource counts as cached once its blocks are stored`() {
        val resource = store().resource("https://cdn.example/video.mp4")
        resource.writeInfo(CachedResourceInfo(25, "video/mp4"))
        read(resource, 0, 9)

        assertEquals(true, resource.hasStart(10))
        assertEquals(false, resource.hasStart(11))
        assertEquals(false, resource.isComplete())
    }
}
