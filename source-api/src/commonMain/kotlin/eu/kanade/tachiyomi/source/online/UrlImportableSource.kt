package eu.kanade.tachiyomi.source.online

import android.net.Uri
import eu.kanade.tachiyomi.source.MangaSource
import java.net.URI
import java.net.URISyntaxException

/**
 * Interface for sources that can import manga directly from a URL
 * (e.g. pasted from clipboard or received via share intent).
 *
 * Identical to Komikku's UrlImportableSource interface.
 */
interface UrlImportableSource : MangaSource {

    /** List of hostnames this source can handle (lowercase, no scheme). */
    val matchingHosts: List<String>

    /** Returns true if the given URI matches one of [matchingHosts]. */
    fun matchesUri(uri: Uri): Boolean {
        return uri.host.orEmpty().lowercase() in matchingHosts
    }

    /** Maps a chapter URI to a chapter URL string, or null if it doesn't apply. */
    fun mapUrlToChapterUrl(uri: Uri): String? = null

    /** Resolves a chapter URL to its parent manga URL, or null if it doesn't apply. */
    suspend fun mapChapterUrlToMangaUrl(uri: Uri): String? = null

    /**
     * Maps a URI to a manga URL string.
     * May perform network I/O (e.g. gtoken API call for page URLs).
     * Returns null if the URI is not a supported manga URL.
     */
    suspend fun mapUrlToMangaUrl(uri: Uri): String?

    /** Strips scheme + host from a manga URL, returning only the path (and query/fragment). */
    fun cleanMangaUrl(url: String): String {
        return try {
            val uri = URI(url)
            var out = uri.path
            if (uri.query != null) out += "?" + uri.query
            if (uri.fragment != null) out += "#" + uri.fragment
            out
        } catch (_: URISyntaxException) {
            url
        }
    }

    /** Strips scheme + host from a chapter URL, returning only the path (and query/fragment). */
    fun cleanChapterUrl(url: String): String {
        return try {
            val uri = URI(url)
            var out = uri.path
            if (uri.query != null) out += "?" + uri.query
            if (uri.fragment != null) out += "#" + uri.fragment
            out
        } catch (_: URISyntaxException) {
            url
        }
    }
}
