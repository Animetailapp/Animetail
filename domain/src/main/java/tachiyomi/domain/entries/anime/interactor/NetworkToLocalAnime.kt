package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager

class NetworkToLocalAnime(
    private val animeRepository: AnimeRepository,
    private val sourceManager: AnimeSourceManager,
) {

    suspend fun await(anime: Anime): Anime {
        val localAnime = getAnime(anime.url, anime.source)
        return when {
            localAnime == null -> {
                val id = insertAnime(anime)
                anime.copy(id = id!!)
            }

            anime.source == LOCAL_ANIME_SOURCE_ID -> {
                val mergedAnime = mergeLocalAnime(localAnime, anime)
                if (mergedAnime != localAnime) {
                    animeRepository.updateAnime(mergedAnime.toDatabaseUpdate())
                }
                mergedAnime
            }

            !localAnime.favorite -> {
                // if the anime isn't a favorite, set its display title from source
                // if it later becomes a favorite, updated title will go to db
                localAnime.copy(ogTitle = anime.title)
            }

            else -> {
                localAnime
            }
        }
    }

    // KMK -->
    suspend fun getLocal(anime: Anime): Anime = if (anime.id <= 0) {
        await(anime)
    } else {
        anime
    }
    // KMK <--

    private suspend fun getAnime(url: String, sourceId: Long): Anime? {
        return animeRepository.getAnimeByUrlAndSourceId(url, sourceId)
    }

    private suspend fun insertAnime(anime: Anime): Long? {
        return animeRepository.insertAnime(anime)
    }

    private fun mergeLocalAnime(
        localAnime: Anime,
        sourceAnime: Anime,
    ): Anime {
        return localAnime.copy(
            cast = sourceAnime.cast ?: localAnime.cast,
            ogTitle = sourceAnime.ogTitle,
            ogArtist = sourceAnime.ogArtist ?: localAnime.ogArtist,
            ogAuthor = sourceAnime.ogAuthor ?: localAnime.ogAuthor,
            ogDescription = sourceAnime.ogDescription ?: localAnime.ogDescription,
            ogGenre = sourceAnime.ogGenre ?: localAnime.ogGenre,
            ogStatus = sourceAnime.ogStatus,
            thumbnailUrl = sourceAnime.thumbnailUrl ?: localAnime.thumbnailUrl,
            backgroundUrl = sourceAnime.backgroundUrl ?: localAnime.backgroundUrl,
            initialized = sourceAnime.initialized || localAnime.initialized,
            fetchType = sourceAnime.fetchType,
            parentId = sourceAnime.parentId ?: localAnime.parentId,
            seasonNumber = sourceAnime.seasonNumber.takeIf { it >= 0 } ?: localAnime.seasonNumber,
            seasonSourceOrder = sourceAnime.seasonSourceOrder.takeIf { it != 0L } ?: localAnime.seasonSourceOrder,
        )
    }

    private fun Anime.toDatabaseUpdate(): AnimeUpdate {
        return AnimeUpdate(
            id = id,
            cast = cast,
            title = ogTitle,
            artist = ogArtist,
            author = ogAuthor,
            description = ogDescription,
            genre = ogGenre,
            status = ogStatus,
            thumbnailUrl = thumbnailUrl,
            backgroundUrl = backgroundUrl,
            initialized = initialized,
            fetchType = fetchType,
            parentId = parentId,
            seasonNumber = seasonNumber,
            seasonSourceOrder = seasonSourceOrder,
        )
    }

    private companion object {
        const val LOCAL_ANIME_SOURCE_ID = 0L
    }
}
