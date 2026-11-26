package eu.kanade.tachiyomi.ui.player.loader

import android.content.Context
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.toVideoList
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.cache.isLoopbackUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.util.system.logcat
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.serialize as serializeHosters
import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.serialize as serializeVideos

/**
 * Cache for the hoster and video lists returned by online sources, kept in memory and, with a
 * [store], on disk so it survives app restarts. Entries only go away when they expire or are
 * cleared from the settings.
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
    private val store: HosterCacheStore? = null,
) {
    private class Entry<T>(val value: T, val storedAt: Long)

    private val hosters = BoundedMap<String, Entry<List<Hoster>>>(maxEpisodes)
    private val videos = BoundedMap<String, Entry<List<Video>>>(maxHosters)

    private var restored = store == null
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var writeJob: Job? = null

    fun getHosters(sourceId: Long, episodeUrl: String): List<Hoster>? = synchronized(this) {
        restoreIfNeeded()
        hosters.freshValue(episodeKey(sourceId, episodeUrl), hosterTtl())
    }

    /** Names of the hosters cached for any episode of the source, even expired ones. */
    fun hosterNames(sourceId: Long): List<String> = synchronized(this) {
        restoreIfNeeded()
        val prefix = "$sourceId|"
        hosters.filterKeys { it.startsWith(prefix) }.values
            .flatMap { entry -> entry.value.map { it.hosterName } }
            .distinct()
    }

    fun putHosters(sourceId: Long, episodeUrl: String, hosterList: List<Hoster>) {
        if (hosterList.isEmpty() || hosterTtl() == Duration.ZERO) return
        update { hosters[episodeKey(sourceId, episodeUrl)] = Entry(hosterList, clock()) }
    }

    fun getVideos(sourceId: Long, hoster: Hoster): List<Video>? = synchronized(this) {
        restoreIfNeeded()
        videos.freshValue(hosterKey(sourceId, hoster), videoTtl())
    }

    fun putVideos(sourceId: Long, hoster: Hoster, videoList: List<Video>) {
        if (videoList.isEmpty() || videoTtl() == Duration.ZERO) return
        update { videos[hosterKey(sourceId, hoster)] = Entry(videoList, clock()) }
    }

    /** Drops the video list of a single [hoster], e.g. after one of its links failed to play. */
    fun invalidateVideos(sourceId: Long, hoster: Hoster) {
        update { videos.remove(hosterKey(sourceId, hoster)) }
    }

    /** Drops the hoster list of an episode along with the video lists of every hoster in it. */
    fun invalidateEpisode(sourceId: Long, episodeUrl: String) {
        update {
            hosters.remove(episodeKey(sourceId, episodeUrl))?.value?.forEach { hoster ->
                videos.remove(hosterKey(sourceId, hoster))
            }
        }
    }

    fun clearSource(sourceId: Long) {
        val prefix = "$sourceId|"
        update {
            hosters.keys.removeAll { it.startsWith(prefix) }
            videos.keys.removeAll { it.startsWith(prefix) }
        }
    }

    fun clear() {
        update {
            hosters.clear()
            videos.clear()
        }
    }

    // Videos served from the device itself (e.g. Mega) point to a server that is gone after a
    // restart, so their links would always fail.
    fun toSnapshot(): HosterCacheSnapshot = synchronized(this) {
        restoreIfNeeded()
        HosterCacheSnapshot(
            hosters = hosters
                .filterValues { entry -> entry.value.none { it.videoList.orEmpty().any(Video::isServedLocally) } }
                .map { (key, entry) -> HosterCacheSnapshot.Entry(key, entry.value.serializeHosters(), entry.storedAt) },
            videos = videos
                .filterValues { entry -> entry.value.none(Video::isServedLocally) }
                .map { (key, entry) -> HosterCacheSnapshot.Entry(key, entry.value.serializeVideos(), entry.storedAt) },
        )
    }

    /** Adds the unexpired entries of [snapshot], skipping any list that no longer parses. */
    fun restore(snapshot: HosterCacheSnapshot) = synchronized(this) {
        snapshot.hosters.restoreInto(hosters, hosterTtl()) { it.toHosterList() }
        snapshot.videos.restoreInto(videos, videoTtl()) { it.toVideoList() }
    }

    private fun <T> List<HosterCacheSnapshot.Entry>.restoreInto(
        target: BoundedMap<String, Entry<T>>,
        ttl: Duration,
        parse: (String) -> T,
    ) {
        filter { clock() - it.storedAt < ttl.inWholeMilliseconds }.forEach { stored ->
            try {
                target[stored.key] = Entry(parse(stored.list), stored.storedAt)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Skipping unreadable cached hoster list" }
            }
        }
    }

    // Reading the file is deferred to the first lookup, which runs off the main thread.
    private fun restoreIfNeeded() {
        if (restored) return
        restored = true
        store?.read()?.let(::restore)
    }

    private inline fun update(change: () -> Unit) {
        synchronized(this) {
            restoreIfNeeded()
            change()
            scheduleWrite()
        }
    }

    // Opening an episode stores one list per hoster in a burst, so they're written together.
    private fun scheduleWrite() {
        val store = store ?: return
        writeJob?.cancel()
        writeJob = writeScope.launch {
            delay(WRITE_DELAY)
            store.write(toSnapshot())
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
        private val WRITE_DELAY = 1.seconds

        @Volatile
        private var sharedInstance: HosterCache? = null

        /**
         * Cache shared by the player, the episode options dialog and the downloader, expiring
         * after the durations set in the player settings.
         */
        fun shared(context: Context): HosterCache {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: run {
                    val preferences = context.applicationContext.appGraph.playerPreferences
                    val hosterListMinutes = preferences.hosterListCacheMinutes()
                    val videoMinutes = preferences.hosterCacheMinutes()
                    HosterCache(
                        hosterTtl = { hosterListMinutes.get().minutes },
                        videoTtl = { videoMinutes.get().minutes },
                        store = HosterCacheStore(File(context.applicationContext.cacheDir, "hoster_cache.json")),
                    ).also { sharedInstance = it }
                }
            }
        }
    }
}

private fun Video.isServedLocally() = videoUrl.isLoopbackUrl()
