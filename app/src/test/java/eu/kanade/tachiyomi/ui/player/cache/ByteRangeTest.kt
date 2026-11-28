package eu.kanade.tachiyomi.ui.player.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ByteRangeTest {

    @Test
    fun `single ranges are parsed`() {
        assertEquals(ByteRange(0, null), ByteRange.parse("bytes=0-"))
        assertEquals(ByteRange(100, 199), ByteRange.parse("bytes=100-199"))
    }

    @Test
    fun `suffix, multiple and reversed ranges are not supported`() {
        assertNull(ByteRange.parse("bytes=-500"))
        assertNull(ByteRange.parse("bytes=0-1,5-9"))
        assertNull(ByteRange.parse("bytes=9-1"))
    }

    @Test
    fun `end is clamped to the resource`() {
        assertEquals(999, ByteRange(0, 5000).endFor(1000))
        assertEquals(999, null.endFor(1000))
        assertEquals(10, ByteRange(5, 10).endFor(1000))
    }

    @Test
    fun `total length is read from content range`() {
        assertEquals(1000L, ByteRange.totalLength("bytes 0-99/1000"))
        assertNull(ByteRange.totalLength("bytes 0-99/*"))
        assertNull(ByteRange.totalLength(null))
    }
}
