package tachiyomi.domain.entries.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.entries.anime.repository.CustomAnimeRepository

@Inject
class GetCustomAnimeInfo(
    private val customAnimeRepository: CustomAnimeRepository,
) {

    fun get(animeId: Long) = customAnimeRepository.get(animeId)
}
