package eu.kanade.tachiyomi.ui.player.loader

import android.app.Application
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/** Hoster and video that last played for an episode, matched by name since indices change. */
@Serializable
data class LastPlayedVideo(val hosterName: String, val videoTitle: String)

/**
 * Remembers the video last played for each episode, so reopening the episode starts on the same
 * hoster and the bytes cached for that video can be reused. Only the most recent episodes are kept.
 */
class LastPlayedVideoStore(
    private val file: File,
    private val maxEpisodes: Int = DEFAULT_MAX_EPISODES,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var entries: LinkedHashMap<String, LastPlayedVideo>? = null

    fun get(sourceId: Long, episodeUrl: String): LastPlayedVideo? = synchronized(this) {
        loadedEntries()[key(sourceId, episodeUrl)]
    }

    fun put(sourceId: Long, episodeUrl: String, video: LastPlayedVideo) {
        synchronized(this) {
            val entries = loadedEntries()
            val key = key(sourceId, episodeUrl)
            if (entries[key] == video) return
            // Re-inserted so the most recently played episodes are the last ones dropped.
            entries.remove(key)
            entries[key] = video
            while (entries.size > maxEpisodes) entries.remove(entries.keys.first())
            write(entries)
        }
    }

    private fun loadedEntries(): LinkedHashMap<String, LastPlayedVideo> {
        entries?.let { return it }
        val read = try {
            if (file.exists()) json.decodeFromString<Map<String, LastPlayedVideo>>(file.readText()) else emptyMap()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Discarding unreadable last played videos" }
            emptyMap()
        }
        return LinkedHashMap(read).also { entries = it }
    }

    private fun write(entries: Map<String, LastPlayedVideo>) {
        try {
            val tmpFile = File(file.parentFile, "${file.name}.tmp")
            tmpFile.writeText(json.encodeToString(entries))
            tmpFile.renameTo(file)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Couldn't save the last played video" }
        }
    }

    private fun key(sourceId: Long, episodeUrl: String) = "$sourceId|$episodeUrl"

    companion object {
        private const val DEFAULT_MAX_EPISODES = 500

        val shared by lazy {
            LastPlayedVideoStore(File(Injekt.get<Application>().filesDir, "last_played_videos.json"))
        }
    }
}
