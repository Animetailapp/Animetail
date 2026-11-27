package eu.kanade.tachiyomi.data.download.anime

import fi.iki.elonen.NanoHTTPD
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import kotlin.random.Random

/**
 * FFmpeg's HLS demuxer drops the rest of a segment whose connection is cut, so the downloaded
 * episode skips. FFmpeg reads through the relay, which must hand it complete segments, or record a
 * failure so the download fails instead of saving a video with a gap.
 */
class DownloadStreamRelayTest {

    private val segment = Random(1).nextBytes(256 * 1024)
    private val host = FakeHost(segment)
    private val relay = DownloadStreamRelay(OkHttpClient(), Headers.headersOf("Referer", "https://example.org/"))
    private val ffmpeg = OkHttpClient()

    @AfterEach
    fun afterEach() {
        relay.stop()
        host.stop()
    }

    private fun startAll() {
        host.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
        relay.open()
    }

    private fun get(url: String) = ffmpeg.newCall(Request.Builder().url(relay.relayUrl(url)).build()).execute()

    @Test
    fun `a segment cut halfway is resumed from where it stopped`() {
        startAll()
        host.cutFirstResponse = true

        val body = get("${host.url}/seg005.ts").use { it.body.bytes() }

        body.contentEquals(segment) shouldBe true
        host.requests.map { it.range } shouldBe listOf(null, "bytes=${segment.size / 2}-${segment.size - 1}")
        host.requests.map { it.referer }.distinct() shouldBe listOf("https://example.org/")
        relay.failure.shouldBeNull()
    }

    @Test
    fun `a cut segment the host won't resume fails the download`() {
        startAll()
        host.cutFirstResponse = true
        host.ignoreRanges = true

        assertThrows<IOException> { get("${host.url}/seg005.ts").use { it.body.bytes() } }

        relay.failure.shouldNotBeNull()
    }

    @Test
    fun `a segment the host refuses fails the download`() {
        startAll()

        get("${host.url}/missing.ts").use { it.code shouldBe 404 }

        relay.failure.shouldNotBeNull()
    }

    @Test
    fun `playlists are rewritten so their segments come through the relay`() {
        startAll()

        val playlist = get("${host.url}/hls/index.m3u8").use { it.body.string() }
        val segmentUrl = playlist.lines().single { it.startsWith("http") }

        segmentUrl.toHttpUrl().host shouldBe "127.0.0.1"
        segmentUrl.toHttpUrl().pathSegments.last() shouldBe "seg005.ts"
        ffmpeg.newCall(Request.Builder().url(segmentUrl).build()).execute()
            .use { it.body.bytes() }.contentEquals(segment) shouldBe true
    }

    @Test
    fun `every URI of a playlist is rewritten, except ones that aren't http`() {
        val base = "https://cdn.example.org/show/ep1/index.m3u8".toHttpUrl()
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x1
            #EXT-X-MAP:URI="/init.mp4"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",URI="https://audio.example.org/ja.m3u8"
            #EXT-X-SESSION-KEY:METHOD=AES-128,URI="data:text/plain;base64,AAAA"
            #EXTINF:6.0,
            seg000.ts?token=abc

        """.trimIndent()

        DownloadStreamRelay.rewritePlaylist(playlist, base) {
            "relay:$it"
        } shouldBe """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="relay:https://cdn.example.org/show/ep1/key.bin",IV=0x1
            #EXT-X-MAP:URI="relay:https://cdn.example.org/init.mp4"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",URI="relay:https://audio.example.org/ja.m3u8"
            #EXT-X-SESSION-KEY:METHOD=AES-128,URI="data:text/plain;base64,AAAA"
            #EXTINF:6.0,
            relay:https://cdn.example.org/show/ep1/seg000.ts?token=abc

        """.trimIndent()
    }

    private class FakeHost(private val segment: ByteArray) : NanoHTTPD("127.0.0.1", 0) {
        data class Received(val range: String?, val referer: String?)

        val requests: MutableList<Received> = Collections.synchronizedList(mutableListOf())

        @Volatile var cutFirstResponse = false

        @Volatile var ignoreRanges = false

        val url get() = "http://127.0.0.1:$listeningPort"

        override fun serve(session: IHTTPSession): Response {
            if (session.uri == "/hls/index.m3u8") {
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/vnd.apple.mpegurl",
                    "#EXTM3U\n#EXTINF:6.0,\n../seg005.ts\n#EXT-X-ENDLIST\n",
                )
            }
            if (session.uri !=
                "/seg005.ts"
            ) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
            }

            val range = session.headers["range"]
            requests += Received(range, session.headers["referer"])
            val start =
                range?.takeUnless { ignoreRanges }?.substringAfter("bytes=")?.substringBefore('-')?.toLong() ?: 0L
            val bytes = segment.copyOfRange(start.toInt(), segment.size)
            val cut = cutFirstResponse && requests.size == 1
            val body: InputStream = if (cut) CutStream(bytes, segment.size / 2) else bytes.inputStream()

            val response = if (start > 0) {
                newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, "video/mp2t", body, bytes.size.toLong())
                    .apply { addHeader("Content-Range", "bytes $start-${segment.size - 1}/${segment.size}") }
            } else {
                newFixedLengthResponse(Response.Status.OK, "video/mp2t", body, bytes.size.toLong())
            }
            if (!ignoreRanges) response.addHeader("Accept-Ranges", "bytes")
            return response
        }
    }

    /** Sends the first [cutAt] bytes, then fails like a dropped connection. */
    private class CutStream(bytes: ByteArray, private val cutAt: Int) : InputStream() {
        private val input = bytes.inputStream()
        private var sent = 0

        override fun read(): Int = throw UnsupportedOperationException()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (sent >= cutAt) throw IOException("connection dropped")
            val n = input.read(b, off, minOf(len, cutAt - sent))
            sent += n
            return n
        }
    }
}
