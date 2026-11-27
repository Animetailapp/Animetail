package eu.kanade.tachiyomi.util.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.RandomAccessFile

class AppLogReaderTest {

    @TempDir
    lateinit var dir: File

    private val error = "2026-11-25 23:39:06.411 -0500 23811 main E/StandaloneCoroutine: failed"
    private val info = "2026-11-25 23:39:07.002 -0500 23811 DefaultDispatcher-worker-2 I/mpv/ffmpeg: a: b"
    private val stackTrace = listOf(
        "java.lang.IllegalStateException: boom",
        "\tat Foo.bar(Foo.kt:1)",
        "Caused by: java.io.IOException",
        "\t... 3 more",
    )

    @Test
    fun `groups a message with its stack trace and parses priority and tag`() {
        val parser = AppLogEntryParser()
        parser.append((listOf(error) + stackTrace + info).asSequence())

        val entries = parser.entries()
        assertEquals(2, entries.size)
        assertEquals('E', entries[0].priority)
        assertEquals("StandaloneCoroutine", entries[0].tag)
        assertEquals((listOf(error) + stackTrace).joinToString("\n"), entries[0].text)
        // The tag ends at the first ": ", so messages containing ": " keep it.
        assertEquals('I', entries[1].priority)
        assertEquals("mpv/ffmpeg", entries[1].tag)
    }

    @Test
    fun `lines outside a message become their own untagged entry`() {
        val parser = AppLogEntryParser()
        parser.append(sequenceOf("\tat orphan.Line(From.kt:1)", info, "--- 3 log messages dropped (queue full)"))

        val entries = parser.entries()
        assertEquals(listOf(null, 'I', null), entries.map { it.priority })
        assertEquals(listOf(null, "mpv/ffmpeg", null), entries.map { it.tag })
    }

    @Test
    fun `keeps only the newest entries`() {
        val parser = AppLogEntryParser(maxEntries = 2)
        parser.append(sequenceOf(error, info, info.replace("a: b", "last")))

        assertEquals(listOf(info, info.replace("a: b", "last")), parser.entries().map { it.text })
    }

    @Test
    fun `tail reads appends, waits for complete lines and reloads after rotation and clear`() {
        val current = File(dir, "app.log")
        val previous = File(dir, "app.log.1")
        val tail = AppLogTail({ listOf(previous, current).filter(File::exists) })

        current.writeText("$error\n")
        assertEquals(listOf(error), tail.poll()!!.map { it.text })
        assertNull(tail.poll(), "unchanged file")

        // A partial write is held back until its newline arrives; the trace joins the open entry.
        current.appendText("${stackTrace[0]}\n${stackTrace[1]}")
        assertEquals(listOf("$error\n${stackTrace[0]}"), tail.poll()!!.map { it.text })
        current.appendText(" still partial")
        assertNull(tail.poll(), "no new complete line")
        current.appendText("\n$info\n")
        assertEquals(
            listOf("$error\n${stackTrace[0]}\n${stackTrace[1]} still partial", info),
            tail.poll()!!.map { it.text },
        )

        // Rotation: the current file becomes the previous one.
        current.renameTo(previous)
        current.writeText("$info\n")
        assertEquals(3, tail.poll()!!.size)

        // Clear, as FileLogcatLogger.clear does it.
        previous.delete()
        RandomAccessFile(current, "rw").use { it.setLength(0) }
        assertEquals(emptyList<AppLogEntry>(), tail.poll())
    }
}
