package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.Video
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HosterPriorityTest {

    private val servers = listOf("SUB HLS", "SUB UPNShare", "SUB Voe", "SUB Byse", "SUB MP4Upload")

    @Test
    fun `blank keywords keep the extension order`() {
        val priority = HosterPriority(" , ")

        assertTrue(priority.isEmpty)
        assertEquals(servers, priority.sort(servers) { it })
    }

    @Test
    fun `matched servers go first in keyword order and the rest keep their order`() {
        val sorted = HosterPriority("byse, upnshare").sort(servers) { it }

        assertEquals(
            listOf("SUB Byse", "SUB UPNShare", "SUB HLS", "SUB Voe", "SUB MP4Upload"),
            sorted,
        )
    }

    @Test
    fun `a server matching several keywords takes the earliest one`() {
        val sorted = HosterPriority("Voe, SUB").sort(servers) { it }

        assertEquals("SUB Voe", sorted.first())
        assertEquals(servers - "SUB Voe", sorted.drop(1))
    }

    @Test
    fun `disabled servers go last even when they have priority`() {
        val priority = HosterPriority("HLS, Voe", rawDisabled = "sub hls")

        assertTrue(priority.isDisabled("SUB HLS"))
        assertFalse(priority.isDisabled("DUB HLS"))
        assertEquals(
            listOf("SUB Voe", "SUB UPNShare", "SUB Byse", "SUB MP4Upload", "SUB HLS"),
            priority.sort(servers) { it },
        )
    }

    @Test
    fun `saving one episode keeps the order of other sources`() {
        val merged = HosterPriority.mergeOrder(
            savedOrder = listOf("Okru", "SUB Voe", "Filemoon", "SUB HLS"),
            episodeOrder = listOf("SUB HLS", "SUB Voe"),
        )

        assertEquals(listOf("Okru", "SUB HLS", "SUB Voe", "Filemoon"), merged)
    }

    @Test
    fun `an episode with no saved hosters goes first`() {
        val merged = HosterPriority.mergeOrder(listOf("Okru"), listOf("sub voe", "SUB HLS"))

        assertEquals(listOf("sub voe", "SUB HLS", "Okru"), merged)
    }

    @Test
    fun `disabled hosters of other sources are kept`() {
        val merged = HosterPriority.mergeDisabled(
            savedDisabled = listOf("Okru", "SUB HLS"),
            episodeNames = listOf("SUB HLS", "SUB Voe"),
            episodeDisabled = setOf("SUB Voe"),
        )

        assertEquals(listOf("Okru", "SUB Voe"), merged)
    }

    @Test
    fun `server names are taken from the video titles of extensions without hosters`() {
        assertEquals("Okru", HosterPriority.serverName("Okru:1080p"))
        assertEquals("StreamWish", HosterPriority.serverName("StreamWish - 720p"))
        assertEquals("Mp4Upload", HosterPriority.serverName("Mp4Upload 720p"))
        assertEquals("F1S-TAPE", HosterPriority.serverName("F1S-TAPE"))
    }

    @Test
    fun `disabled servers of an extension without hosters go last`() {
        val videos = listOf("Okru:1080p", "StreamWish - 720p", "Okru:480p", "Uqload").map {
            Video(videoUrl = "https://cdn.example/$it", videoTitle = it)
        }

        val sorted = HosterPriority("uqload", rawDisabled = "Okru").sortVideosWithoutHosters(videos)

        assertEquals(
            listOf("Uqload", "StreamWish - 720p", "Okru:1080p", "Okru:480p"),
            sorted.map { it.videoTitle },
        )
    }
}
