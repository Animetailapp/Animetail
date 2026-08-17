package tachiyomi.domain.entries.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.anime.model.AnimeRelationGroup
import tachiyomi.domain.entries.anime.repository.AnimeRelationRepository

@Inject
class GetRelatedAnime(
    private val relationRepository: AnimeRelationRepository,
) {
    fun subscribe(animeId: Long): Flow<List<AnimeRelationGroup>> {
        return relationRepository.subscribeRelatedAnime(animeId)
    }
}
