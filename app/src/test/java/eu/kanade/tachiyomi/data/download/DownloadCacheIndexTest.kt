package eu.kanade.tachiyomi.data.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DownloadCacheIndexTest {
    private data class Directory(val name: String, val uri: String?)

    @Test
    fun sourceIdsWithTheSameNameShareOneDirectory() {
        val indexed = associateSourceDirectories(
            directories = listOf("Shared", "Other", "Unknown"),
            sourceIdsByDirName = mapOf("shared" to listOf(1L, 2L), "other" to listOf(3L)),
            directoryName = { it },
            createEntry = { Directory(it, it) },
        )

        assertEquals(setOf(1L, 2L, 3L), indexed.keys)
        assertSame(indexed[1L], indexed[2L])
        assertNotSame(indexed[1L], indexed[3L])
    }

    @Test
    fun restoredDirectoriesWithTheSameUriShareOneEntry() {
        val first = Directory("first", "content://shared")
        val duplicate = Directory("second", "content://shared")
        val missing = Directory("missing", null)
        val restored = restoreSharedDirectories(mapOf(1L to first, 2L to duplicate, 3L to missing)) { it.uri }

        assertSame(first, restored[1L])
        assertSame(first, restored[2L])
        assertSame(missing, restored[3L])
    }
}
