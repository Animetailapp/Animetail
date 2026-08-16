package tachiyomi.domain.entries.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository

@Inject
class UpdateAnimeNotes(
    private val animeRepository: AnimeRepository,
) {

    suspend operator fun invoke(animeId: Long, notes: String): Boolean {
        return animeRepository.updateAnime(
            AnimeUpdate(
                id = animeId,
                notes = notes,
            ),
        )
    }
}
