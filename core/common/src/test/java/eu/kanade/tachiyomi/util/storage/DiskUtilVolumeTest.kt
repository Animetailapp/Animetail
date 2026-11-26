package eu.kanade.tachiyomi.util.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Folders picked with the system picker are documents of the external storage provider. Their
 * free space is read from the root of the volume their document id names.
 */
class DiskUtilVolumeTest {

    data class Case(
        val description: String,
        val documentId: String,
        val expected: String?,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `finds the volume of a picked folder`(case: Case) {
        assertEquals(case.expected, DiskUtil.getVolumePath(case.documentId, PRIMARY), case.description)
    }

    companion object {
        private const val PRIMARY = "/storage/emulated/0"

        @JvmStatic
        fun cases() = listOf(
            Case("Folder on the shared storage, the usual pick", "primary:Animetail/downloads", PRIMARY),
            Case("Root of the shared storage", "primary:", PRIMARY),
            Case("Documents shortcut of the shared storage", "home:Animetail", PRIMARY),
            Case("SD card or USB drive, named by its volume id", "1A2B-3C4D:Animetail", "/storage/1A2B-3C4D"),
            Case("No volume prefix, so there is nothing to map", "Animetail/downloads", null),
            Case("A volume id that is a path must not leave /storage", "../data:x", null),
        )
    }
}
