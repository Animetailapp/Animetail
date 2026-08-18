package tachiyomi.domain.entries.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.entries.anime.model.CustomAnimeInfo
import tachiyomi.domain.entries.anime.repository.CustomAnimeRepository

@Inject
class SetCustomAnimeInfo(
    private val customAnimeRepository: CustomAnimeRepository,
) {

    fun set(animeInfo: CustomAnimeInfo) = customAnimeRepository.set(animeInfo)
}
