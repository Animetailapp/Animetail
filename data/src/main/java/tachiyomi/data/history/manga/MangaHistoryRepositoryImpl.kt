package tachiyomi.data.history.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.history.manga.model.MangaHistory
import tachiyomi.domain.history.manga.model.MangaHistoryUpdate
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository

class MangaHistoryRepositoryImpl(
    private val database: Database,
) : MangaHistoryRepository {

    override fun getMangaHistory(query: String): Flow<List<MangaHistoryWithRelations>> {
        return database.historyViewQueries
            .history(query, MangaHistoryMapper::mapMangaHistoryWithRelations)
            .subscribeToList()
    }

    override suspend fun getLastMangaHistory(): MangaHistoryWithRelations? {
        return database.historyViewQueries
            .getLatestHistory(MangaHistoryMapper::mapMangaHistoryWithRelations)
            .awaitAsOneOrNull()
    }

    override suspend fun getTotalReadDuration(): Long {
        return database.historyQueries
            .getReadDuration()
            .awaitAsOne()
    }

    override suspend fun getHistoryByMangaId(mangaId: Long): List<MangaHistory> {
        return database.historyQueries
            .getHistoryByMangaId(mangaId, MangaHistoryMapper::mapMangaHistory)
            .awaitAsList()
    }

    override suspend fun resetMangaHistory(historyId: Long) {
        try {
            database.historyQueries.resetHistoryById(historyId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByMangaId(mangaId: Long) {
        try {
            database.historyQueries.resetHistoryByMangaId(mangaId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllMangaHistory(): Boolean {
        return try {
            database.historyQueries.removeAllHistory()
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertMangaHistory(historyUpdate: MangaHistoryUpdate) {
        try {
            database.historyQueries.upsert(
                historyUpdate.chapterId,
                historyUpdate.readAt,
                historyUpdate.sessionReadDuration,
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
}
