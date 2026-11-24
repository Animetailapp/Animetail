package eu.kanade.tachiyomi.ui.player.loader

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Hoster and video lists of a [HosterCache], with their lists already serialized, so they can be
 * written to disk.
 */
@Serializable
data class HosterCacheSnapshot(
    val hosters: List<Entry> = emptyList(),
    val videos: List<Entry> = emptyList(),
) {
    @Serializable
    data class Entry(val key: String, val list: String, val storedAt: Long)
}

/**
 * Keeps a [HosterCache] in a JSON file so it survives app restarts. A missing or unreadable file
 * just means an empty cache.
 */
class HosterCacheStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }

    fun read(): HosterCacheSnapshot? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString<HosterCacheSnapshot>(file.readText())
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Discarding unreadable hoster cache" }
            null
        }
    }

    // Written to a temporary file first so a crash mid-write never leaves a truncated cache.
    fun write(snapshot: HosterCacheSnapshot) {
        if (snapshot.hosters.isEmpty() && snapshot.videos.isEmpty()) {
            file.delete()
            return
        }
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        tmpFile.writeText(json.encodeToString(HosterCacheSnapshot.serializer(), snapshot))
        tmpFile.renameTo(file)
    }
}
