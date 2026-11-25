package eu.kanade.tachiyomi.ui.player.cache

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HlsPlaylistTest {

    private val base = "https://cdn.example/hls/720/index.m3u8?token=abc".toHttpUrl()

    @Test
    fun `segments keys and variants are sent through the proxy`() {
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x1
            #EXT-X-MAP:URI="/init.mp4"
            #EXTINF:4.0,
            seg-1.ts?token=abc

            #EXTINF:4.0,
            https://other.example/seg-2.ts
        """.trimIndent()

        val rewritten = HlsPlaylist.rewrite(playlist, base) { "proxy:$it" }

        assertEquals(
            """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="proxy:https://cdn.example/hls/720/key.bin",IV=0x1
            #EXT-X-MAP:URI="proxy:https://cdn.example/init.mp4"
            #EXTINF:4.0,
            proxy:https://cdn.example/hls/720/seg-1.ts?token=abc

            #EXTINF:4.0,
            proxy:https://other.example/seg-2.ts
            """.trimIndent(),
            rewritten,
        )
    }

    @Test
    fun `non http uris are kept`() {
        val playlist = "#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://key\"\n#EXT-X-KEY:URI=\"data:text/plain;base64,AA==\""

        assertEquals(playlist, HlsPlaylist.rewrite(playlist, base) { "proxy:$it" })
    }

    @Test
    fun `playlists are recognized by type, extension or content`() {
        assertTrue(HlsPlaylist.isPlaylist("application/vnd.apple.mpegurl", "https://a.b/x".toHttpUrl()))
        assertTrue(HlsPlaylist.isPlaylist(null, "https://a.b/master.M3U8?x=1".toHttpUrl()))
        assertFalse(HlsPlaylist.isPlaylist("video/mp4", "https://a.b/video.mp4".toHttpUrl()))
        assertTrue(HlsPlaylist.startsLikePlaylist("\uFEFF#EXTM3U\n#EXT"))
        assertFalse(HlsPlaylist.startsLikePlaylist("\u0000\u0000\u0000 ftyp"))
    }

    @Test
    fun `only finished and master playlists are kept for offline use`() {
        assertTrue(HlsPlaylist.isStatic("#EXTM3U\n#EXTINF:4,\na.ts\n#EXT-X-ENDLIST"))
        assertTrue(HlsPlaylist.isStatic("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n720.m3u8"))
        assertFalse(HlsPlaylist.isStatic("#EXTM3U\n#EXTINF:4,\nlive-1.ts"))
    }

    @Test
    fun `media segments are listed in order and master playlists have none`() {
        val media = "#EXTM3U\n#EXTINF:10,\nseg-1.ts\n#EXTINF:10,\nhttps://other.example/seg-2.ts\n#EXT-X-ENDLIST"
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n720.m3u8"

        assertEquals(
            listOf("https://cdn.example/hls/720/seg-1.ts", "https://other.example/seg-2.ts"),
            HlsPlaylist.mediaSegments(media, base).map { it.toString() },
        )
        assertEquals(emptyList<Any>(), HlsPlaylist.mediaSegments(master, base))
    }
}
