package eu.kanade.domain.track.anime.interactor

import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.anime.model.toDomainTrack
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZoneOffset

class AddAnimeTracks(
    private val insertTrack: InsertAnimeTrack,
    private val syncChapterProgressWithTrack: SyncEpisodeProgressWithTrack,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val trackerManager: TrackerManager,
) {

    // TODO: update all trackers based on common data
    suspend fun bind(tracker: AnimeTracker, item: AnimeTrack, animeId: Long) = withNonCancellableContext {
        withIOContext {
            val allChapters = getEpisodesByAnimeId.await(animeId)
            val hasSeenEpisodes = allChapters.any { it.seen }
            tracker.bind(item, hasSeenEpisodes)

            var track = item.toDomainTrack(idRequired = false) ?: return@withIOContext

            insertTrack.await(track)

            // After successfully inserting the track, try to fetch cast from the tracker if available and persist it
            try {
                val updateAnime: UpdateAnime = Injekt.get()
                val getAnime: GetAnime = Injekt.get()
                val localAnime = getAnime.await(animeId)
                val titleForLookup = localAnime?.title

                try {
                    val cast = tracker.fetchCastByTitle(titleForLookup)
                    if (!cast.isNullOrEmpty()) {
                        updateAnime.await(
                            tachiyomi.domain.entries.anime.model.AnimeUpdate(
                                id = animeId,
                                cast = cast,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    // swallow individual tracker failures; do not fail the bind process
                    logcat(LogPriority.WARN, e) { "Could not fetch/persist cast from tracker after binding" }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not fetch/persist cast after binding tracker" }
            }

            // TODO: merge into [SyncChapterProgressWithTrack]?
            // Update chapter progress if newer chapters marked read locally
            if (hasSeenEpisodes) {
                val latestLocalReadChapterNumber = allChapters
                    .sortedBy { it.episodeNumber }
                    .takeWhile { it.seen }
                    .lastOrNull()
                    ?.episodeNumber ?: -1.0

                if (latestLocalReadChapterNumber > track.lastEpisodeSeen) {
                    track = track.copy(
                        lastEpisodeSeen = latestLocalReadChapterNumber,
                    )
                    tracker.setRemoteLastEpisodeSeen(track.toDbTrack(), latestLocalReadChapterNumber.toInt())
                }

                if (track.startDate <= 0) {
                    val firstReadChapterDate = Injekt.get<GetAnimeHistory>().await(animeId)
                        .sortedBy { it.seenAt }
                        .firstOrNull()
                        ?.seenAt

                    firstReadChapterDate?.let {
                        val startDate = firstReadChapterDate.time.convertEpochMillisZone(
                            ZoneOffset.systemDefault(),
                            ZoneOffset.UTC,
                        )
                        track = track.copy(
                            startDate = startDate,
                        )
                        tracker.setRemoteStartDate(track.toDbTrack(), startDate)
                    }
                }
            }

            syncChapterProgressWithTrack.await(animeId, track, tracker)
        }
    }

    suspend fun bindEnhancedTrackers(anime: Anime, source: AnimeSource) = withNonCancellableContext {
        withIOContext {
            trackerManager.trackers
                .filter { (it as? eu.kanade.tachiyomi.data.track.Tracker)?.isAvailableForUse() == true }
                .filterIsInstance<EnhancedAnimeTracker>()
                .filter { it.accept(source) }
                .forEach { service ->
                    try {
                        service.match(anime)?.let { track ->
                            track.anime_id = anime.id
                            try {
                                (service as Tracker).animeService.register(track, anime.id)
                            } catch (_: Exception) {
                                // Fallback to previous behavior if register fails for some reason
                                (service as Tracker).animeService.bind(track)
                                insertTrack.await(track.toDomainTrack(idRequired = false)!!)
                            }

                            try {
                                if (service is AnimeTracker) {
                                    try {
                                        val cast = (service as AnimeTracker).fetchCastByTitle(anime.title)
                                        if (!cast.isNullOrEmpty()) {
                                            Injekt.get<UpdateAnime>().await(
                                                tachiyomi.domain.entries.anime.model.AnimeUpdate(
                                                    id = anime.id,
                                                    cast = cast,
                                                ),
                                            )
                                        }
                                    } catch (e: Exception) {
                                        logcat(LogPriority.WARN, e) {
                                            "Could not fetch/persist cast from enhanced tracker"
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.WARN, e) {
                                    "Could not fetch/persist cast after enhanced tracker bind"
                                }
                            }

                            syncChapterProgressWithTrack.await(
                                anime.id,
                                track.toDomainTrack(idRequired = false)!!,
                                service.animeService,
                            )
                        }
                    } catch (e: Exception) {
                        logcat(
                            LogPriority.WARN,
                            e,
                        ) { "Could not match anime: ${anime.title} with service $service" }
                    }
                }
        }
    }
}
