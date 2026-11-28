package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class HosterCacheTest {

    private var now = 0L
    private var hosterTtl = 10.minutes
    private var videoTtl = 5.minutes
    private val cache = HosterCache(
        hosterTtl = { hosterTtl },
        videoTtl = { videoTtl },
        maxEpisodes = 2,
        clock = { now },
    )

    private val voe = Hoster(hosterUrl = "https://voe.sx/e/1", hosterName = "SUB Voe")
    private val byse = Hoster(hosterUrl = "https://byse.example/e/1", hosterName = "SUB Byse")
    private val videos = listOf(Video(videoUrl = "https://cdn.example/master.m3u8", videoTitle = "720p"))

    @Test
    fun `entries expire after the ttl`() {
        cache.putHosters(SOURCE, "/ep/1", listOf(voe))

        now += 9.minutes.inWholeMilliseconds
        assertEquals(listOf(voe), cache.getHosters(SOURCE, "/ep/1"))

        now += 2.minutes.inWholeMilliseconds
        assertNull(cache.getHosters(SOURCE, "/ep/1"))
    }

    @Test
    fun `video lists expire before hoster lists`() {
        cache.putHosters(SOURCE, "/ep/1", listOf(voe))
        cache.putVideos(SOURCE, voe, videos)

        now += 6.minutes.inWholeMilliseconds

        assertEquals(listOf(voe), cache.getHosters(SOURCE, "/ep/1"))
        assertNull(cache.getVideos(SOURCE, voe))
    }

    @Test
    fun `hosters sent with their videos expire like video lists`() {
        val placeholder = Hoster(hosterUrl = "", hosterName = Hoster.NO_HOSTER_LIST, videoList = videos)
        cache.putHosters(SOURCE, "/ep/1", listOf(placeholder))

        now += 4.minutes.inWholeMilliseconds
        assertEquals(listOf(placeholder), cache.getHosters(SOURCE, "/ep/1"))

        now += 2.minutes.inWholeMilliseconds
        assertNull(cache.getHosters(SOURCE, "/ep/1"))
    }

    @Test
    fun `a zero ttl turns off caching for that kind of list only`() {
        videoTtl = Duration.ZERO
        cache.putHosters(SOURCE, "/ep/1", listOf(voe))
        cache.putVideos(SOURCE, voe, videos)

        assertEquals(listOf(voe), cache.getHosters(SOURCE, "/ep/1"))
        assertNull(cache.getVideos(SOURCE, voe))

        hosterTtl = Duration.ZERO
        cache.putHosters(SOURCE, "/ep/2", listOf(voe))

        assertNull(cache.getHosters(SOURCE, "/ep/2"))
    }

    @Test
    fun `clear drops every entry`() {
        cache.putHosters(SOURCE, "/ep/1", listOf(voe))
        cache.putVideos(SOURCE, voe, videos)

        cache.clear()

        assertNull(cache.getHosters(SOURCE, "/ep/1"))
        assertNull(cache.getVideos(SOURCE, voe))
    }

    @Test
    fun `clearing a source keeps the entries of other sources`() {
        cache.putVideos(SOURCE, voe, videos)
        cache.putVideos(SOURCE + 1, voe, videos)

        cache.clearSource(SOURCE)

        assertNull(cache.getVideos(SOURCE, voe))
        assertEquals(videos, cache.getVideos(SOURCE + 1, voe))
    }

    @Test
    fun `empty lists are not cached so failed extractions are retried`() {
        cache.putHosters(SOURCE, "/ep/1", emptyList())
        cache.putVideos(SOURCE, voe, emptyList())

        assertNull(cache.getHosters(SOURCE, "/ep/1"))
        assertNull(cache.getVideos(SOURCE, voe))
    }

    @Test
    fun `invalidating an episode also drops the videos of its hosters`() {
        cache.putHosters(SOURCE, "/ep/1", listOf(voe, byse))
        cache.putVideos(SOURCE, voe, videos)
        cache.putVideos(SOURCE, byse, videos)

        cache.invalidateEpisode(SOURCE, "/ep/1")

        assertNull(cache.getHosters(SOURCE, "/ep/1"))
        assertNull(cache.getVideos(SOURCE, voe))
        assertNull(cache.getVideos(SOURCE, byse))
    }

    @Test
    fun `least recently used episodes are evicted first`() {
        cache.putHosters(SOURCE, "/ep/1", listOf(voe))
        cache.putHosters(SOURCE, "/ep/2", listOf(voe))
        cache.getHosters(SOURCE, "/ep/1")
        cache.putHosters(SOURCE, "/ep/3", listOf(voe))

        assertEquals(listOf(voe), cache.getHosters(SOURCE, "/ep/1"))
        assertNull(cache.getHosters(SOURCE, "/ep/2"))
    }

    @Test
    fun `hosters that differ only in their internal data have their own videos`() {
        // Sources that keep the episode in internalData give every episode a hoster with the
        // same URL and name; each episode must still get its own videos.
        val episode1 = Hoster(hosterName = "Server", internalData = "/watch/1")
        val episode2 = Hoster(hosterName = "Server", internalData = "/watch/2")
        cache.putVideos(SOURCE, episode1, videos)

        assertNull(cache.getVideos(SOURCE, episode2))
        assertEquals(videos, cache.getVideos(SOURCE, Hoster(hosterName = "Server", internalData = "/watch/1")))
    }

    @Test
    fun `entries of different sources do not collide`() {
        cache.putVideos(SOURCE, voe, videos)

        assertNull(cache.getVideos(SOURCE + 1, voe))
    }

    @Test
    fun `entries survive a restart through the store`(@TempDir dir: File) {
        val store = HosterCacheStore(File(dir, "hoster_cache.json"))
        cache.putHosters(SOURCE, "/ep/1", listOf(voe))
        cache.putVideos(SOURCE, voe, videos)
        store.write(cache.toSnapshot())

        val restarted = HosterCache(hosterTtl = { hosterTtl }, videoTtl = { videoTtl }, clock = { now }, store = store)

        assertEquals(listOf("SUB Voe"), restarted.getHosters(SOURCE, "/ep/1")?.map { it.hosterName })
        assertEquals(videos.map { it.videoUrl }, restarted.getVideos(SOURCE, voe)?.map { it.videoUrl })
    }

    @Test
    fun `expired entries are not restored`(@TempDir dir: File) {
        val store = HosterCacheStore(File(dir, "hoster_cache.json"))
        cache.putVideos(SOURCE, voe, videos)
        store.write(cache.toSnapshot())

        now += 6.minutes.inWholeMilliseconds
        val restarted = HosterCache(hosterTtl = { hosterTtl }, videoTtl = { videoTtl }, clock = { now }, store = store)

        assertNull(restarted.getVideos(SOURCE, voe))
    }

    private companion object {
        const val SOURCE = 42L
    }
}
