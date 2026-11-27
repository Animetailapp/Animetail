package eu.kanade.tachiyomi.data.download.anime

import fi.iki.elonen.NanoHTTPD
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Serves a download's network inputs to FFmpeg from 127.0.0.1, fetching them with OkHttp.
 *
 * FFmpeg's HLS demuxer doesn't reconnect segment requests (it doesn't pass `reconnect` on to them),
 * so a connection that drops or stalls in the middle of a segment loses the rest of that segment and
 * the downloaded episode skips. The relay resumes a cut response with a Range request from where it
 * stopped, so FFmpeg only ever reads complete responses. HLS playlists are rewritten so that their
 * segments, keys, init sections and renditions are fetched through the relay too.
 *
 * When a response can't be completed or the host refuses it, [failure] is set and FFmpeg sees an
 * error. The download has to fail then, since FFmpeg would otherwise skip the segment and still
 * write the file.
 */
class DownloadStreamRelay(
    client: OkHttpClient,
    private val headers: Headers,
) : NanoHTTPD(LOOPBACK, 0) {

    private val client = client.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .cache(null)
        .build()

    private val nextId = AtomicInteger()
    private val urlById = ConcurrentHashMap<String, HttpUrl>()
    private val idByUrl = ConcurrentHashMap<HttpUrl, String>()

    private val _failure = AtomicReference<StreamRelayException?>()

    /** The first response the relay gave up on, if any. */
    val failure: StreamRelayException? get() = _failure.get()

    fun open(): DownloadStreamRelay = apply { start(SOCKET_READ_TIMEOUT, true) }

    /** Returns the relay URL FFmpeg should read [url] from, or [url] itself if it isn't fetched over the network. */
    fun relayUrl(url: String): String {
        return url.toHttpUrlOrNull()?.let(::relayUrl) ?: url
    }

    private fun relayUrl(url: HttpUrl): String {
        val id = idByUrl.getOrPut(url) {
            nextId.incrementAndGet().toString().also { urlById[it] = url }
        }
        // Keep the last path segment: FFmpeg picks demuxers and checks segment extensions by name.
        val name = url.pathSegments.lastOrNull { it.isNotEmpty() } ?: "stream"
        return HttpUrl.Builder()
            .scheme("http")
            .host(LOOPBACK)
            .port(listeningPort)
            .addPathSegment(id)
            .addPathSegment(name)
            .build()
            .toString()
    }

    override fun serve(session: IHTTPSession): Response {
        val id = session.uri.trimStart('/').substringBefore('/')
        val url = urlById[id] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
        return try {
            relay(url, session.headers["range"])
        } catch (e: IOException) {
            fail(url, e)
            newFixedLengthResponse(status(502, "Bad Gateway"), MIME_PLAINTEXT, "")
        }
    }

    private fun relay(url: HttpUrl, range: String?): Response {
        val upstream = fetch(url, range)
        val code = upstream.code
        if (code != 200 && code != 206) {
            upstream.close()
            // FFmpeg skips an HLS segment it can't fetch and still writes the file. 416 is a range
            // past the end, which FFmpeg asks for when it reads to the end of a file.
            if (code != 416) fail(url, IOException("HTTP $code"))
            return newFixedLengthResponse(status(code, upstream.message), MIME_PLAINTEXT, "")
        }

        if (isPlaylist(upstream)) {
            val text = upstream.use { it.body.string() }
            val rewritten = rewritePlaylist(text, upstream.request.url) { relayUrl(it) }
            return newFixedLengthResponse(Response.Status.OK, HLS_MIME, rewritten)
        }

        val length = upstream.body.contentLength()
        val body = ResumingInputStream(url, upstream, range)
        val response = if (length >= 0) {
            newFixedLengthResponse(status(code, upstream.message), upstream.mime(), body, length)
        } else {
            newChunkedResponse(status(code, upstream.message), upstream.mime(), body)
        }
        upstream.header("Content-Range")?.let { response.addHeader("Content-Range", it) }
        upstream.header("Accept-Ranges")?.let { response.addHeader("Accept-Ranges", it) }
        return response
    }

    /** Requests [url], retrying network errors and server errors. */
    private fun fetch(url: HttpUrl, range: String?): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .apply { range?.let { header("Range", it) } }
            .build()
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) Thread.sleep(retryDelayMillis(attempt))
            try {
                val response = client.newCall(request).execute()
                if (response.code < 500 || attempt == MAX_ATTEMPTS - 1) return response
                response.close()
                lastError = IOException("HTTP ${response.code}")
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError!!
    }

    private fun fail(url: HttpUrl, e: IOException) {
        if (_failure.compareAndSet(null, StreamRelayException("Couldn't fetch ${url.redacted()}: ${e.message}", e))) {
            logcat(LogPriority.WARN, e) { "Download relay gave up on ${url.redacted()}" }
        }
    }

    /**
     * The body of a media response. When the connection fails before the body is complete, the rest
     * is requested with a Range request from the first missing byte, so the reader never sees the gap.
     */
    private inner class ResumingInputStream(
        private val url: HttpUrl,
        first: okhttp3.Response,
        requestedRange: String?,
    ) : InputStream() {

        private var current: okhttp3.Response = first
        private var input: InputStream = first.body.byteStream()

        // Absolute offsets in the resource of the next byte to read and of the end (exclusive),
        // when known.
        private var position: Long = startOffset(first, requestedRange)
        private val end: Long? = endOffset(first, position)

        private var resumes = 0

        override fun read(): Int {
            val buffer = ByteArray(1)
            return if (read(buffer, 0, 1) == -1) -1 else buffer[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (true) {
                val error = try {
                    val n = input.read(b, off, len)
                    if (n > 0) {
                        position += n
                        return n
                    }
                    if (end == null || position >= end) return -1
                    IOException("Stream ended at $position of $end")
                } catch (e: IOException) {
                    e
                }
                resume(error)
            }
        }

        private fun resume(cause: IOException) {
            current.close()
            if (end == null || resumes >= MAX_RESUMES) {
                fail(url, cause)
                throw cause
            }
            resumes++
            logcat(LogPriority.INFO) {
                "Download relay resuming ${url.redacted()} at $position of $end (${cause.message})"
            }
            Thread.sleep(retryDelayMillis(resumes))
            val response = try {
                fetch(url, "bytes=$position-${end - 1}")
            } catch (e: IOException) {
                resume(e)
                return
            }
            val start = response.header("Content-Range")?.let(::contentRangeStart)
            if (response.code != 206 || start != position) {
                response.close()
                val error = IOException("Host didn't resume at $position (HTTP ${response.code}, starts at $start)")
                fail(url, error)
                throw error
            }
            current = response
            input = response.body.byteStream()
        }

        override fun close() {
            current.close()
        }
    }

    companion object {
        private const val LOOPBACK = "127.0.0.1"
        private const val HLS_MIME = "application/vnd.apple.mpegurl"

        private const val CONNECT_TIMEOUT_SECONDS = 15L

        // A host that sends nothing for this long is treated as a dropped connection and resumed.
        private const val READ_TIMEOUT_SECONDS = 20L

        // FFmpeg may pause reading while it writes; the relay waits as long as the download's
        // stall timeout instead of closing the connection on it.
        private const val SOCKET_READ_TIMEOUT = 3 * 60 * 1000

        private const val MAX_ATTEMPTS = 4
        private const val MAX_RESUMES = 8

        private fun retryDelayMillis(attempt: Int) = 1000L shl (attempt - 1).coerceAtMost(4)

        private val URI_ATTRIBUTE = Regex("""URI="([^"]*)"""")

        private fun isPlaylist(response: okhttp3.Response): Boolean {
            val head = response.peekBody(PLAYLIST_PEEK_BYTES).string().removePrefix("﻿").trimStart()
            return head.startsWith("#EXTM3U")
        }

        private const val PLAYLIST_PEEK_BYTES = 64L

        /**
         * Rewrites every URI in the HLS playlist [text] (segments, variant playlists, and the `URI`
         * attributes of tags such as `#EXT-X-KEY`, `#EXT-X-MAP` and `#EXT-X-MEDIA`) to [relay] of the
         * URI resolved against [base]. URIs that aren't http(s), such as `data:` keys, are kept.
         */
        internal fun rewritePlaylist(text: String, base: HttpUrl, relay: (HttpUrl) -> String): String {
            fun rewrite(uri: String): String = base.resolve(uri)?.let(relay) ?: uri
            return text.lineSequence().joinToString("\n") { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> line
                    trimmed.startsWith("#") -> URI_ATTRIBUTE.replace(line) { "URI=\"${rewrite(it.groupValues[1])}\"" }
                    else -> rewrite(trimmed)
                }
            }
        }

        internal fun contentRangeStart(contentRange: String): Long? =
            contentRange.substringAfter("bytes ", "").substringBefore('-').trim().toLongOrNull()

        private fun startOffset(response: okhttp3.Response, requestedRange: String?): Long {
            if (response.code != 206) return 0
            return response.header("Content-Range")?.let(::contentRangeStart)
                ?: requestedRange?.substringAfter("bytes=")?.substringBefore('-')?.toLongOrNull()
                ?: 0
        }

        private fun endOffset(response: okhttp3.Response, start: Long): Long? {
            val length = response.body.contentLength()
            // Without a length, a cut can't be told apart from the end, so it can't be resumed.
            // A host that doesn't support ranges fails the resume, which is checked.
            return if (length < 0) null else start + length
        }

        private fun okhttp3.Response.mime() = header("Content-Type") ?: "application/octet-stream"

        private fun status(code: Int, message: String) = object : Response.IStatus {
            override fun getDescription() = "$code $message"
            override fun getRequestStatus() = code
        }

        private fun HttpUrl.redacted() = newBuilder().query(null).build().toString()
    }
}

/** The relay couldn't fetch one of a video's responses from its host. */
class StreamRelayException(message: String, cause: Throwable) : IOException(message, cause)
