package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.os.Process
import android.util.Log
import logcat.LogPriority
import logcat.LogcatLogger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * [LogcatLogger] that appends the app's own log messages to a size-capped file in app storage.
 *
 * The system logcat ring buffer is shared with every other process and is easily flushed by
 * system spam, so app logs written there are often gone by the time a user exports them. This file
 * survives that and process death, and is included in "Share crash logs" (see CrashLogUtil).
 *
 * Writes happen on a dedicated thread. When [QUEUE_CAPACITY] messages are pending, new messages
 * are dropped (and the drop is recorded in the file) instead of blocking the caller.
 */
class FileLogcatLogger(
    private val file: File,
    private val minPriority: LogPriority,
    private val maxFileBytes: Long = MAX_FILE_BYTES,
) : LogcatLogger {

    private class Entry(
        val timeMillis: Long,
        val priority: LogPriority,
        val threadName: String,
        val tag: String,
        val message: String,
    )

    private val queue = LinkedBlockingQueue<Entry>(QUEUE_CAPACITY)
    private val dropped = AtomicInteger()
    private val pid = Process.myPid()

    init {
        thread(name = "FileLogcatLogger", isDaemon = true) { writeLoop() }
    }

    override fun isLoggable(priority: LogPriority, tag: String): Boolean =
        priority.priorityInt >= minPriority.priorityInt

    @Deprecated("Superseded by isLoggable(priority, tag) in logcat")
    override fun isLoggable(priority: LogPriority): Boolean = priority.priorityInt >= minPriority.priorityInt

    override fun log(priority: LogPriority, tag: String, message: String) {
        // mpv messages end with a line break of their own
        val text = message.trimEnd('\n')
        val entry = Entry(System.currentTimeMillis(), priority, Thread.currentThread().name, tag, text)
        if (!queue.offer(entry)) dropped.incrementAndGet()
    }

    private fun writeLoop() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
        var writer: Writer? = null
        while (true) {
            val entry = queue.take()
            try {
                val out = writer ?: openWriter().also { writer = it }
                val droppedCount = dropped.getAndSet(0)
                if (droppedCount > 0) out.write("--- $droppedCount log messages dropped (queue full)\n")
                out.write(
                    "${dateFormat.format(Date(entry.timeMillis))} $pid ${entry.threadName} " +
                        "${entry.priority.name.first()}/${entry.tag}: ${entry.message}\n",
                )
                if (queue.isEmpty()) {
                    out.flush()
                    if (file.length() > maxFileBytes) {
                        out.close()
                        writer = null
                        rotate(file)
                    }
                }
            } catch (e: IOException) {
                runCatching { writer?.close() }
                writer = null
                Log.w(TAG, "Failed to write app log file ${file.absolutePath}", e)
            }
        }
    }

    private fun openWriter(): Writer {
        file.parentFile?.mkdirs()
        return FileOutputStream(file, true).bufferedWriter()
    }

    companion object {
        private const val TAG = "FileLogcatLogger"
        private const val QUEUE_CAPACITY = 10_000

        /** Each log file is capped at this size. */
        private const val MAX_FILE_BYTES = 2L * 1024 * 1024

        /**
         * How many full files are kept besides the current one. With verbose logging, player
         * output can fill a file in minutes, and a failure is often only looked at later.
         */
        internal const val ROTATED_FILES = 4

        fun currentFile(context: Context): File = File(context.filesDir, "logs/app.log")

        /** The [n]th most recent full file: app.log.1 is the newest. */
        private fun rotatedFile(current: File, n: Int): File = File("${current.path}.$n")

        /** Log files from oldest to newest. */
        fun logFiles(context: Context): List<File> = logFiles(currentFile(context))

        internal fun logFiles(current: File): List<File> {
            val rotated = (ROTATED_FILES downTo 1).map { rotatedFile(current, it) }
            return (rotated + current).filter(File::exists)
        }

        /** Makes [current] the newest rotated file, dropping the oldest one. */
        internal fun rotate(current: File) {
            rotatedFile(current, ROTATED_FILES).delete()
            for (n in ROTATED_FILES - 1 downTo 1) {
                val older = rotatedFile(current, n)
                if (older.exists() && !older.renameTo(rotatedFile(current, n + 1))) older.delete()
            }
            if (!current.renameTo(rotatedFile(current, 1))) current.delete()
        }

        /**
         * Empties the log. The current file is truncated rather than deleted: the writer keeps it
         * open in append mode, so its next write lands at the new end.
         */
        fun clear(context: Context) {
            val current = currentFile(context)
            (1..ROTATED_FILES).forEach { rotatedFile(current, it).delete() }
            if (current.exists()) RandomAccessFile(current, "rw").use { it.setLength(0) }
        }
    }
}
