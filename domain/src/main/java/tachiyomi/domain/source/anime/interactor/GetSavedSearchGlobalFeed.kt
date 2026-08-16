package tachiyomi.domain.source.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.source.anime.model.SavedSearch
import tachiyomi.domain.source.anime.repository.FeedSavedSearchRepository

@Inject
class GetSavedSearchGlobalFeed(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(): List<SavedSearch> {
        return feedSavedSearchRepository.getGlobalFeedSavedSearch()
    }
}
