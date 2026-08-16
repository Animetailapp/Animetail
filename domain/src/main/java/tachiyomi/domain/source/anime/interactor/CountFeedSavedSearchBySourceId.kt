package tachiyomi.domain.source.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.source.anime.repository.FeedSavedSearchRepository

@Inject
class CountFeedSavedSearchBySourceId(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(sourceId: Long): Long {
        return feedSavedSearchRepository.countBySourceId(sourceId)
    }
}
