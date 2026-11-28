package eu.kanade.tachiyomi.data.download.anime

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.anime.interactor.GetAnimeDownloadPolicy
import tachiyomi.domain.download.anime.model.AnimeDownloadPolicy
import tachiyomi.domain.download.anime.model.AnimeDownloadPolicyOverride
import tachiyomi.domain.download.anime.service.CleanupEpisode
import tachiyomi.domain.download.anime.service.CleanupPlan
import tachiyomi.domain.download.anime.service.CleanupShow
import tachiyomi.domain.download.anime.service.DownloadCleanupPlanner
import tachiyomi.domain.download.anime.service.PlannedDeletion
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.service.getEpisodeSort
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Applies the download cleanup policies (see [DownloadCleanupPlanner]): a show's "delete after
 * watching" and "keep the next unseen episodes downloaded", and the storage limit.
 *
 * The storage limit is only enforced automatically once the user has reviewed and applied its
 * cleanup in the storage screen; until then, going over it only shows a notification.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeStorageCleaner(
    private val downloadManager: AnimeDownloadManager,
    private val provider: AnimeDownloadProvider,
    private val sizes: AnimeDownloadSizes,
    private val getAnimeDownloadPolicy: GetAnimeDownloadPolicy,
    private val getLibraryAnime: GetLibraryAnime,
    private val animeRepository: AnimeRepository,
    private val getAnime: GetAnime,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val getAnimeHistory: GetAnimeHistory,
    private val sourceManager: AnimeSourceManager,
    private val preferences: DownloadPreferences,
    private val notifier: AnimeDownloadNotifier,
) {

    /**
     * The downloaded anime directories, matched with the anime in the database they belong to
     * (in the library, or watched). [DownloadedAnime.anime] is null for directories of anime that
     * aren't in the database anymore.
     */
    suspend fun matchDownloads(shows: List<AnimeDownloadSizes.Show>): List<DownloadedAnime> {
        val library = getLibraryAnime.await()
        val categoriesByAnime = library.groupBy({ it.id }, { it.category })
        val candidates = library.distinctBy { it.id }.map { it.anime } + animeRepository.getWatchedAnimeNotInLibrary()
        val animeByDir = candidates.associateBy { it.source to provider.getAnimeDirName(it.title) }

        return shows.map { show ->
            val anime = show.sourceIds.firstNotNullOfOrNull { animeByDir[it to show.dirName] }
            DownloadedAnime(show, anime, anime?.let { categoriesByAnime[it.id] }.orEmpty())
        }
    }

    /** What the cleanup policies and storage limit would delete and download now. */
    suspend fun preview(): CleanupPreview {
        val state = sizes.await()
        val downloads = matchDownloads(state.shows).filter { it.anime != null }
        val overrides = getAnimeDownloadPolicy.awaitOverrides()
        val global = getAnimeDownloadPolicy.global()
        val contexts = downloads.map { downloaded ->
            val anime = downloaded.anime!!
            showContext(
                anime = anime,
                show = downloaded.show,
                policy = AnimeDownloadPolicy.resolve(global, overrides[anime.id], downloaded.categoryIds),
            )
        }
        val limitBytes = limitBytes()
        val plan = DownloadCleanupPlanner.plan(contexts.map { it.cleanupShow }, state.totalSize, limitBytes)
        return CleanupPreview(plan, contexts.associateBy { it.anime.id }, state.totalSize, limitBytes)
    }

    /**
     * What applying [override] to [anime] would delete and download now, given its downloads in
     * [show] (null when it has none).
     */
    suspend fun previewShow(
        anime: Anime,
        show: AnimeDownloadSizes.Show?,
        override: AnimeDownloadPolicyOverride,
        categoryIds: List<Long>,
    ): CleanupPreview {
        val policy = AnimeDownloadPolicy.resolve(getAnimeDownloadPolicy.global(), override, categoryIds)
        val context = showContext(anime, show, policy)
        val plan = DownloadCleanupPlanner.planShow(context.cleanupShow)
        return CleanupPreview(plan, mapOf(anime.id to context), totalBytes = 0, limitBytes = 0)
    }

    /** Deletes and downloads what [preview] planned. */
    fun apply(preview: CleanupPreview) {
        val plan = preview.plan
        plan.deletions.groupBy { it.animeId }.forEach { (animeId, deletions) ->
            val context = preview.shows[animeId] ?: return@forEach
            val source = sourceManager.get(context.anime.source) ?: return@forEach
            val episodes = deletions.mapNotNull { context.episodesById[it.episodeId] }
            downloadManager.deleteEpisodes(episodes, context.anime, source)
        }
        plan.downloads.groupBy { it.animeId }.forEach { (animeId, downloads) ->
            val context = preview.shows[animeId] ?: return@forEach
            downloadManager.downloadEpisodes(context.anime, downloads.mapNotNull { context.episodesById[it.episodeId] })
        }
        if (preview.limitBytes > 0) {
            preferences.animeDownloadStorageLimitAppliedGb.set(preferences.animeDownloadStorageLimitGb.get())
        }
        logcat(LogPriority.INFO) {
            "Storage cleanup: deleting ${plan.deletions.size} episode(s) (${plan.bytesFreed} bytes), " +
                "downloading ${plan.downloads.size}"
        }
    }

    /**
     * Downloads the next unseen episodes of [animeId] that its "keep the next unseen episodes
     * downloaded" policy asks for, after one of its episodes was seen.
     *
     * @param seenEpisodeIds episodes just seen that may not be saved as seen yet (the player saves
     * them when it leaves the episode).
     */
    suspend fun onEpisodesSeen(animeId: Long, seenEpisodeIds: Set<Long> = emptySet()) {
        val policy = getAnimeDownloadPolicy.await(animeId)
        if (policy.keepUnseen <= 0) return
        val anime = getAnime.await(animeId) ?: return
        val show = sizes.state.value.shows.find {
            it.dirName == provider.getAnimeDirName(anime.title) &&
                anime.source in it.sourceIds
        }
        // Seen episodes are deleted the way they always were (in the player, or when marked as
        // seen), not all at once here
        val context = showContext(anime, show, policy.copy(deleteAfterSeen = false), seenEpisodeIds)
        val plan = DownloadCleanupPlanner.planShow(context.cleanupShow)
        if (plan.downloads.isEmpty()) return
        apply(CleanupPreview(plan, mapOf(animeId to context), totalBytes = 0, limitBytes = 0))
    }

    /**
     * Deletes the oldest watched episodes while the downloads are over the storage limit, once the
     * user applied the limit from the storage screen. Before that, only notifies.
     */
    suspend fun enforceStorageLimit() {
        val limitGb = preferences.animeDownloadStorageLimitGb.get()
        if (limitGb <= 0) return
        val state = sizes.await()
        if (state.totalSize <= limitBytes()) return

        if (preferences.animeDownloadStorageLimitAppliedGb.get() != limitGb) {
            notifier.onStorageLimitExceeded(state.totalSize, limitBytes(), needsReview = true)
            return
        }

        val preview = preview()
        // Only the storage limit: the shows' own policies apply when episodes are watched
        val limitOnly = preview.copy(
            plan = preview.plan.copy(
                deletions = preview.plan.deletions.filter { it.reason == PlannedDeletion.Reason.StorageLimit },
                downloads = emptyList(),
            ),
        )
        if (limitOnly.plan.deletions.isNotEmpty()) apply(limitOnly)
        if (preview.plan.bytesOverLimit > 0) {
            notifier.onStorageLimitExceeded(
                state.totalSize - limitOnly.plan.bytesFreed,
                limitBytes(),
                needsReview = false,
            )
        }
    }

    private fun limitBytes(): Long = preferences.animeDownloadStorageLimitGb.get() * BYTES_PER_GB

    private suspend fun showContext(
        anime: Anime,
        show: AnimeDownloadSizes.Show?,
        policy: AnimeDownloadPolicy,
        seenEpisodeIds: Set<Long> = emptySet(),
    ): ShowContext {
        val entries = show?.entries.orEmpty().associateBy { it.name }
        val watchedAt = getAnimeHistory.await(anime.id).associate { it.episodeId to (it.seenAt?.time ?: 0L) }
        val queued = downloadManager.queueState.value.mapTo(HashSet()) { it.episode.id }
        val episodes = getEpisodesByAnimeId.await(anime.id).sortedWith(getEpisodeSort(anime, sortDescending = false))
        val cleanupEpisodes = episodes.map { episode ->
            val entry = provider.getValidEpisodeDirNames(episode.name, episode.scanlator, episode.url)
                .firstNotNullOfOrNull { entries[it] }
            CleanupEpisode(
                id = episode.id,
                seen = episode.seen || episode.id in seenEpisodeIds,
                bookmark = episode.bookmark,
                fillermark = episode.fillermark,
                downloaded = entry != null,
                queued = episode.id in queued,
                size = entry?.size?.coerceAtLeast(0) ?: 0,
                watchedAt = watchedAt[episode.id] ?: 0L,
            )
        }
        return ShowContext(
            anime = anime,
            episodesById = episodes.associateBy { it.id },
            cleanupShow = CleanupShow(anime.id, cleanupEpisodes, policy),
        )
    }

    data class DownloadedAnime(
        val show: AnimeDownloadSizes.Show,
        val anime: Anime?,
        val categoryIds: List<Long>,
    )

    class ShowContext(
        val anime: Anime,
        val episodesById: Map<Long, Episode>,
        val cleanupShow: CleanupShow,
    )

    /**
     * A [plan] with the anime and episodes it is about.
     *
     * @property limitBytes the storage limit it was planned with, 0 for none.
     */
    data class CleanupPreview(
        val plan: CleanupPlan,
        val shows: Map<Long, ShowContext>,
        val totalBytes: Long,
        val limitBytes: Long,
    )

    companion object {
        const val BYTES_PER_GB = 1000L * 1000 * 1000
    }
}
