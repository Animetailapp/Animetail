package eu.kanade.tachiyomi.ui.player.cache

import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Downloads the next segments of an HLS playlist into the cache while the current one plays,
 * several at a time. mpv requests segments one by one at playback speed, so on a slow host every
 * segment would otherwise start downloading only when it's needed.
 *
 * Prefetching starts once a segment was fully sent to the player: segments that are only probed
 * when opening a video don't trigger it, and it never competes with the segment being waited for.
 *
 * @param download stores a whole resource in the cache; it may throw, the player then simply
 * downloads that segment itself.
 */
class SegmentPrefetcher(
    private val download: (url: HttpUrl, headers: Headers) -> Unit,
    private val isCached: (url: HttpUrl) -> Boolean,
    private val segmentsAhead: Int = DEFAULT_SEGMENTS_AHEAD,
    parallelism: Int = DEFAULT_PARALLELISM,
) {
    private val executor = Executors.newFixedThreadPool(parallelism) { task ->
        Thread(task, "VideoCachePrefetch").apply { isDaemon = true }
    }
    private val inFlight = ConcurrentHashMap<String, Future<*>>()

    // Position of each known segment in its playlist; only the latest playlists are kept.
    private val positions = object : LinkedHashMap<String, Pair<List<HttpUrl>, Int>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<List<HttpUrl>, Int>>?) =
            size > MAX_KNOWN_SEGMENTS
    }

    fun registerPlaylist(segments: List<HttpUrl>) {
        synchronized(positions) {
            segments.forEachIndexed { index, url -> positions[url.toString()] = segments to index }
        }
    }

    /** Starts downloading the segments that follow [url], if it belongs to a known playlist. */
    fun onSegmentPlayed(url: HttpUrl, headers: Headers) {
        val (segments, index) = synchronized(positions) { positions[url.toString()] } ?: return
        segments.drop(index + 1).take(segmentsAhead).forEach { next ->
            val key = next.toString()
            if (inFlight.containsKey(key) || isCached(next)) return@forEach
            inFlight.computeIfAbsent(key) {
                executor.submit {
                    try {
                        if (!isCached(next)) download(next, headers)
                    } catch (e: Exception) {
                        logcat(LogPriority.DEBUG, e) { "Couldn't prefetch $next" }
                    } finally {
                        inFlight.remove(key)
                    }
                }
            }
        }
    }

    /** Waits for a running prefetch of [url] so the same bytes aren't downloaded twice. */
    fun awaitPrefetch(url: HttpUrl) {
        val future = inFlight[url.toString()] ?: return
        try {
            future.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // Timed out or failed: the caller downloads the segment on its own.
        }
    }

    companion object {
        // About 40 seconds with the usual 10 second segments, close to ExoPlayer's default buffer.
        private const val DEFAULT_SEGMENTS_AHEAD = 4
        private const val DEFAULT_PARALLELISM = 3
        private const val MAX_KNOWN_SEGMENTS = 5000
        private const val MAX_WAIT_SECONDS = 30L
    }
}
