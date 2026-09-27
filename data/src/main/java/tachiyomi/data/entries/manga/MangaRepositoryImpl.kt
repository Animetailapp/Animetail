package tachiyomi.data.entries.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaRemoteUpdate
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.manga.LibraryManga
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MangaRepositoryImpl(
    private val database: Database,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga {
        return database.mangasQueries
            .getMangaById(id, MangaMapper::mapManga)
            .awaitAsOne()
    }

    override fun getMangaByIdAsFlow(id: Long): Flow<Manga> {
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

    override suspend fun getUpcomingManga(
        statuses: Set<Long>,
        excludedCategories: List<Long>,
        includedCategories: List<Long>,
    ): Flow<List<Manga>> {
        val timeZone = TimeZone.currentSystemDefault()
        val epochMillis =
            Clock.System.now().toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone).toEpochMilliseconds()
        return database.mangasQueries
            .getUpcomingManga(
                startOfDay = epochMillis,
                statuses = statuses,
                includedEmpty = includedCategories.isEmpty(),
                includedCategories = includedCategories,
                excludedEmpty = excludedCategories.isEmpty(),
                excludedCategories = excludedCategories,
                mapper = MangaMapper::mapManga,
            )
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

    override suspend fun deleteNonLibraryManga(sourceIds: List<Long>, keepReadManga: Boolean) {
        database.mangasQueries.deleteNonLibraryManga(sourceIds, keepReadManga.toLong())
    }

    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        database.transaction {
            database.mangas_categoriesQueries.deleteMangaCategoryByMangaId(mangaId)
            categoryIds.forEach { categoryId ->
                database.mangas_categoriesQueries.insert(mangaId, categoryId)
            }
        }
    }

    override suspend fun getExcludedScanlators(mangaId: Long): Set<String> {
        return database.excluded_scanlatorsQueries
            .getExcludedScanlatorsByMangaId(mangaId)
            .awaitAsList()
            .toSet()
    }

    override fun getExcludedScanlatorsAsFlow(mangaId: Long): Flow<Set<String>> {
        return database.excluded_scanlatorsQueries
            .getExcludedScanlatorsByMangaId(mangaId)
            .subscribeToList()
            .map { it.toSet() }
    }

    override suspend fun setExcludedScanlators(mangaId: Long, excludedScanlators: Set<String>) {
        database.transaction {
            val current = database.excluded_scanlatorsQueries
                .getExcludedScanlatorsByMangaId(mangaId)
                .awaitAsList()
                .toSet()
            excludedScanlators.minus(current).forEach { scanlator ->
                database.excluded_scanlatorsQueries.insert(mangaId, scanlator)
            }
            database.excluded_scanlatorsQueries.remove(mangaId, current.minus(excludedScanlators))
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
                notes = manga.notes,
                memo = manga.memo,
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

    override suspend fun updateMangaRemote(update: MangaRemoteUpdate): Boolean {
        return try {
            database.mangasQueries.updateRemote(
                artist = update.artist,
                author = update.author,
                description = update.description,
                genre = update.genre,
                title = update.title,
                status = update.status,
                thumbnailUrl = update.thumbnailUrl,
                initialized = update.initialized,
                coverLastModified = update.coverLastModified,
                updateStrategy = update.updateStrategy,
                memo = update.memo,
                mangaId = update.id,
            )
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
                    favorite = value.favorite,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    viewer = value.viewerFlags,
                    chapterFlags = value.chapterFlags,
                    coverLastModified = value.coverLastModified,
                    dateAdded = value.dateAdded,
                    mangaId = value.id,
                    notes = value.notes,
                )
            }
        }
    }
}
