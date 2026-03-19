package tachiyomi.domain.entries.manga.interactor

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository

class NetworkToLocalManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(manga: Manga): Manga {
        val localManga = getManga(manga.url, manga.source)
        return when {
            localManga == null -> {
                val id = insertManga(manga)
                manga.copy(id = id!!)
            }
            manga.source == LOCAL_MANGA_SOURCE_ID -> {
                val mergedManga = mergeLocalManga(localManga, manga)
                if (mergedManga != localManga) {
                    mangaRepository.updateManga(mergedManga.toDatabaseUpdate())
                }
                mergedManga
            }
            !localManga.favorite -> {
                // if the manga isn't a favorite, set its display title from source
                // if it later becomes a favorite, updated title will go to db
                localManga.copy(ogTitle = manga.title)
            }
            else -> {
                localManga
            }
        }
    }

    private suspend fun getManga(url: String, sourceId: Long): Manga? {
        return mangaRepository.getMangaByUrlAndSourceId(url, sourceId)
    }

    private suspend fun insertManga(manga: Manga): Long? {
        return mangaRepository.insertManga(manga)
    }

    private fun mergeLocalManga(
        localManga: Manga,
        sourceManga: Manga,
    ): Manga {
        return localManga.copy(
            ogTitle = sourceManga.ogTitle,
            ogArtist = sourceManga.ogArtist ?: localManga.ogArtist,
            ogAuthor = sourceManga.ogAuthor ?: localManga.ogAuthor,
            ogDescription = sourceManga.ogDescription ?: localManga.ogDescription,
            ogGenre = sourceManga.ogGenre ?: localManga.ogGenre,
            ogStatus = sourceManga.ogStatus,
            thumbnailUrl = sourceManga.thumbnailUrl ?: localManga.thumbnailUrl,
            initialized = sourceManga.initialized || localManga.initialized,
        )
    }

    private fun Manga.toDatabaseUpdate(): MangaUpdate {
        return MangaUpdate(
            id = id,
            title = ogTitle,
            artist = ogArtist,
            author = ogAuthor,
            description = ogDescription,
            genre = ogGenre,
            status = ogStatus,
            thumbnailUrl = thumbnailUrl,
            initialized = initialized,
        )
    }

    private companion object {
        const val LOCAL_MANGA_SOURCE_ID = 0L
    }
}
