package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * In-memory cache for the hoster and video lists returned by online sources.
 *
 * Extracting links is the slowest part of opening an episode, and the same lists are requested
 * again when reopening the player, switching back to an episode or downloading it. Hoster lists
 * (the embeds of an episode page) rarely change, so they live for [hosterTtl]. Video lists hold
 * signed stream URLs that stop working after a few hours, so they expire after [videoTtl].
 * A zero TTL turns off caching for that kind of list.
 *
 * Only unresolved lists are stored; [HosterLoader.getResolvedVideo] still runs on every playback.
 */
class HosterCache(
    private val hosterTtl: () -> Duration = { DEFAULT_HOSTER_TTL },
    private val videoTtl: () -> Duration = { DEFAULT_VIDEO_TTL },
    private val maxEpisodes: Int = DEFAULT_MAX_EPISODES,
    private val maxHosters: Int = DEFAULT_MAX_HOSTERS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry<T>(val value: T, val storedAt: Long)

    private val hosters = BoundedMap<String, Entry<List<Hoster>>>(maxEpisodes)
    private val videos = BoundedMap<String, Entry<List<Video>>>(maxHosters)

    fun getHosters(sourceId: Long, episodeUrl: String): List<Hoster>? = synchronized(this) {
        hosters.freshValue(episodeKey(sourceId, episodeUrl), hosterTtl())
    }

    fun putHosters(sourceId: Long, episodeUrl: String, hosterList: List<Hoster>) {
        if (hosterList.isEmpty() || hosterTtl() == Duration.ZERO) return
        synchronized(this) { hosters[episodeKey(sourceId, episodeUrl)] = Entry(hosterList, clock()) }
    }

    fun getVideos(sourceId: Long, hoster: Hoster): List<Video>? = synchronized(this) {
        videos.freshValue(hosterKey(sourceId, hoster), videoTtl())
    }

    fun putVideos(sourceId: Long, hoster: Hoster, videoList: List<Video>) {
        if (videoList.isEmpty() || videoTtl() == Duration.ZERO) return
        synchronized(this) { videos[hosterKey(sourceId, hoster)] = Entry(videoList, clock()) }
    }

    /** Drops the video list of a single [hoster], e.g. after one of its links failed to play. */
    fun invalidateVideos(sourceId: Long, hoster: Hoster) {
        synchronized(this) { videos.remove(hosterKey(sourceId, hoster)) }
    }

    /** Drops the hoster list of an episode along with the video lists of every hoster in it. */
    fun invalidateEpisode(sourceId: Long, episodeUrl: String) {
        synchronized(this) {
            hosters.remove(episodeKey(sourceId, episodeUrl))?.value?.forEach { hoster ->
                videos.remove(hosterKey(sourceId, hoster))
            }
        }
    }

    fun clearSource(sourceId: Long) {
        val prefix = "$sourceId|"
        synchronized(this) {
            hosters.keys.removeAll { it.startsWith(prefix) }
            videos.keys.removeAll { it.startsWith(prefix) }
        }
    }

    fun clear() {
        synchronized(this) {
            hosters.clear()
            videos.clear()
        }
    }

    private fun <T> BoundedMap<String, Entry<T>>.freshValue(key: String, ttl: Duration): T? {
        val entry = get(key) ?: return null
        if (clock() - entry.storedAt < ttl.inWholeMilliseconds) return entry.value
        remove(key)
        return null
    }

    private fun episodeKey(sourceId: Long, episodeUrl: String) = "$sourceId|$episodeUrl"

    // A source lists a hoster's videos from the hoster alone, so the key is every field it can read.
    // URL and name are not enough: some sources reuse one URL for several hosters (e.g. SUB and DUB
    // variants of the same embed), and others leave the URL empty and keep the episode in
    // internalData, so every episode has a hoster with the same URL and name.
    private fun hosterKey(sourceId: Long, hoster: Hoster) =
        listOf(sourceId, hoster.hosterUrl, hoster.hosterName, hoster.internalData, hoster.memo).joinToString("|")

    private class BoundedMap<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > maxSize
    }

    companion object {
        val DEFAULT_HOSTER_TTL = 24.hours
        val DEFAULT_VIDEO_TTL = 2.hours
        private const val DEFAULT_MAX_EPISODES = 50
        private const val DEFAULT_MAX_HOSTERS = 300

        /**
         * Cache shared by the player, the episode options dialog and the downloader, expiring
         * after the durations set in the player settings.
         */
        val shared by lazy {
            val preferences = Injekt.get<PlayerPreferences>()
            val hosterListMinutes = preferences.hosterListCacheMinutes()
            val videoMinutes = preferences.hosterCacheMinutes()
            HosterCache(
                hosterTtl = { hosterListMinutes.get().minutes },
                videoTtl = { videoMinutes.get().minutes },
            )
        }
    }
}
