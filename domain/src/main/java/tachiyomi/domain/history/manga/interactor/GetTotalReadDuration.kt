package tachiyomi.domain.history.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository

@Inject
class GetTotalReadDuration(
    private val repository: MangaHistoryRepository,
) {

    suspend fun await(): Long {
        return repository.getTotalReadDuration()
    }
}
