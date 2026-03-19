package tachiyomi.domain.updates.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.manga.model.MangaUpdatesWithRelations
import tachiyomi.domain.updates.manga.repository.MangaUpdatesRepository
import java.time.Instant

class GetMangaUpdates(
    private val repository: MangaUpdatesRepository,
) {

    suspend fun await(read: Boolean, after: Long): List<MangaUpdatesWithRelations> {
        return repository.awaitWithRead(read, after, limit = 500)
    }

    fun subscribe(
        instant: Instant,
        unread: Boolean? = null,
        started: Boolean? = null,
        bookmarked: Boolean? = null,
        hideExcludedScanlators: Boolean = false,
    ): Flow<List<MangaUpdatesWithRelations>> {
        return repository.subscribeAllMangaUpdates(
            instant.toEpochMilli(),
            limit = 500,
            unread = unread,
            started = started,
            bookmarked = bookmarked,
            hideExcludedScanlators = hideExcludedScanlators,
        )
    }

    fun subscribe(read: Boolean, after: Long): Flow<List<MangaUpdatesWithRelations>> {
        return repository.subscribeWithRead(read, after, limit = 500)
    }
}
