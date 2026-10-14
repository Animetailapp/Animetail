package tachiyomi.domain.items.chapter.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterRemoteUpdate
import tachiyomi.domain.items.chapter.model.ChapterUpdate

interface ChapterRepository {

    suspend fun update(chapterUpdate: ChapterUpdate) = updateChapter(chapterUpdate)

    suspend fun updateChapter(chapterUpdate: ChapterUpdate)

    suspend fun updateAll(chapterUpdates: List<ChapterUpdate>) = updateAllChapters(chapterUpdates)

    suspend fun updateAllChapters(chapterUpdates: List<ChapterUpdate>)

    suspend fun updateFromRemote(
        removedIds: List<Long>,
        added: List<Chapter>,
        updated: List<ChapterRemoteUpdate>,
    ): List<Chapter>

    suspend fun getChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean = false): List<Chapter>

    suspend fun getChaptersByMangaIds(mangaIds: List<Long>): Map<Long, List<Chapter>>

    suspend fun getScanlatorsByMangaId(mangaId: Long): List<String>

    fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>>

    suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter>

    suspend fun getChapterById(id: Long): Chapter?

    fun getChapterByMangaIdAsFlow(mangaId: Long, applyScanlatorFilter: Boolean = false): Flow<List<Chapter>>

    suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter?
}
