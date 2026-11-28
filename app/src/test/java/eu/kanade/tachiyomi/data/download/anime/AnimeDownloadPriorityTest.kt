package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadPriority.Item
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadPriority.WatchActivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class AnimeDownloadPriorityTest {

    /** A queue entry: "<show><episode>", e.g. "W3" is episode 3 of show W; a `*` marks it downloading. */
    data class Entry(val name: String) {
        val downloading = name.endsWith("*")
        val item = Item(animeId = name.first().code.toLong(), episodeId = name.trimEnd('*').drop(1).toLong())
        override fun toString() = name
    }

    data class Case(
        val description: String,
        val queue: List<String>,
        val added: List<String>,
        val expected: List<String>,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `episodes of the shows being watched go ahead of the backlog`(case: Case) {
        val result = priority.insert(
            queue = case.queue.map(::Entry),
            added = case.added.map(::Entry),
            item = Entry::item,
            isDownloading = Entry::downloading,
        )
        assertEquals(case.expected, result.map { it.name })
    }

    companion object {
        private val now = 1_800_000_000_000L

        private fun watched(ago: kotlin.time.Duration, upNext: Long?) =
            WatchActivity(lastWatchedAt = now - ago.inWholeMilliseconds, upNextEpisodeId = upNext)

        // W is watched now and its next episode is 3; V was watched yesterday, next episode 8;
        // O was last watched a month ago, so it is backlog; B was never watched.
        private val priority = AnimeDownloadPriority(
            activity = mapOf(
                'W'.code.toLong() to watched(1.hours, upNext = 3),
                'V'.code.toLong() to watched(1.days, upNext = 8),
                'O'.code.toLong() to watched(30.days, upNext = 5),
            ),
            now = now,
        )

        @JvmStatic
        fun cases() = listOf(
            Case(
                "the next episode of the show being watched jumps the backlog",
                queue = listOf("B1", "B2", "B3"),
                added = listOf("W3"),
                expected = listOf("W3", "B1", "B2", "B3"),
            ),
            Case(
                "the downloading item, and what is ahead of it, are not interrupted",
                queue = listOf("B1", "B2*", "B3"),
                added = listOf("W3"),
                expected = listOf("B1", "B2*", "W3", "B3"),
            ),
            Case(
                "up next beats other episodes of watched shows, which beat the backlog",
                queue = listOf("B1"),
                added = listOf("B2", "W5", "W3"),
                expected = listOf("W3", "W5", "B1", "B2"),
            ),
            Case(
                "within a tier, the show watched more recently goes first",
                queue = emptyList(),
                added = listOf("V9", "W4"),
                expected = listOf("W4", "V9"),
            ),
            Case(
                "a show not watched for weeks is backlog, even its next episode",
                queue = listOf("B1"),
                added = listOf("O5"),
                expected = listOf("B1", "O5"),
            ),
            Case(
                "existing items keep their order, new ones go after items of the same tier",
                queue = listOf("W4", "B1", "V9"),
                added = listOf("W6", "B2"),
                expected = listOf("W4", "W6", "B1", "V9", "B2"),
            ),
            Case(
                "new backlog items keep their order at the end",
                queue = listOf("W4"),
                added = listOf("B3", "B1", "B2"),
                expected = listOf("W4", "B3", "B1", "B2"),
            ),
        )
    }
}
