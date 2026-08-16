package tachiyomi.domain.entries.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.entries.anime.repository.AnimeRepository

@Inject
class ResetAnimeViewerFlags(
    private val animeRepository: AnimeRepository,
) {
    suspend fun await(): Boolean {
        return animeRepository.resetAnimeViewerFlags()
    }
}
