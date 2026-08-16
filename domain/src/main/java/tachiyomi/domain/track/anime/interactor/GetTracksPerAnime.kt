package tachiyomi.domain.track.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository

@Inject
class GetTracksPerAnime(
    private val trackRepository: AnimeTrackRepository,
) {

    fun subscribe(): Flow<Map<Long, List<AnimeTrack>>> {
        return trackRepository.getAnimeTracksAsFlow().map { tracks -> tracks.groupBy { it.animeId } }
    }
}
