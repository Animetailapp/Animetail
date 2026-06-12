package tachiyomi.data.entries.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.manga.LibraryManga
import java.time.LocalDate
import java.time.ZoneId

class MangaRepositoryImpl(
    private val database: Database,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga {
        return database.mangasQueries
            .getMangaById(id, MangaMapper::mapManga)
            .awaitAsOne()
    }

    override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> {
        return database.mangasQueries
            .getMangaById(id, MangaMapper::mapManga)
            .subscribeToOne()
    }

    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? {
        return database.mangasQueries
            .getMangaByUrlAndSource(url, sourceId, MangaMapper::mapManga)
            .awaitAsOneOrNull()
    }

    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> {
        return database.mangasQueries
            .getMangaByUrlAndSource(url, sourceId, MangaMapper::mapManga)
            .subscribeToOneOrNull()
    }

    override suspend fun getMangaFavorites(): List<Manga> {
        return database.mangasQueries
            .getFavorites(MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getReadMangaNotInLibrary(): List<Manga> {
        return database.mangasQueries
            .getReadMangaNotInLibrary(MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getLibraryManga(): List<LibraryManga> {
        return database.libraryViewQueries
            .library(MangaMapper::mapLibraryManga)
            .awaitAsList()
    }

    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> {
        return database.libraryViewQueries
            .library(MangaMapper::mapLibraryManga)
            .subscribeToList()
    }

    override fun getMangaFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return database.mangasQueries
            .getFavoriteBySourceId(sourceId, MangaMapper::mapManga)
            .subscribeToList()
    }

    override suspend fun getDuplicateLibraryManga(id: Long, title: String): List<Manga> {
        return database.mangasQueries
            .getDuplicateLibraryManga(id, title, MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>> {
        val epochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        return database.mangasQueries
            .getUpcomingManga(epochMillis, statuses, MangaMapper::mapManga)
            .subscribeToList()
    }

    override suspend fun resetMangaViewerFlags(): Boolean {
        return try {
            database.mangasQueries.resetViewerFlags()
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        database.transaction {
            database.mangas_categoriesQueries.deleteMangaCategoryByMangaId(mangaId)
            categoryIds.forEach { categoryId ->
                database.mangas_categoriesQueries.insert(mangaId, categoryId)
            }
        }
    }

    override suspend fun insertManga(manga: Manga): Long? {
        return database.transactionWithResult {
            database.mangasQueries.insertReturningId(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                title = manga.title,
                status = manga.status,
                thumbnailUrl = manga.thumbnailUrl,
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = manga.nextUpdate,
                calculateInterval = manga.fetchInterval.toLong(),
                initialized = manga.initialized,
                viewerFlags = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
                version = manga.version,
                notes = manga.notes,
            ).awaitAsOneOrNull()
        }
    }

    override suspend fun updateManga(update: MangaUpdate): Boolean {
        return try {
            partialUpdateManga(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllManga(mangaUpdates: List<MangaUpdate>): Boolean {
        return try {
            partialUpdateManga(*mangaUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdateManga(vararg mangaUpdates: MangaUpdate) {
        database.transaction {
            mangaUpdates.forEach { value ->
                database.mangasQueries.update(
                    source = value.source,
                    url = value.url,
                    artist = value.artist,
                    author = value.author,
                    description = value.description,
                    genre = value.genre?.let(StringListColumnAdapter::encode),
                    title = value.title,
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    favorite = value.favorite,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    initialized = value.initialized,
                    viewer = value.viewerFlags,
                    chapterFlags = value.chapterFlags,
                    coverLastModified = value.coverLastModified,
                    dateAdded = value.dateAdded,
                    mangaId = value.id,
                    updateStrategy = value.updateStrategy?.let(MangaUpdateStrategyColumnAdapter::encode),
                    version = value.version,
                    isSyncing = 0,
                    notes = value.notes,
                )
            }
        }
    }
}
