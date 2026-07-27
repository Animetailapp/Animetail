package eu.kanade.domain.track.anime.interactor

import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.anime.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.model.AnimeTrack

class RefreshAnimeTracks(
    private val getTracks: GetAnimeTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertAnimeTrack,
    private val syncEpisodeProgressWithTrack: SyncEpisodeProgressWithTrack,
) {

    /**
     * Fetches updated tracking data from all logged in trackers.
     *
     * @return Failed updates.
     */
    suspend fun await(
        animeId: Long,
        // AM -->
        enhancedOnly: Boolean = false,
        skipCompleted: Boolean = false,
        // <-- AM
    ): List<RefreshResult> {
        return supervisorScope {
            return@supervisorScope getTracks.await(animeId)
                .map { it to trackerManager.get(it.trackerId) }
                .filter { (_, service) -> service?.isLoggedIn == true }
                // AM -->
                .filter { (_, service) -> !enhancedOnly || service is EnhancedAnimeTracker }
                // <-- AM
                .map { (track, service) ->
                    async {
                        return@async try {
                            // AM -->
                            if (!(skipCompleted && track.totalEpisodes == track.lastEpisodeSeen.toLong())) {
                                // <-- AM
                                val updatedTrack = service!!.animeService.refresh(track.toDbTrack()).toDomainTrack()!!
                                insertTrack.await(updatedTrack)
                                syncEpisodeProgressWithTrack.await(animeId, updatedTrack, service.animeService)
                            }

                            RefreshResult.Success(track)
                        } catch (e: Throwable) {
                            RefreshResult.Failure(service!!, e)
                        }
                    }
                }
                .awaitAll()
        }
    }
}

// AM -->
sealed interface RefreshResult {
    data class Failure(val tracker: Tracker, val error: Throwable) : RefreshResult
    data class Success(val track: AnimeTrack) : RefreshResult
}
// <-- AM
