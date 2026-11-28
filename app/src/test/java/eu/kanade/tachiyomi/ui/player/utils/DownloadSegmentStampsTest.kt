package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.animesource.model.ChapterType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.domain.items.episode.segment.model.EpisodeSegment

class DownloadSegmentStampsTest {

    data class Case(
        val description: String,
        val existingTypes: Set<ChapterType>,
        val fileSize: Long = FILE_SIZE,
        val segments: List<EpisodeSegment> = defaultSegments,
        val expectedTypes: List<ChapterType>,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `stored segments only fill in what the video lacks`(case: Case) {
        val stamps = DownloadSegmentStamps.missingStamps(case.segments, case.existingTypes, case.fileSize)
        assertEquals(case.expectedTypes, stamps.map { it.type })
        stamps.forEach {
            val segment = case.segments.single { s -> s.startMs / 1000.0 == it.start }
            assertEquals(segment.startMs / 1000.0, it.start)
            assertEquals(segment.endMs / 1000.0, it.end)
        }
    }

    companion object {
        private const val FILE_SIZE = 123_456_789L

        private val defaultSegments = listOf(
            segment(EpisodeSegment.Type.Ending, 1_290_500, 1_380_000),
            segment(EpisodeSegment.Type.Opening, 0, 89_800),
        )

        private fun segment(type: EpisodeSegment.Type, start: Long, end: Long) = EpisodeSegment(
            episodeId = 1,
            type = type,
            source = EpisodeSegment.Source.Source,
            startMs = start,
            endMs = end,
            fileSize = FILE_SIZE,
            updatedAt = 0,
        )

        @JvmStatic
        fun cases() = listOf(
            Case(
                "no timestamps of its own: both, in time order",
                existingTypes = setOf(ChapterType.Other),
                expectedTypes = listOf(ChapterType.Opening, ChapterType.Ending),
            ),
            Case(
                "the video's own opening wins, the stored ending is still added",
                existingTypes = setOf(ChapterType.Opening, ChapterType.Other),
                expectedTypes = listOf(ChapterType.Ending),
            ),
            Case(
                "a mixed opening counts as an opening",
                existingTypes = setOf(ChapterType.MixedOp),
                expectedTypes = listOf(ChapterType.Ending),
            ),
            Case(
                "both supplied: nothing added",
                existingTypes = setOf(ChapterType.Opening, ChapterType.Ending),
                expectedTypes = emptyList(),
            ),
            Case(
                "an opening counts as a mixed opening",
                existingTypes = setOf(ChapterType.Opening),
                segments = listOf(segment(EpisodeSegment.Type.MixedOpening, 0, 89_800)),
                expectedTypes = emptyList(),
            ),
            Case(
                "a recap is kept",
                existingTypes = setOf(ChapterType.Opening),
                segments = listOf(segment(EpisodeSegment.Type.Recap, 0, 60_000)),
                expectedTypes = listOf(ChapterType.Recap),
            ),
            Case(
                "a different (re-downloaded) file: stored times don't apply",
                existingTypes = emptySet(),
                fileSize = FILE_SIZE + 1,
                expectedTypes = emptyList(),
            ),
        )
    }
}
