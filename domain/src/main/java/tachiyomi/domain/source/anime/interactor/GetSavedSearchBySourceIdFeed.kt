package tachiyomi.domain.source.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.source.anime.model.SavedSearch
import tachiyomi.domain.source.anime.repository.FeedSavedSearchRepository

@Inject
class GetSavedSearchBySourceIdFeed(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(sourceId: Long): List<SavedSearch> {
        return feedSavedSearchRepository.getBySourceIdFeedSavedSearch(sourceId)
    }
}
