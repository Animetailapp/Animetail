package eu.kanade.tachiyomi.util.system

import java.io.File
import java.io.RandomAccessFile

/**
 * One message from the [FileLogcatLogger] file: its header line plus any following lines that do
 * not start a new message (multi-line messages and stack traces).
 */
data class AppLogEntry(
    /** Unique within one [AppLogEntryParser] run, stable while continuation lines are appended. */
    val id: Long,
    /** V, D, I, W, E or A; null for lines that are not a log message (e.g. "messages dropped"). */
    val priority: Char?,
    val tag: String?,
    val text: String,
)

/** Groups log lines into [AppLogEntry]s, keeping only the newest [maxEntries]. */
class AppLogEntryParser(private val maxEntries: Int = MAX_ENTRIES) {

    private val entries = ArrayDeque<AppLogEntry>()
    private var nextId = 0L

    fun reset() {
        entries.clear()
    }

    fun append(lines: Sequence<String>) {
        lines.forEach(::appendLine)
    }

    fun appendLine(line: String) {
        val header = HEADER.find(line)
        val last = entries.lastOrNull()
        when {
            header != null -> add(AppLogEntry(nextId++, header.groupValues[1].single(), header.groupValues[2], line))
            line.startsWith(DROPPED_PREFIX) || last == null -> add(AppLogEntry(nextId++, null, null, line))
            else -> entries[entries.lastIndex] = last.copy(text = last.text + "\n" + line)
        }
    }

    fun entries(): List<AppLogEntry> = entries.toList()

    private fun add(entry: AppLogEntry) {
        entries.addLast(entry)
        if (entries.size > maxEntries) entries.removeFirst()
    }

    companion object {
        const val MAX_ENTRIES = 5000

        private const val DROPPED_PREFIX = "--- "

        // "2026-11-25 23:39:06.411 -0500 23811 main E/StandaloneCoroutine: message"
        private val HEADER =
            Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} [+-]\d{4} \d+ .*? ([VDIWEA])/(.*?): """)
    }
}

/**
 * Incrementally reads the [FileLogcatLogger] files (oldest first). Appends to the newest file are
 * read from the last offset; anything else (rotation, clearing) triggers a full reload.
 */
class AppLogTail(
    private val files: () -> List<File>,
    maxEntries: Int = AppLogEntryParser.MAX_ENTRIES,
) {
    private val parser = AppLogEntryParser(maxEntries)
    private var olderFiles: List<FileState>? = null
    private var newestPath: String? = null
    private var newestOffset = 0L

    private data class FileState(val path: String, val length: Long, val lastModified: Long)

    /** Returns the entries if anything changed since the last call, otherwise null. */
    @Synchronized
    fun poll(): List<AppLogEntry>? {
        val current = files()
        val newest = current.lastOrNull()
        val older = current.dropLast(1).map { FileState(it.path, it.length(), it.lastModified()) }
        val newestLength = newest?.length() ?: 0L

        val fullReload = olderFiles == null ||
            older != olderFiles ||
            newest?.path != newestPath ||
            newestLength < newestOffset
        if (!fullReload && newestLength == newestOffset) return null

        if (fullReload) {
            parser.reset()
            current.dropLast(1).forEach { file ->
                val text = file.readText().removeSuffix("\n")
                if (text.isNotEmpty()) parser.append(text.lineSequence())
            }
            olderFiles = older
            newestPath = newest?.path
            newestOffset = 0L
        }
        val consumed = if (newest != null) readCompleteLines(newest, newestOffset, newestLength) else 0L
        newestOffset += consumed
        return if (fullReload || consumed > 0) parser.entries() else null
    }

    /** Forces the next [poll] to reload everything. */
    @Synchronized
    fun invalidate() {
        olderFiles = null
    }

    /** Parses the complete lines in [from, to) and returns how many bytes they used. */
    private fun readCompleteLines(file: File, from: Long, to: Long): Long {
        if (to <= from) return 0L
        val bytes = ByteArray((to - from).toInt())
        val read = RandomAccessFile(file, "r").use { raf ->
            raf.seek(from)
            var total = 0
            while (total < bytes.size) {
                val n = raf.read(bytes, total, bytes.size - total)
                if (n < 0) break
                total += n
            }
            total
        }
        val end = bytes.lastIndexOf('\n'.code.toByte(), read)
        if (end < 0) return 0L
        parser.append(String(bytes, 0, end, Charsets.UTF_8).lineSequence())
        return end + 1L
    }

    private fun ByteArray.lastIndexOf(byte: Byte, length: Int): Int {
        for (i in length - 1 downTo 0) if (this[i] == byte) return i
        return -1
    }
}
