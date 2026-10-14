package tachiyomi.domain.backup.model

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.manga.model.MangaTrack
import java.util.Date

data class RestoredManga(
    val manga: Manga,
    val chapters: List<Chapter>,
    val categoryIds: List<Long>,
    val history: List<RestoredHistory>,
    val tracks: List<MangaTrack>,
    val excludedScanlators: List<String>,
)

data class RestoredHistory(
    val chapterUrl: String,
    val readAt: Date?,
    val readDuration: Long,
)
