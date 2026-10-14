package tachiyomi.domain.track.manga.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository

@Inject
class UpsertMangaTrack(
    private val trackRepository: MangaTrackRepository,
) {

    suspend fun await(track: MangaTrack) {
        try {
            trackRepository.upsertManga(track)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitAll(tracks: List<MangaTrack>) {
        try {
            trackRepository.upsertAllManga(tracks)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}

typealias InsertMangaTrack = UpsertMangaTrack
