package eu.kanade.tachiyomi.ui.player.cache

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SegmentPrefetcherTest {

    private val segments = (1..6).map { "https://cdn.example/seg-$it.ts".toHttpUrl() }
    private val downloaded: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val cached = mutableSetOf<HttpUrl>()

    @Test
    fun `the segments after the played one are downloaded`() {
        val done = CountDownLatch(2)
        val prefetcher = SegmentPrefetcher(
            download = { url, _ ->
                downloaded += url.encodedPath
                done.countDown()
            },
            isCached = { it in cached },
            segmentsAhead = 3,
        )
        cached += segments[2]
        prefetcher.registerPlaylist(segments)

        prefetcher.onSegmentPlayed(segments[0], Headers.headersOf())

        done.await(5, TimeUnit.SECONDS)
        segments.take(5).forEach(prefetcher::awaitPrefetch)
        assertEquals(setOf("/seg-2.ts", "/seg-4.ts"), downloaded.toSet())
    }

    @Test
    fun `unknown urls and failed downloads are ignored`() {
        val prefetcher = SegmentPrefetcher(
            download = { _, _ -> error("offline") },
            isCached = { false },
        )
        prefetcher.registerPlaylist(segments)

        prefetcher.onSegmentPlayed("https://cdn.example/other.ts".toHttpUrl(), Headers.headersOf())
        prefetcher.onSegmentPlayed(segments[4], Headers.headersOf())
        prefetcher.awaitPrefetch(segments[5])
    }
}
