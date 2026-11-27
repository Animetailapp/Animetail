package eu.kanade.presentation.more.settings.screen.debug

import eu.kanade.tachiyomi.util.system.AppLogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class LogViewerFilterTest {

    data class Case(
        val description: String,
        val entry: AppLogEntry,
        val query: String = "",
        val expected: Set<LogViewerFilter>,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `filter matching`(case: Case) {
        val matched = LogViewerFilter.entries.filter { matchesLogFilter(case.entry, it, case.query) }.toSet()
        assertEquals(case.expected, matched, case.description)
    }

    companion object {
        private fun entry(priority: Char?, tag: String?, message: String = "message") =
            AppLogEntry(id = 0, priority = priority, tag = tag, text = "$tag: $message")

        @JvmStatic
        fun cases() = listOf(
            Case(
                "debug line from the downloader is only a download entry",
                entry('D', "AnimeDownloader"),
                expected = setOf(LogViewerFilter.ALL, LogViewerFilter.DOWNLOADS),
            ),
            Case(
                "the manga download job counts as downloads, warnings count as errors",
                entry('W', "MangaDownloader"),
                expected = setOf(LogViewerFilter.ALL, LogViewerFilter.ERRORS, LogViewerFilter.DOWNLOADS),
            ),
            Case(
                "cast classes under the player package are cast, not player",
                entry('I', "CastManager"),
                expected = setOf(LogViewerFilter.ALL, LogViewerFilter.CAST),
            ),
            Case(
                "a coroutine-tagged failure whose trace runs through the cast code is cast",
                entry(
                    'E',
                    "StandaloneCoroutine",
                    "could not connect\n\tat eu.kanade.tachiyomi.ui.player.CastManager.loadMedia(x:1)",
                ),
                expected = setOf(LogViewerFilter.ALL, LogViewerFilter.ERRORS, LogViewerFilter.CAST),
            ),
            Case(
                "other coroutine lines are not cast",
                entry('I', "StandaloneCoroutine", "Library update finished"),
                expected = setOf(LogViewerFilter.ALL),
            ),
            Case(
                "mpv lines use an explicit mpv/<module> tag",
                entry('E', "mpv/ffmpeg"),
                expected = setOf(LogViewerFilter.ALL, LogViewerFilter.ERRORS, LogViewerFilter.PLAYER),
            ),
            Case(
                "player classes not starting with Player",
                entry('D', "EpisodeLoader"),
                expected = setOf(LogViewerFilter.ALL, LogViewerFilter.PLAYER),
            ),
            Case(
                "a network exception logged by any class is network",
                entry('E', "PlayerViewModel", "failed\njava.net.UnknownHostException: host"),
                expected = setOf(
                    LogViewerFilter.ALL,
                    LogViewerFilter.ERRORS,
                    LogViewerFilter.PLAYER,
                    LogViewerFilter.NETWORK,
                ),
            ),
            Case(
                "interceptors are network",
                entry('I', "CloudflareInterceptor"),
                expected = setOf(LogViewerFilter.ALL, LogViewerFilter.NETWORK),
            ),
            Case(
                "info is not an error",
                entry('I', "App"),
                expected = setOf(LogViewerFilter.ALL),
            ),
            Case(
                "a dropped-messages marker has no tag or priority",
                entry(null, null),
                expected = setOf(LogViewerFilter.ALL),
            ),
            Case(
                "search is case-insensitive and also matches stack trace lines",
                entry('E', "AnimeDownloader", "boom\n\tat Foo.bar(Foo.kt:1)"),
                query = " foo.BAR ",
                expected = setOf(LogViewerFilter.ALL, LogViewerFilter.ERRORS, LogViewerFilter.DOWNLOADS),
            ),
            Case(
                "a search miss hides the entry from every filter",
                entry('E', "AnimeDownloader"),
                query = "nothing like this",
                expected = emptySet(),
            ),
        )
    }
}
