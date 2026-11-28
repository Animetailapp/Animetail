package tachiyomi.domain.download.anime.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.domain.download.anime.model.AnimeDownloadPolicy
import tachiyomi.domain.download.anime.service.PlannedDeletion.Reason

class DownloadCleanupPlannerTest {

    data class ShowCase(
        val description: String,
        val episodes: List<CleanupEpisode>,
        val policy: AnimeDownloadPolicy,
        val expectedDeleted: List<Long>,
        val expectedDownloaded: List<Long>,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("showCases")
    fun `show policy deletes seen episodes and keeps the next unseen ones downloaded`(case: ShowCase) {
        val plan = DownloadCleanupPlanner.planShow(CleanupShow(ANIME, case.episodes, case.policy))

        assertEquals(case.expectedDeleted, plan.deletions.map { it.episodeId }, case.description)
        assertEquals(case.expectedDownloaded, plan.downloads.map { it.episodeId }, case.description)
    }

    data class LimitCase(
        val description: String,
        val shows: List<CleanupShow>,
        val totalBytes: Long,
        val limitBytes: Long,
        val expectedDeleted: List<Pair<Long, Reason>>,
        val expectedOverLimit: Long,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("limitCases")
    fun `storage limit deletes the oldest watched episodes, never unwatched ones`(case: LimitCase) {
        val plan = DownloadCleanupPlanner.plan(case.shows, case.totalBytes, case.limitBytes)

        assertEquals(case.expectedDeleted, plan.deletions.map { it.episodeId to it.reason }, case.description)
        assertEquals(case.expectedOverLimit, plan.bytesOverLimit, case.description)
    }

    @Test
    fun `freed bytes add up the deleted episodes`() {
        val show = CleanupShow(
            ANIME,
            listOf(seen(1, size = 300), seen(2, size = 200), unseen(3, size = 900)),
            DELETE_AFTER_SEEN,
        )

        assertEquals(500, DownloadCleanupPlanner.plan(listOf(show), totalBytes = 1400, limitBytes = 0).bytesFreed)
    }

    companion object {
        private const val ANIME = 1L
        private const val GB = 1_000_000_000L

        private val OFF = AnimeDownloadPolicy(
            deleteAfterSeen = false,
            keepSeen = 0,
            playerRemoveAfterSeenSlots = -1,
            deleteOnMarkedSeen = false,
            keepUnseen = 0,
            protected = false,
            deleteBookmarked = false,
            downloadFillers = false,
        )
        private val DELETE_AFTER_SEEN = OFF.copy(deleteAfterSeen = true)

        private fun seen(id: Long, size: Long = GB, watchedAt: Long = id, bookmark: Boolean = false) =
            CleanupEpisode(
                id,
                seen = true,
                bookmark,
                fillermark = false,
                downloaded = true,
                queued = false,
                size,
                watchedAt,
            )

        private fun unseen(
            id: Long,
            size: Long = GB,
            downloaded: Boolean = true,
            filler: Boolean = false,
            queued: Boolean = false,
        ) = CleanupEpisode(
            id,
            seen = false,
            bookmark = false,
            filler,
            downloaded,
            queued,
            if (downloaded) size else 0,
            watchedAt = 0,
        )

        private fun notDownloaded(episode: CleanupEpisode) = episode.copy(downloaded = false, size = 0)

        @JvmStatic
        fun showCases() = listOf(
            ShowCase(
                "Policy off: nothing happens even with a backlog of watched downloads",
                listOf(seen(1), seen(2), unseen(3)),
                OFF,
                expectedDeleted = emptyList(),
                expectedDownloaded = emptyList(),
            ),
            ShowCase(
                "Delete after watching removes every watched download, and no unwatched one",
                listOf(seen(1), seen(2), unseen(3), unseen(4)),
                DELETE_AFTER_SEEN,
                expectedDeleted = listOf(1, 2),
                expectedDownloaded = emptyList(),
            ),
            ShowCase(
                "Partly watched (not seen) episodes count as unwatched and stay",
                listOf(seen(1), unseen(2)),
                DELETE_AFTER_SEEN,
                expectedDeleted = listOf(1),
                expectedDownloaded = emptyList(),
            ),
            ShowCase(
                "keepSeen mirrors the player's 'second to last' slot: the last watched one stays",
                listOf(seen(1), seen(2), seen(3), unseen(4)),
                DELETE_AFTER_SEEN.copy(keepSeen = 1),
                expectedDeleted = listOf(1, 2),
                expectedDownloaded = emptyList(),
            ),
            ShowCase(
                "keepSeen counts watched episodes, downloaded or not, like the player's playlist slots",
                listOf(seen(1), seen(2), notDownloaded(seen(3)), unseen(4)),
                DELETE_AFTER_SEEN.copy(keepSeen = 1),
                expectedDeleted = listOf(1, 2),
                expectedDownloaded = emptyList(),
            ),
            ShowCase(
                "Bookmarked episodes are kept unless deleting bookmarked ones is allowed",
                listOf(seen(1, bookmark = true), seen(2)),
                DELETE_AFTER_SEEN,
                expectedDeleted = listOf(2),
                expectedDownloaded = emptyList(),
            ),
            ShowCase(
                "Deleting bookmarked episodes allowed",
                listOf(seen(1, bookmark = true), seen(2)),
                DELETE_AFTER_SEEN.copy(deleteBookmarked = true),
                expectedDeleted = listOf(1, 2),
                expectedDownloaded = emptyList(),
            ),
            ShowCase(
                "Protected show (never delete, or excluded category) deletes nothing",
                listOf(seen(1), seen(2)),
                DELETE_AFTER_SEEN.copy(protected = true),
                expectedDeleted = emptyList(),
                expectedDownloaded = emptyList(),
            ),
            ShowCase(
                "Keep 2 unseen: downloads the missing ones right after the last watched episode",
                listOf(seen(1), unseen(2), unseen(3, downloaded = false), unseen(4, downloaded = false)),
                OFF.copy(keepUnseen = 2),
                expectedDeleted = emptyList(),
                expectedDownloaded = listOf(3),
            ),
            ShowCase(
                "Keep unseen starts after the last watched episode, not at a skipped earlier one",
                listOf(
                    unseen(1, downloaded = false),
                    seen(2),
                    unseen(3, downloaded = false),
                    unseen(4, downloaded = false),
                ),
                OFF.copy(keepUnseen = 1),
                expectedDeleted = emptyList(),
                expectedDownloaded = listOf(3),
            ),
            ShowCase(
                "Nothing watched yet: keep unseen starts at the first episode",
                listOf(unseen(1, downloaded = false), unseen(2, downloaded = false), unseen(3, downloaded = false)),
                OFF.copy(keepUnseen = 2),
                expectedDeleted = emptyList(),
                expectedDownloaded = listOf(1, 2),
            ),
            ShowCase(
                "Fillers are skipped and don't count when they aren't downloaded",
                listOf(seen(1), unseen(2, downloaded = false, filler = true), unseen(3, downloaded = false)),
                OFF.copy(keepUnseen = 1),
                expectedDeleted = emptyList(),
                expectedDownloaded = listOf(3),
            ),
            ShowCase(
                "Already queued episodes aren't queued again",
                listOf(seen(1), unseen(2, downloaded = false, queued = true), unseen(3, downloaded = false)),
                OFF.copy(keepUnseen = 2),
                expectedDeleted = emptyList(),
                expectedDownloaded = listOf(3),
            ),
            ShowCase(
                "Caught up: nothing left to download",
                listOf(seen(1), seen(2)),
                OFF.copy(keepUnseen = 3),
                expectedDeleted = emptyList(),
                expectedDownloaded = emptyList(),
            ),
            ShowCase(
                "Rolling window: delete watched and top up unseen together",
                listOf(seen(1), seen(2), unseen(3), unseen(4, downloaded = false), unseen(5, downloaded = false)),
                DELETE_AFTER_SEEN.copy(keepUnseen = 2),
                expectedDeleted = listOf(1, 2),
                expectedDownloaded = listOf(4),
            ),
        )

        @JvmStatic
        fun limitCases() = listOf(
            LimitCase(
                "Under the limit: nothing is deleted",
                listOf(CleanupShow(ANIME, listOf(seen(1), seen(2)), OFF)),
                totalBytes = 2 * GB,
                limitBytes = 5 * GB,
                expectedDeleted = emptyList(),
                expectedOverLimit = 0,
            ),
            LimitCase(
                "Over by 1.5 GB: the two oldest watched episodes go, across shows, by watch time",
                listOf(
                    CleanupShow(1, listOf(seen(10, watchedAt = 300), seen(11, watchedAt = 100)), OFF),
                    CleanupShow(2, listOf(seen(20, watchedAt = 200), unseen(21)), OFF),
                ),
                totalBytes = 4 * GB + GB / 2,
                limitBytes = 3 * GB,
                expectedDeleted = listOf(11L to Reason.StorageLimit, 20L to Reason.StorageLimit),
                expectedOverLimit = 0,
            ),
            LimitCase(
                "Only unwatched episodes left: stays over the limit rather than deleting them",
                listOf(CleanupShow(ANIME, listOf(seen(1), unseen(2), unseen(3)), OFF)),
                totalBytes = 3 * GB,
                limitBytes = GB / 2,
                expectedDeleted = listOf(1L to Reason.StorageLimit),
                expectedOverLimit = 2 * GB - GB / 2,
            ),
            LimitCase(
                "Protected shows are never deleted for the limit",
                listOf(
                    CleanupShow(1, listOf(seen(10, watchedAt = 1)), OFF.copy(protected = true)),
                    CleanupShow(2, listOf(seen(20, watchedAt = 2)), OFF),
                ),
                totalBytes = 2 * GB,
                limitBytes = GB,
                expectedDeleted = listOf(20L to Reason.StorageLimit),
                expectedOverLimit = 0,
            ),
            LimitCase(
                "Episodes the show policy deletes count first, so the limit needs fewer",
                listOf(
                    CleanupShow(1, listOf(seen(10, watchedAt = 5)), DELETE_AFTER_SEEN),
                    CleanupShow(2, listOf(seen(20, watchedAt = 1), seen(21, watchedAt = 2)), OFF),
                ),
                totalBytes = 3 * GB,
                limitBytes = GB,
                expectedDeleted = listOf(10L to Reason.Seen, 20L to Reason.StorageLimit),
                expectedOverLimit = 0,
            ),
            LimitCase(
                "Watched without a watch time (marked as seen) counts as oldest",
                listOf(CleanupShow(ANIME, listOf(seen(1, watchedAt = 50), seen(2, watchedAt = 0)), OFF)),
                totalBytes = 2 * GB,
                limitBytes = GB,
                expectedDeleted = listOf(2L to Reason.StorageLimit),
                expectedOverLimit = 0,
            ),
        )
    }
}
