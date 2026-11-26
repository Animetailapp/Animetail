package tachiyomi.domain.download.anime.service

import tachiyomi.domain.download.anime.model.AnimeDownloadPolicy

/**
 * An episode as download cleanup sees it.
 *
 * @property size bytes the download takes on disk, 0 when not downloaded.
 * @property watchedAt when the episode was last watched (epoch ms), 0 when unknown.
 * @property queued whether the episode is already in the download queue.
 */
data class CleanupEpisode(
    val id: Long,
    val seen: Boolean,
    val bookmark: Boolean,
    val fillermark: Boolean,
    val downloaded: Boolean,
    val queued: Boolean,
    val size: Long,
    val watchedAt: Long,
)

/**
 * A show and its episodes in watching order (the first one to watch first).
 */
data class CleanupShow(
    val animeId: Long,
    val episodes: List<CleanupEpisode>,
    val policy: AnimeDownloadPolicy,
)

data class PlannedDeletion(
    val animeId: Long,
    val episodeId: Long,
    val size: Long,
    val reason: Reason,
) {
    enum class Reason {
        /** The show deletes episodes after they are seen. */
        Seen,

        /** Downloads are over the storage limit; the oldest watched episodes go first. */
        StorageLimit,
    }
}

data class PlannedDownload(
    val animeId: Long,
    val episodeId: Long,
)

/**
 * @property bytesOverLimit how far downloads stay over the storage limit after the deletions, when
 * there aren't enough watched episodes to delete. 0 when under the limit or without one.
 */
data class CleanupPlan(
    val deletions: List<PlannedDeletion>,
    val downloads: List<PlannedDownload>,
    val bytesOverLimit: Long,
) {
    val bytesFreed: Long
        get() = deletions.sumOf { it.size }

    val isEmpty: Boolean
        get() = deletions.isEmpty() && downloads.isEmpty()

    companion object {
        val EMPTY = CleanupPlan(emptyList(), emptyList(), 0)
    }
}

/**
 * Works out what the download cleanup policies would delete and download. It never deletes an
 * unseen episode: partly watched episodes count as unseen.
 */
object DownloadCleanupPlanner {

    /**
     * Plans the cleanup of [shows], then deletes the oldest watched episodes while the downloads,
     * [totalBytes] in all, are over [limitBytes] (no limit when 0 or less).
     */
    fun plan(shows: List<CleanupShow>, totalBytes: Long, limitBytes: Long): CleanupPlan {
        val showPlans = shows.map { planShow(it) }
        val deletions = showPlans.flatMap { it.deletions }
        val downloads = showPlans.flatMap { it.downloads }

        if (limitBytes <= 0) return CleanupPlan(deletions, downloads, 0)

        val deleted = deletions.mapTo(HashSet()) { it.episodeId }
        var remaining = totalBytes - deletions.sumOf { it.size }
        val limitDeletions = mutableListOf<PlannedDeletion>()
        if (remaining > limitBytes) {
            val candidates = shows
                .filterNot { it.policy.protected }
                .flatMap { show ->
                    show.episodes
                        .filter { it.id !in deleted && it.isDeletable(show.policy) }
                        .map { show.animeId to it }
                }
                // Unknown watch times (e.g. marked as seen without watching) count as oldest
                .sortedBy { (_, episode) -> episode.watchedAt }
            for ((animeId, episode) in candidates) {
                if (remaining <= limitBytes) break
                limitDeletions +=
                    PlannedDeletion(animeId, episode.id, episode.size, PlannedDeletion.Reason.StorageLimit)
                remaining -= episode.size
            }
        }

        return CleanupPlan(
            deletions = deletions + limitDeletions,
            downloads = downloads,
            bytesOverLimit = (remaining - limitBytes).coerceAtLeast(0),
        )
    }

    /**
     * Plans one show by its own policy: deletes its seen episodes except the last
     * [AnimeDownloadPolicy.keepSeen], and downloads the next [AnimeDownloadPolicy.keepUnseen]
     * unseen episodes after the last seen one.
     */
    fun planShow(show: CleanupShow): CleanupPlan {
        val policy = show.policy
        val episodes = show.episodes

        val deletions = if (policy.deleteAfterSeen && !policy.protected) {
            // Like the player, which keeps the episodes up to keepSeen before the one just watched
            val keptSeen = episodes.filter { it.seen }.takeLast(policy.keepSeen).mapTo(HashSet()) { it.id }
            episodes
                .filter { it.id !in keptSeen && it.isDeletable(policy) }
                .map { PlannedDeletion(show.animeId, it.id, it.size, PlannedDeletion.Reason.Seen) }
        } else {
            emptyList()
        }

        val downloads = if (policy.keepUnseen > 0) {
            val lastSeen = episodes.indexOfLast { it.seen }
            episodes
                .drop(lastSeen + 1)
                .filter { !it.seen && (policy.downloadFillers || !it.fillermark) }
                .take(policy.keepUnseen)
                .filter { !it.downloaded && !it.queued }
                .map { PlannedDownload(show.animeId, it.id) }
        } else {
            emptyList()
        }

        return CleanupPlan(deletions, downloads, 0)
    }

    private fun CleanupEpisode.isDeletable(policy: AnimeDownloadPolicy): Boolean {
        return downloaded && seen && (policy.deleteBookmarked || !bookmark)
    }
}
