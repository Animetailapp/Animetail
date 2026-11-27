package eu.kanade.tachiyomi.util.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileLogcatLoggerTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `rotation keeps the newest files and drops the oldest`() {
        val current = File(dir, "app.log")
        val rotations = FileLogcatLogger.ROTATED_FILES + 2
        repeat(rotations) { i ->
            current.writeText("file $i")
            FileLogcatLogger.rotate(current)
        }
        current.writeText("current")

        val files = FileLogcatLogger.logFiles(current)

        // Oldest first, as the log viewer and the shared crash log read them
        val kept = (rotations - FileLogcatLogger.ROTATED_FILES until rotations).map { "file $it" }
        assertEquals(kept + "current", files.map(File::readText))
        assertEquals(current, files.last())
    }

    @Test
    fun `log files before the first rotation are just the current one`() {
        val current = File(dir, "app.log")
        current.writeText("current")

        assertEquals(listOf(current), FileLogcatLogger.logFiles(current))
    }
}
