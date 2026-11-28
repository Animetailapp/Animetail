package eu.kanade.domain.entries.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository

@Inject
class SetAnimeAutoDownload(
    private val animeRepository: AnimeRepository,
) {
    /** [value] is [Anime.AUTO_DOWNLOAD_DEFAULT], [Anime.AUTO_DOWNLOAD_ALWAYS] or [Anime.AUTO_DOWNLOAD_NEVER]. */
    suspend fun await(animeId: Long, value: Long): Boolean {
        return animeRepository.updateAnime(AnimeUpdate(id = animeId, autoDownload = value))
    }
}
