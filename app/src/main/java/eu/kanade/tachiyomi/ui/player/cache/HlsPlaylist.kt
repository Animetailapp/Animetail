package eu.kanade.tachiyomi.ui.player.cache

import okhttp3.HttpUrl

/** Rewrites the URIs of HLS playlists so their segments, keys and variants go through the proxy. */
object HlsPlaylist {

    private const val HEADER = "#EXTM3U"
    private val URI_ATTRIBUTE = Regex("""URI="([^"]+)"""")
    private val BANDWIDTH = Regex("""[:,]BANDWIDTH=(\d+)""")

    fun isPlaylist(contentType: String?, url: HttpUrl): Boolean {
        val type = contentType?.lowercase().orEmpty()
        val path = url.encodedPath.lowercase()
        return "mpegurl" in type || path.endsWith(".m3u8") || path.endsWith(".m3u")
    }

    /**
     * Whether [playlist] won't change: a finished media playlist or a master playlist. Live
     * playlists are always fetched again.
     */
    fun isStatic(playlist: String): Boolean =
        "#EXT-X-ENDLIST" in playlist || "#EXT-X-STREAM-INF" in playlist

    /** Variant with the highest bandwidth of a master playlist, null for media playlists. */
    fun highestVariant(playlist: String, baseUrl: HttpUrl): HttpUrl? {
        var bestBandwidth = -1L
        var best: HttpUrl? = null
        var pendingBandwidth: Long? = null
        playlist.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("#EXT-X-STREAM-INF") ->
                    pendingBandwidth = BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0

                line.isEmpty() || line.startsWith("#") -> Unit

                else -> {
                    val bandwidth = pendingBandwidth ?: return@forEach
                    pendingBandwidth = null
                    if (bandwidth > bestBandwidth) {
                        baseUrl.resolve(line)?.let {
                            best = it
                            bestBandwidth = bandwidth
                        }
                    }
                }
            }
        }
        return best
    }

    /** Segment URLs of a media playlist in playback order; empty for master playlists. */
    fun mediaSegments(playlist: String, baseUrl: HttpUrl): List<HttpUrl> {
        if ("#EXT-X-STREAM-INF" in playlist) return emptyList()
        return playlist.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { baseUrl.resolve(it) }
            .toList()
    }

    // Some hosts serve playlists as text/plain or under misleading extensions.
    fun startsLikePlaylist(start: String): Boolean = start.trimStart('\uFEFF').startsWith(HEADER)

    /**
     * Resolves every URI of [playlist] against [baseUrl] and maps it with [proxyUrl]. URIs with
     * schemes other than http(s), such as `data:` or DRM key schemes, are left untouched.
     */
    fun rewrite(playlist: String, baseUrl: HttpUrl, proxyUrl: (HttpUrl) -> String): String {
        fun map(uri: String): String = baseUrl.resolve(uri)?.let(proxyUrl) ?: uri

        return playlist.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> line
                trimmed.startsWith("#") -> URI_ATTRIBUTE.replace(line) { "URI=\"${map(it.groupValues[1])}\"" }
                else -> map(trimmed)
            }
        }
    }
}
