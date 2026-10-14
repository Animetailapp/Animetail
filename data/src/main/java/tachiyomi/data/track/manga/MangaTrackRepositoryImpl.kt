package tachiyomi.data.track.manga

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.track.manga.MangaTrackMapper.mapTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MangaTrackRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : MangaTrackRepository {

    override suspend fun getTrackByMangaId(id: Long): MangaTrack? {
        return handler.awaitOneOrNull { manga_trackQueries.getTrackById(id, MangaTrackMapper::mapTrack) }
    }

    // SY -->
    override suspend fun getMangaTracks(): List<MangaTrack> {
        return handler.awaitList {
            manga_trackQueries.getTracks(::mapTrack)
        }
    }

    override suspend fun getTracksByMangaIds(mangaIds: List<Long>): List<MangaTrack> {
        return handler.awaitList {
            manga_trackQueries.getTracksByMangaIds(mangaIds, ::mapTrack)
        }
    }
    // SY <--

    override suspend fun getTracksByMangaId(mangaId: Long): List<MangaTrack> {
        return handler.awaitList {
            manga_trackQueries.getTracksByMangaId(mangaId, MangaTrackMapper::mapTrack)
        }
    }

    override fun getMangaTracksAsFlow(): Flow<List<MangaTrack>> {
        return handler.subscribeToList {
            manga_trackQueries.getTracks(MangaTrackMapper::mapTrack)
        }
    }

    override fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<MangaTrack>> {
        return handler.subscribeToList {
            manga_trackQueries.getTracksByMangaId(mangaId, MangaTrackMapper::mapTrack)
        }
    }

    override suspend fun delete(mangaId: Long, trackerId: Long) {
        handler.await {
            manga_trackQueries.delete(
                mangaId = mangaId,
                trackerId = trackerId,
            )
        }
    }

    override suspend fun upsertManga(track: MangaTrack) {
        upsertValues(track)
    }

    override suspend fun upsertAllManga(tracks: List<MangaTrack>) {
        upsertValues(*tracks.toTypedArray())
    }

    override suspend fun insertManga(track: MangaTrack) {
        upsertValues(track)
    }

    override suspend fun insertAllManga(tracks: List<MangaTrack>) {
        upsertValues(*tracks.toTypedArray())
    }

    private suspend fun upsertValues(vararg tracks: MangaTrack) {
        handler.await(inTransaction = true) {
            tracks.forEach { mangaTrack ->
                manga_trackQueries.upsert(
                    mangaId = mangaTrack.mangaId,
                    trackerId = mangaTrack.trackerId,
                    remoteId = mangaTrack.remoteId,
                    libraryId = mangaTrack.libraryId,
                    title = mangaTrack.title,
                    lastChapterRead = mangaTrack.lastChapterRead,
                    totalChapters = mangaTrack.totalChapters,
                    status = mangaTrack.status,
                    score = mangaTrack.score,
                    remoteUrl = mangaTrack.remoteUrl,
                    startDate = mangaTrack.startDate,
                    finishDate = mangaTrack.finishDate,
                    private = mangaTrack.private,
                )
            }
        }
    }
}
