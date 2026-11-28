package eu.kanade.tachiyomi.data.opdetect

import eu.kanade.tachiyomi.data.opdetect.SharedSegmentMatcherTest.Kind
import eu.kanade.tachiyomi.data.opdetect.SharedSegmentMatcherTest.Part
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class SegmentConsensusTest {

    data class Case(
        val description: String,
        /** Each episode's window, or null if it couldn't be read. */
        val episodes: List<List<Part>?>,
        /** Expected start frame per episode, or null for none. */
        val expectedStarts: List<Int?>,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `detected stretch per episode`(case: Case) {
        val fingerprints = case.episodes.mapIndexed { i, parts ->
            parts?.let { SharedSegmentMatcherTest.build(it, seed = 100 + i) }
        }
        val spans = SegmentConsensus().detect(fingerprints)
        case.expectedStarts.forEachIndexed { i, expected ->
            if (expected == null) {
                assertNull(spans[i], "episode $i: expected nothing, got ${spans[i]}")
            } else {
                val span = spans[i] ?: error("episode $i: expected a stretch at $expected")
                assertEquals(expected.toDouble(), span.start.toDouble(), 3.0, "episode $i: $span")
                assertEquals(OP.toDouble(), span.length.toDouble(), 6.0, "episode $i: $span")
            }
        }
    }

    companion object {
        private const val OP = 900
        private val U = Kind.Unique

        /** An episode window with the opening after [before] frames of unique audio. */
        private fun withOp(before: Int) = listOf(Part(before, U), Part(OP, Kind.Shared(7)), Part(2700 - before, U))

        private fun withoutOp() = listOf(Part(3600, U))

        /** Episode [index] shares a 20 s jingle with the next one only (each pair a different one). */
        private fun withPairJingle(index: Int) = listOf(
            Part(300, U),
            Part(200, Kind.Shared(1000 + index)),
            Part(300, U),
            Part(200, Kind.Shared(999 + index)),
            Part(300, U),
        )

        @JvmStatic
        fun cases() = listOf(
            Case(
                "every episode has the opening, at different offsets",
                episodes = listOf(withOp(0), withOp(450), withOp(1200), withOp(30), withOp(2000)),
                expectedStarts = listOf(0, 450, 1200, 30, 2000),
            ),
            Case(
                "two episodes: their one comparison is enough",
                episodes = listOf(withOp(100), withOp(900)),
                expectedStarts = listOf(100, 900),
            ),
            Case(
                "an episode without the opening (e.g. the first) gets none; its neighbours look further",
                episodes = listOf(withOp(300), withoutOp(), withOp(600), withOp(50), withOp(1500)),
                expectedStarts = listOf(300, null, 600, 50, 1500),
            ),
            Case(
                "an unreadable episode is skipped",
                episodes = listOf(withOp(300), null, withOp(600), withOp(50)),
                expectedStarts = listOf(300, null, 600, 50),
            ),
            Case(
                "music shared with one neighbour only is not enough with three or more episodes",
                episodes = listOf(withPairJingle(0), withPairJingle(1), withPairJingle(2), withPairJingle(3)),
                expectedStarts = listOf(null, null, null, null),
            ),
            Case(
                "no shared audio at all",
                episodes = listOf(withoutOp(), withoutOp(), withoutOp()),
                expectedStarts = listOf(null, null, null),
            ),
        )
    }
}
