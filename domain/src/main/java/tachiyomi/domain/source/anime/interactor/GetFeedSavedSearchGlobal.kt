package tachiyomi.domain.source.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.anime.model.FeedSavedSearch
import tachiyomi.domain.source.anime.repository.FeedSavedSearchRepository

@Inject
class GetFeedSavedSearchGlobal(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    suspend fun await(): List<FeedSavedSearch> {
        return feedSavedSearchRepository.getGlobal()
    }

    fun subscribe(): Flow<List<FeedSavedSearch>> {
        return feedSavedSearchRepository.getGlobalAsFlow()
    }
}
