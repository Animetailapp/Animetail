package eu.kanade.tachiyomi.ui.player.cache

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import fi.iki.elonen.NanoHTTPD
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Local HTTP proxy between mpv and the video hosts that keeps the played bytes in a
 * [VideoCacheStore], so reopening an episode or seeking back to a watched part doesn't wait for
 * the network.
 *
 * mpv's request headers are forwarded unchanged, so hosts receive the same requests as when mpv
 * connects directly. HLS playlists are rewritten so their segments go through the proxy too.
 * Anything the proxy can't handle (no range support, unknown length) is passed through as is.
 */
class VideoCacheProxy(
    private val client: OkHttpClient,
    val store: VideoCacheStore,
    private val prefetchEnabled: () -> Boolean = { true },
) {
    // Only URLs carrying this token are served, other apps can't use the proxy as a relay.
    private val token = UUID.randomUUID().toString().replace("-", "")
    private var server: Server? = null
    private val prefetcher = SegmentPrefetcher(
        download = ::downloadToCache,
        isCached = { store.resource(it.toString()).isComplete() },
    )

    /**
     * Returns the URL mpv should open to play [url] through the proxy, or null when the proxy
     * couldn't be started and the video has to be played directly.
     *
     * @param cacheKey identifies the video when its URL changes between plays, see [cacheKey].
     */
    @Synchronized
    fun proxyUrl(url: String, cacheKey: String? = null): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val port = startedPort() ?: return null
        return proxyUrl(port, httpUrl, cacheKey)
    }

    private fun startedPort(): Int? {
        server?.let { return it.listeningPort }
        return try {
            Server().also {
                it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                server = it
            }.listeningPort
        } catch (e: IOException) {
            logcat(LogPriority.ERROR, e) { "Couldn't start the video cache proxy" }
            null
        }
    }

    // The original file name is kept at the end so mpv can still tell HLS playlists by extension.
    private fun proxyUrl(port: Int, url: HttpUrl, cacheKey: String? = null): String {
        val fileName = url.pathSegments.lastOrNull()
            ?.replace(UNSAFE_FILE_NAME_CHARS, "_")
            ?.takeIf { it.isNotBlank() }
            ?: "video"
        val encodedKey = cacheKey?.encodeUtf8()?.base64Url() ?: NO_CACHE_KEY
        return "http://$LOCALHOST:$port/$token/${url.toString().encodeUtf8().base64Url()}/$encodedKey/$fileName"
    }

    private inner class Server : NanoHTTPD(LOCALHOST, 0) {

        override fun useGzipWhenAccepted(response: Response): Boolean = false

        override fun serve(session: IHTTPSession): Response {
            val segments = session.uri.trim('/').split('/')
            if (segments.size < 3 || segments[0] != token) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "")
            }
            val url = segments[1].decodeBase64()?.utf8()?.toHttpUrlOrNull()
                ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "")
            val cacheKey = segments[2].takeIf { it != NO_CACHE_KEY }?.decodeBase64()?.utf8()

            return try {
                serveResource(url, session.headers, cacheKey)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Video cache proxy failed for $url" }
                newFixedLengthResponse(BadGateway, MIME_PLAINTEXT, "")
            }
        }

        private fun serveResource(url: HttpUrl, requestHeaders: Map<String, String>, cacheKey: String?): Response {
            val forwardedHeaders = forwardedHeaders(requestHeaders)
            val rangeHeader = requestHeaders["range"]
            val range = rangeHeader?.let { ByteRange.parse(it) ?: return passThrough(url, forwardedHeaders, it) }
            val start = range?.start ?: 0L
            prefetcher.awaitPrefetch(url)
            val onEndReached = { if (prefetchEnabled()) prefetcher.onSegmentPlayed(url, forwardedHeaders) }
            val resource = store.resource(cacheKey ?: url.toString())
            resource.touch()

            val cachedInfo = resource.readInfo()?.let { checkedInfo(resource, it, url, forwardedHeaders) }
            val firstBlock = start / store.blockSize
            val firstBlockCached = cachedInfo != null &&
                (resource.hasBlock(firstBlock, cachedInfo.length) || resource.hasPartialBlock(firstBlock))
            if (cachedInfo != null && start < cachedInfo.length && firstBlockCached) {
                val stream = CachedRangeStream(
                    resource = resource,
                    length = cachedInfo.length,
                    start = start,
                    endInclusive = range.endFor(cachedInfo.length),
                    openUpstream = { offset ->
                        openUpstream(url, forwardedHeaders, offset, cachedInfo.length, resource)
                    },
                    onEndReached = onEndReached,
                )
                return rangeResponse(stream, cachedInfo, start, range)
            }

            val blockStart = firstBlock * store.blockSize
            val upstream = try {
                client.newCall(upstreamRequest(url, forwardedHeaders, blockStart)).execute()
            } catch (e: IOException) {
                // Lets a cached video start without a connection; its segments are on disk already.
                val playlist = resource.readPlaylist() ?: throw e
                return playlistResponse(playlist)
            }
            return try {
                serveUpstream(url, upstream, resource, blockStart, start, range, forwardedHeaders, onEndReached)
            } catch (e: Exception) {
                upstream.close()
                throw e
            }
        }

        private fun serveUpstream(
            url: HttpUrl,
            upstream: okhttp3.Response,
            resource: VideoCacheStore.Resource,
            blockStart: Long,
            start: Long,
            range: ByteRange?,
            forwardedHeaders: Headers,
            onEndReached: () -> Unit,
        ): Response {
            if (!upstream.isSuccessful) {
                upstream.close()
                // Signed playlist links expire, while the segments they list may be cached.
                resource.readPlaylist()?.let { return playlistResponse(it) }
                return newFixedLengthResponse(status(upstream.code), MIME_PLAINTEXT, "")
            }

            val contentType = upstream.header("Content-Type")
            val finalUrl = upstream.request.url
            if (blockStart == 0L && isPlaylist(upstream, contentType, finalUrl)) {
                val playlist = CachedPlaylist(finalUrl.toString(), contentType, upstream.body.string())
                if (HlsPlaylist.isStatic(playlist.body)) resource.writePlaylist(playlist)
                return playlistResponse(playlist)
            }

            val length = when {
                upstream.code == PARTIAL_CONTENT -> ByteRange.totalLength(upstream.header("Content-Range"))
                blockStart == 0L -> upstream.body.contentLength().takeIf { it >= 0 }
                else -> null
            }
            if (length == null) {
                // Without a known length or range support the bytes can't be split into blocks.
                upstream.close()
                return passThrough(url, forwardedHeaders, range?.header)
            }
            if (start >= length) {
                upstream.close()
                return newFixedLengthResponse(RangeNotSatisfiable, MIME_PLAINTEXT, "")
            }

            val info = CachedResourceInfo(length, contentType, url.toString())
            resource.writeInfo(info)
            val stream = CachedRangeStream(
                resource = resource,
                length = length,
                start = start,
                endInclusive = range.endFor(length),
                openUpstream = { offset -> openUpstream(url, forwardedHeaders, offset, length, resource) },
                upstream = UpstreamBody(upstream.body.byteStream(), blockStart),
                onEndReached = onEndReached,
            )
            return rangeResponse(stream, info, start, range)
        }

        private fun playlistResponse(playlist: CachedPlaylist): Response {
            val baseUrl = playlist.url.toHttpUrl()
            prefetcher.registerPlaylist(HlsPlaylist.mediaSegments(playlist.body, baseUrl))
            val rewritten = HlsPlaylist.rewrite(playlist.body, baseUrl) { proxyUrl(listeningPort, it) }
            return newFixedLengthResponse(Response.Status.OK, playlist.contentType ?: HLS_MIME_TYPE, rewritten)
        }

        private fun rangeResponse(
            stream: CachedRangeStream,
            info: CachedResourceInfo,
            start: Long,
            range: ByteRange?,
        ): Response {
            val end = range.endFor(info.length)
            val status = if (range != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK
            return newFixedLengthResponse(status, info.contentType ?: DEFAULT_MIME_TYPE, stream, end - start + 1)
                .apply {
                    addHeader("Accept-Ranges", "bytes")
                    if (range != null) addHeader("Content-Range", "bytes $start-$end/${info.length}")
                }
        }

        // Forwards the request exactly as mpv sent it and returns the host's answer unchanged.
        private fun passThrough(url: HttpUrl, forwardedHeaders: Headers, rangeHeader: String?): Response {
            val request = Request.Builder().url(url).headers(forwardedHeaders)
                .apply { rangeHeader?.let { header("Range", it) } }
                .build()
            val upstream = client.newCall(request).execute()
            val body = upstream.body
            val contentType = upstream.header("Content-Type") ?: DEFAULT_MIME_TYPE
            val response = if (body.contentLength() >= 0) {
                newFixedLengthResponse(status(upstream.code), contentType, body.byteStream(), body.contentLength())
            } else {
                newChunkedResponse(status(upstream.code), contentType, body.byteStream())
            }
            upstream.header("Content-Range")?.let { response.addHeader("Content-Range", it) }
            upstream.header("Accept-Ranges")?.let { response.addHeader("Accept-Ranges", it) }
            return response
        }
    }

    /**
     * Downloads the start of [url] into the cache before it's played: the first segments of an
     * HLS stream, in its highest quality as mpv picks by default, or the first blocks of a file.
     */
    fun warmUp(url: String, headers: Headers, cacheKey: String? = null) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val requestHeaders = forwardedHeaders(headers.associate { (name, value) -> name to value })
        warmUp(httpUrl, requestHeaders, store.resource(cacheKey ?: url), isVariant = false)
    }

    private fun warmUp(url: HttpUrl, headers: Headers, resource: VideoCacheStore.Resource, isVariant: Boolean) {
        if (resource.hasStart(WARM_UP_BYTES)) return
        val response = client.newCall(upstreamRequest(url, headers, 0)).execute()
        val contentType = response.header("Content-Type")
        val finalUrl = response.request.url
        if (!response.isSuccessful || !isPlaylist(response, contentType, finalUrl)) {
            response.close()
            if (response.isSuccessful) downloadToCache(url, headers, resource, maxBytes = WARM_UP_BYTES)
            return
        }

        val playlist = CachedPlaylist(finalUrl.toString(), contentType, response.body.string())
        if (HlsPlaylist.isStatic(playlist.body)) resource.writePlaylist(playlist)
        val variant = HlsPlaylist.highestVariant(playlist.body, finalUrl)
        when {
            variant != null -> if (!isVariant) {
                warmUp(
                    variant,
                    headers,
                    store.resource(variant.toString()),
                    isVariant = true,
                )
            }

            else -> HlsPlaylist.mediaSegments(playlist.body, finalUrl)
                .take(WARM_UP_SEGMENTS)
                .filterNot { store.resource(it.toString()).isComplete() }
                .forEach { downloadToCache(it, headers, store.resource(it.toString())) }
        }
    }

    private fun isPlaylist(upstream: okhttp3.Response, contentType: String?, url: HttpUrl): Boolean =
        HlsPlaylist.isPlaylist(contentType, url) ||
            HlsPlaylist.startsLikePlaylist(upstream.peekBody(PLAYLIST_PEEK_BYTES).string())

    private fun downloadToCache(
        url: HttpUrl,
        headers: Headers,
        resource: VideoCacheStore.Resource = store.resource(url.toString()),
        maxBytes: Long = Long.MAX_VALUE,
    ) {
        val response = client.newCall(upstreamRequest(url, headers, 0)).execute()
        val length = when (response.code) {
            PARTIAL_CONTENT -> ByteRange.totalLength(response.header("Content-Range"))
            OK -> response.body.contentLength().takeIf { it >= 0 }
            else -> null
        }
        if (length == null || length == 0L) {
            response.close()
            return
        }
        resource.writeInfo(CachedResourceInfo(length, response.header("Content-Type"), url.toString()))
        val upstream = UpstreamBody(response.body.byteStream(), 0)
        val end = minOf(length, maxBytes) - 1
        CachedRangeStream(resource, length, 0, end, { openUpstream(url, headers, it, length, resource) }, upstream)
            .use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (stream.read(buffer) != -1) Unit
            }
    }

    private fun openUpstream(
        url: HttpUrl,
        headers: Headers,
        offset: Long,
        expectedLength: Long,
        resource: VideoCacheStore.Resource,
    ): UpstreamBody {
        val response = client.newCall(upstreamRequest(url, headers, offset)).execute()
        val length = when (response.code) {
            PARTIAL_CONTENT -> ByteRange.totalLength(response.header("Content-Range"))
            OK -> response.body.contentLength().takeIf { offset == 0L }
            else -> null
        }
        if (length != expectedLength) {
            response.close()
            // The cached blocks belong to another version of the file.
            if (length != null) resource.delete()
            throw IOException("Unexpected answer at $offset: ${response.code}, length $length")
        }
        return UpstreamBody(response.body.byteStream(), offset)
    }

    /**
     * A video cached under a [cacheKey] may come back with another URL, such as a new token or the
     * port of an extension's local server. Its size is checked with a one-byte request before the
     * stored bytes are used; when it differs they belong to another file and are dropped.
     */
    private fun checkedInfo(
        resource: VideoCacheStore.Resource,
        info: CachedResourceInfo,
        url: HttpUrl,
        headers: Headers,
    ): CachedResourceInfo? {
        if (info.url == url.toString()) return info
        val length = try {
            client.newCall(upstreamRequest(url, headers, 0, 0)).execute().use { response ->
                if (response.code == PARTIAL_CONTENT) ByteRange.totalLength(response.header("Content-Range")) else null
            }
        } catch (_: IOException) {
            // Without a connection the stored bytes are all there is; a wrong size fails later.
            return info
        }
        if (length != info.length) {
            resource.delete()
            return null
        }
        return info.copy(url = url.toString()).also(resource::writeInfo)
    }

    private fun upstreamRequest(url: HttpUrl, headers: Headers, offset: Long, end: Long? = null): Request =
        Request.Builder().url(url).headers(headers)
            .header("Range", "bytes=$offset-${end ?: ""}")
            .build()

    private fun forwardedHeaders(requestHeaders: Map<String, String>): Headers {
        val builder = Headers.Builder()
        requestHeaders.forEach { (name, value) ->
            if (name.lowercase() !in NOT_FORWARDED_HEADERS) builder.add(name, value)
        }
        // Compressed bodies don't map to byte ranges.
        builder["Accept-Encoding"] = "identity"
        return builder.build()
    }

    private fun status(code: Int): NanoHTTPD.Response.IStatus =
        NanoHTTPD.Response.Status.lookup(code) ?: object : NanoHTTPD.Response.IStatus {
            override fun getDescription() = "$code"
            override fun getRequestStatus() = code
        }

    private object BadGateway : NanoHTTPD.Response.IStatus {
        override fun getDescription() = "502 Bad Gateway"
        override fun getRequestStatus() = 502
    }

    private object RangeNotSatisfiable : NanoHTTPD.Response.IStatus {
        override fun getDescription() = "416 Range Not Satisfiable"
        override fun getRequestStatus() = 416
    }

    companion object {
        private const val LOCALHOST = "127.0.0.1"
        private const val OK = 200
        private const val PARTIAL_CONTENT = 206
        private const val PLAYLIST_PEEK_BYTES = 16L
        private const val NO_CACHE_KEY = "-"
        private const val WARM_UP_SEGMENTS = 2
        private const val WARM_UP_BYTES = 4L * 1024 * 1024
        private const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"
        private const val DEFAULT_MIME_TYPE = "application/octet-stream"
        private val UNSAFE_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")

        // NanoHTTPD adds remote-addr and http-client-ip; the rest are about the local connection.
        private val NOT_FORWARDED_HEADERS = setOf(
            "host", "range", "connection", "keep-alive", "accept-encoding", "te", "upgrade",
            "transfer-encoding", "proxy-connection", "remote-addr", "http-client-ip",
        )

        /**
         * Identifies a video by what it is instead of its URL, which can change between plays:
         * signed links get new tokens and Mega streams get a new local port. Different files under
         * the same key are told apart by their size, see [checkedInfo].
         */
        fun cacheKey(sourceId: Long, episodeUrl: String, hosterName: String, videoTitle: String): String =
            "$sourceId|$episodeUrl|$hosterName|$videoTitle"

        /** Proxy used by the player; null when the video cache is turned off. */
        val shared: VideoCacheProxy? get() {
            val maxBytes = Injekt.get<PlayerPreferences>().videoCacheMaxMegabytes().get() * BYTES_PER_MEGABYTE
            return if (maxBytes > 0) instance else null
        }

        private val instance by lazy {
            val preferences = Injekt.get<PlayerPreferences>()
            val network = Injekt.get<NetworkHelper>()
            val client = OkHttpClient.Builder()
                .dns(network.client.dns)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val store = VideoCacheStore(
                dir = File(Injekt.get<Application>().cacheDir, "video_cache"),
                maxBytes = { preferences.videoCacheMaxMegabytes().get() * BYTES_PER_MEGABYTE },
            )
            VideoCacheProxy(client, store, prefetchEnabled = { preferences.prefetchVideoSegments().get() })
        }

        /** Store of the shared proxy, also available while the cache is off so it can be cleared. */
        val sharedStore: VideoCacheStore get() = instance.store

        private const val BYTES_PER_MEGABYTE = 1024L * 1024
    }
}
