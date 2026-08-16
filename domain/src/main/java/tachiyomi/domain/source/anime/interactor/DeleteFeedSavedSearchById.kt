package tachiyomi.domain.source.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.source.anime.repository.FeedSavedSearchRepository

@Inject
class DeleteFeedSavedSearchById(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(feedSavedSearchId: Long) {
        return feedSavedSearchRepository.delete(feedSavedSearchId)
    }
}
