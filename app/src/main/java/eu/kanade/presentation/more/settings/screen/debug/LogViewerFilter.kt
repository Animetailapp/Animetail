package eu.kanade.presentation.more.settings.screen.debug

import eu.kanade.tachiyomi.util.system.AppLogEntry

enum class LogViewerFilter {
    ALL,
    ERRORS,
    DOWNLOADS,
    CAST,
    PLAYER,
    NETWORK,
}

/**
 * Whether [entry] belongs to [filter] and contains [query] (case-insensitive; blank matches all).
 *
 * Area filters match on the tags those areas log with: the logcat tag is the logging class's
 * simple name, or an explicit one such as mpv/<module>. Network also matches network exceptions
 * from any tag.
 */
fun matchesLogFilter(entry: AppLogEntry, filter: LogViewerFilter, query: String): Boolean {
    if (query.isNotBlank() && !entry.text.contains(query.trim(), ignoreCase = true)) return false
    val tag = entry.tag.orEmpty()
    return when (filter) {
        LogViewerFilter.ALL -> true

        LogViewerFilter.ERRORS -> entry.priority in ERROR_PRIORITIES

        LogViewerFilter.DOWNLOADS -> tag.contains("Download", ignoreCase = true)

        LogViewerFilter.CAST -> tag.contains("Cast", ignoreCase = true) || CAST_FRAMES.any { entry.text.contains(it) }

        LogViewerFilter.PLAYER ->
            tag.startsWith("Player") ||
                tag.startsWith("mpv/") ||
                tag in PLAYER_TAGS

        LogViewerFilter.NETWORK ->
            tag == "NetworkHelper" ||
                tag.endsWith("Interceptor") ||
                NETWORK_ERRORS.any { entry.text.contains(it) }
    }
}

// Lines logged inside a coroutine are tagged with the coroutine class (e.g. StandaloneCoroutine),
// so cast failures there are recognised by a cast frame in their stack trace.
private val CAST_FRAMES = listOf("tachiyomi.ui.player.cast.", "tachiyomi.ui.player.CastManager")

private val ERROR_PRIORITIES = setOf('W', 'E', 'A')

private val PLAYER_TAGS = setOf(
    "AniyomiMPVView",
    "EpisodeLoader",
    "ExternalIntents",
)

// Network failures logged by any caller (HttpException is the app's own).
private val NETWORK_ERRORS = listOf(
    "[FlareSolverr]",
    "java.net.",
    "javax.net.ssl.",
    "okhttp3.",
    "HttpException",
)
