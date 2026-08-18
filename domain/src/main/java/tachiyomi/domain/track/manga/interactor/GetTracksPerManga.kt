package tachiyomi.domain.track.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository

@Inject
class GetTracksPerManga(
    private val trackRepository: MangaTrackRepository,
) {

    fun subscribe(): Flow<Map<Long, List<MangaTrack>>> {
        return trackRepository.getMangaTracksAsFlow().map { tracks -> tracks.groupBy { it.mangaId } }
    }
}
