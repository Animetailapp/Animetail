package tachiyomi.data.track.manga

import tachiyomi.domain.track.manga.model.MangaTrack

object MangaTrackMapper {
    fun mapTrack(
        id: Long,
        mangaId: Long,
        trackerId: Long,
        remoteId: Long,
        remoteUrl: String,
        libraryId: Long?,
        title: String,
        lastChapterRead: Double,
        totalChapters: Long,
        status: Long,
        score: Double,
        startDate: Long,
        finishDate: Long,
        private: Boolean,
    ): MangaTrack = MangaTrack(
        id = id,
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startDate = startDate,
        finishDate = finishDate,
        private = private,
    )
}
