package eu.kanade.tachiyomi.ui.player.cache

import eu.kanade.tachiyomi.util.lang.Hash
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.IOException
import java.util.UUID

/** Size and type of a cached resource, used to answer range requests without the network. */
@Serializable
data class CachedResourceInfo(val length: Long, val contentType: String?)

/** HLS playlist as last received, with the URL it was finally served from after redirects. */
@Serializable
data class CachedPlaylist(val url: String, val contentType: String?, val body: String)

/**
 * Disk storage for the bytes played through [VideoCacheProxy]. Each resource is split into blocks
 * of [blockSize] bytes, so any range that was played once can be served again from disk, even
 * when only parts of the video were watched.
 *
 * Once [maxBytes] is exceeded, the resources used least recently are deleted. Nothing here
 * throws on I/O problems: an unreadable block is a cache miss and is downloaded again.
 */
class VideoCacheStore(
    private val dir: File,
    private val maxBytes: () -> Long,
    val blockSize: Int = DEFAULT_BLOCK_SIZE,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Computed on first write instead of at startup, the folder can hold thousands of blocks.
    private var usedBytes = -1L

    fun resource(url: String): Resource = Resource(File(dir, Hash.sha256(url)))

    fun sizeBytes(): Long = synchronized(this) { dir.diskUsage() }

    fun clear() {
        synchronized(this) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
            usedBytes = 0
        }
    }

    private fun onBlockWritten(bytes: Long) {
        synchronized(this) {
            if (usedBytes < 0) usedBytes = dir.diskUsage() else usedBytes += bytes
            val limit = maxBytes()
            if (usedBytes <= limit) return

            // Trimmed below the limit so the next writes don't evict on every block.
            val target = limit / 10 * 9
            val oldestFirst = dir.listFiles()?.sortedBy { it.lastModified() }.orEmpty()
            for (resourceDir in oldestFirst) {
                if (usedBytes <= target) break
                usedBytes -= resourceDir.diskUsage()
                resourceDir.deleteRecursively()
            }
        }
    }

    inner class Resource internal constructor(private val resourceDir: File) {

        private val infoFile = File(resourceDir, INFO_FILE)
        private val playlistFile = File(resourceDir, PLAYLIST_FILE)

        fun readInfo(): CachedResourceInfo? {
            if (!infoFile.exists()) return null
            return try {
                json.decodeFromString<CachedResourceInfo>(infoFile.readText())
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Discarding unreadable video cache info" }
                delete()
                null
            }
        }

        /** Replaces the cached blocks when the resource changed size since it was cached. */
        fun writeInfo(info: CachedResourceInfo) {
            val current = readInfo()
            if (current == info) return
            if (current != null) delete()
            writeAtomically(infoFile, json.encodeToString(CachedResourceInfo.serializer(), info).toByteArray())
        }

        fun readPlaylist(): CachedPlaylist? {
            if (!playlistFile.exists()) return null
            return try {
                json.decodeFromString<CachedPlaylist>(playlistFile.readText())
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Discarding unreadable cached playlist" }
                playlistFile.delete()
                null
            }
        }

        fun writePlaylist(playlist: CachedPlaylist) {
            try {
                writeAtomically(playlistFile, json.encodeToString(CachedPlaylist.serializer(), playlist).toByteArray())
            } catch (e: IOException) {
                logcat(LogPriority.WARN, e) { "Couldn't store playlist" }
            }
        }

        /** Marks the resource as recently used so it's evicted last. */
        fun touch() {
            resourceDir.setLastModified(clock())
        }

        val blockSize: Int get() = this@VideoCacheStore.blockSize

        /** Size of block [index] of a resource of [length] bytes; only the last one is shorter. */
        fun blockLength(index: Long, length: Long): Int =
            minOf(blockSize.toLong(), length - index * blockSize).toInt()

        fun hasBlock(index: Long, length: Long): Boolean =
            File(resourceDir, index.toString()).length() == blockLength(index, length).toLong()

        /** Returns the block, or null when it's missing or was not written completely. */
        fun readBlock(index: Long, length: Long): ByteArray? {
            val file = File(resourceDir, index.toString())
            val expectedSize = blockLength(index, length)
            return try {
                file.readBytes().takeIf { it.size == expectedSize }
            } catch (_: IOException) {
                null
            }
        }

        fun writeBlock(index: Long, data: ByteArray, size: Int) {
            try {
                writeAtomically(File(resourceDir, index.toString()), data, size)
                onBlockWritten(size.toLong())
            } catch (e: IOException) {
                logcat(LogPriority.WARN, e) { "Couldn't store video block" }
            }
        }

        fun delete() {
            resourceDir.deleteRecursively()
        }

        // Concurrent connections may store the same block, each one through its own temporary file.
        private fun writeAtomically(file: File, data: ByteArray, size: Int = data.size) {
            resourceDir.mkdirs()
            val tmpFile = File(resourceDir, "${file.name}.${UUID.randomUUID()}.tmp")
            try {
                tmpFile.outputStream().use { it.write(data, 0, size) }
                if (!tmpFile.renameTo(file)) throw IOException("Couldn't rename ${tmpFile.name}")
            } finally {
                tmpFile.delete()
            }
        }
    }

    private fun File.diskUsage(): Long = walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    companion object {
        const val DEFAULT_BLOCK_SIZE = 1024 * 1024
        private const val INFO_FILE = "info.json"
        private const val PLAYLIST_FILE = "playlist.json"
    }
}
