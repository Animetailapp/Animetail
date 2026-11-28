package eu.kanade.tachiyomi.data.download.anime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Covers the concurrency selection that wires the "download slots" preference into the anime
 * downloader: the slot count is clamped to at least one, at most one download per source runs
 * (the per-host politeness cap), and queue order decides which items fill the slots.
 */
class SelectConcurrentDownloadsTest {

    data class FakeDownload(val id: Int, val source: String, val runnable: Boolean = true)

    data class Case(
        val description: String,
        val queue: List<FakeDownload>,
        val slots: Int,
        val expectedIds: List<Int>,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `selects at most one runnable download per source, capped at the slot count`(case: Case) {
        val selected = selectConcurrentDownloads(
            queue = case.queue,
            slots = case.slots,
            isRunnable = { it.runnable },
            source = { it.source },
        )
        assertEquals(case.expectedIds, selected.map { it.id })
    }

    companion object {
        @JvmStatic
        fun cases() = listOf(
            Case(
                "the default of 1 slot downloads one at a time: only the first source runs",
                queue = listOf(
                    FakeDownload(id = 1, source = "a"),
                    FakeDownload(id = 2, source = "b"),
                    FakeDownload(id = 3, source = "c"),
                ),
                slots = 1,
                expectedIds = listOf(1),
            ),
            Case(
                "0 slots is clamped to 1 so a bad preference never stalls the queue",
                queue = listOf(
                    FakeDownload(id = 1, source = "a"),
                    FakeDownload(id = 2, source = "b"),
                ),
                slots = 0,
                expectedIds = listOf(1),
            ),
            Case(
                "negative slots is clamped to 1 too",
                queue = listOf(FakeDownload(id = 1, source = "a"), FakeDownload(id = 2, source = "b")),
                slots = -5,
                expectedIds = listOf(1),
            ),
            Case(
                "3 slots across 3 sources: one download from each runs in parallel",
                queue = listOf(
                    FakeDownload(id = 1, source = "a"),
                    FakeDownload(id = 2, source = "b"),
                    FakeDownload(id = 3, source = "c"),
                ),
                slots = 3,
                expectedIds = listOf(1, 2, 3),
            ),
            Case(
                "many episodes of one source stay one-at-a-time even with 3 slots (per-host politeness)",
                queue = listOf(
                    FakeDownload(id = 1, source = "a"),
                    FakeDownload(id = 2, source = "a"),
                    FakeDownload(id = 3, source = "a"),
                ),
                slots = 3,
                expectedIds = listOf(1),
            ),
            Case(
                "the first queued download of each source runs, in queue order, not later ones",
                queue = listOf(
                    FakeDownload(id = 1, source = "a"),
                    FakeDownload(id = 2, source = "b"),
                    FakeDownload(id = 3, source = "a"),
                    FakeDownload(id = 4, source = "b"),
                ),
                slots = 5,
                expectedIds = listOf(1, 2),
            ),
            Case(
                "slots cap the number of sources: 2 slots over 3 sources fills the first two",
                queue = listOf(
                    FakeDownload(id = 1, source = "a"),
                    FakeDownload(id = 2, source = "b"),
                    FakeDownload(id = 3, source = "c"),
                ),
                slots = 2,
                expectedIds = listOf(1, 2),
            ),
            Case(
                "finished or failed downloads are filtered out before slots are filled",
                queue = listOf(
                    FakeDownload(id = 1, source = "a", runnable = false),
                    FakeDownload(id = 2, source = "b"),
                    FakeDownload(id = 3, source = "c"),
                ),
                slots = 2,
                expectedIds = listOf(2, 3),
            ),
            Case(
                "nothing runnable: no slots are filled",
                queue = listOf(
                    FakeDownload(id = 1, source = "a", runnable = false),
                    FakeDownload(id = 2, source = "b", runnable = false),
                ),
                slots = 3,
                expectedIds = emptyList(),
            ),
        )
    }
}
