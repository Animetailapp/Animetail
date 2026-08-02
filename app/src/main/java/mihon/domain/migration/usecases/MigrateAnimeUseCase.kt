package mihon.domain.migration.usecases

import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.hasCustomCover
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.toEpisodeUpdate
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import java.time.Instant

class MigrateAnimeUseCase(
    private val sourcePreferences: SourcePreferences,
    private val trackerManager: TrackerManager,
    private val sourceManager: AnimeSourceManager,
    private val downloadManager: AnimeDownloadManager,
    private val updateAnime: UpdateAnime,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val updateEpisode: UpdateEpisode,
    private val getCategories: GetAnimeCategories,
    private val setAnimeCategories: SetAnimeCategories,
    private val getTracks: GetAnimeTracks,
    private val insertTrack: InsertAnimeTrack,
    private val coverCache: AnimeCoverCache,
) {
    private val enhancedServices by lazy { trackerManager.trackers.filterIsInstance<EnhancedAnimeTracker>() }

    suspend operator fun invoke(current: Anime, target: Anime, replace: Boolean) {
        val targetSource = sourceManager.get(target.source) ?: return
        val currentSource = sourceManager.get(current.source)
        val flags = MigrationFlag.fromBit(sourcePreferences.migrationFlags.get())

        try {
            val episodes = try {
                targetSource.getEpisodeList(target.toSAnime())
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e)
                emptyList()
            }

            try {
                syncEpisodesWithSource.await(episodes, target, targetSource)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e)
                // Worst case, episodes won't be synced
            }

            // Update episodes seen, bookmark and dateFetch
            if (MigrationFlag.CHAPTER in flags) {
                val prevAnimeEpisodes = getEpisodesByAnimeId.await(current.id)
                val animeEpisodes = getEpisodesByAnimeId.await(target.id)

                val maxEpisodeSeen = prevAnimeEpisodes
                    .filter { it.seen }
                    .maxOfOrNull { it.episodeNumber }

                val updatedAnimeEpisodes = animeEpisodes.map { animeEpisode ->
                    var updatedEpisode = animeEpisode
                    if (updatedEpisode.isRecognizedNumber) {
                        val prevEpisode = prevAnimeEpisodes
                            .find { it.isRecognizedNumber && it.episodeNumber == updatedEpisode.episodeNumber }

                        if (prevEpisode != null) {
                            updatedEpisode = updatedEpisode.copy(
                                dateFetch = prevEpisode.dateFetch,
                                bookmark = prevEpisode.bookmark,
                                fillermark = prevEpisode.fillermark,
                                lastSecondSeen = prevEpisode.lastSecondSeen,
                                totalSeconds = prevEpisode.totalSeconds,
                            )
                        }

                        if (prevEpisode?.seen == true ||
                            (maxEpisodeSeen != null && updatedEpisode.episodeNumber <= maxEpisodeSeen)
                        ) {
                            updatedEpisode = updatedEpisode.copy(seen = true)
                        }
                    }

                    updatedEpisode
                }

                val episodeUpdates = updatedAnimeEpisodes.map { it.toEpisodeUpdate() }
                updateEpisode.awaitAll(episodeUpdates)
            }

            // Update categories
            if (MigrationFlag.CATEGORY in flags) {
                val categoryIds = getCategories.await(current.id).map { it.id }
                setAnimeCategories.await(target.id, categoryIds)
            }

            // Update track
            getTracks.await(current.id).mapNotNull { track ->
                val updatedTrack = track.copy(animeId = target.id)

                val service = enhancedServices
                    .firstOrNull { it.isTrackFrom(updatedTrack, current, currentSource) }

                if (service != null) {
                    try {
                        service.migrateTrack(updatedTrack, target, targetSource)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.ERROR, e)
                        updatedTrack
                    }
                } else {
                    updatedTrack
                }
            }
                .takeIf { it.isNotEmpty() }
                ?.let { insertTrack.awaitAll(it) }

            // Delete downloaded
            if (MigrationFlag.REMOVE_DOWNLOAD in flags && currentSource != null) {
                try {
                    downloadManager.deleteAnime(current, currentSource)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e)
                }
            }

            // Update custom cover (recheck if custom cover exists)
            if (MigrationFlag.CUSTOM_COVER in flags && current.hasCustomCover(coverCache)) {
                try {
                    coverCache.setCustomCoverToCache(target, coverCache.getCustomCoverFile(current.id).inputStream())
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e)
                }
            }

            val currentAnimeUpdate = AnimeUpdate(
                id = current.id,
                favorite = false,
                dateAdded = 0,
            )
                .takeIf { replace }
            val targetAnimeUpdate = AnimeUpdate(
                id = target.id,
                favorite = true,
                episodeFlags = current.episodeFlags,
                viewerFlags = current.viewerFlags,
                dateAdded = if (replace) current.dateAdded else Instant.now().toEpochMilli(),
                notes = if (MigrationFlag.NOTES in flags) current.notes else null,
            )

            updateAnime.awaitAll(listOfNotNull(currentAnimeUpdate, targetAnimeUpdate))
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            logcat(LogPriority.ERROR, e)
        }
    }
}
