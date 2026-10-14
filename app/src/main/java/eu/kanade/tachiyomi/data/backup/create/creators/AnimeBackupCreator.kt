package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeHistory
import eu.kanade.tachiyomi.data.backup.models.toBackupEpisode
import eu.kanade.tachiyomi.data.backup.models.toBackupTracking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository

@Inject
class AnimeBackupCreator(
    private val episodeRepository: EpisodeRepository,
    private val categoryRepository: AnimeCategoryRepository,
    private val historyRepository: AnimeHistoryRepository,
    private val trackRepository: AnimeTrackRepository,
) {

    /**
     * Builds the entries a chunk at a time, so only one chunk's episodes are held at once.
     */
    operator fun invoke(animes: List<Anime>, options: BackupOptions): Flow<BackupAnime> = flow {
        animes.chunked(CHUNK_SIZE).forEach { chunk ->
            backupAnimes(chunk, options).forEach { emit(it) }
        }
    }

    private suspend fun backupAnimes(animes: List<Anime>, options: BackupOptions): List<BackupAnime> {
        val animeIds = animes.map { it.id }

        // History is backed up by episode URL, so it needs the episodes too
        val episodesByAnimeId = if (options.chapters || options.history) {
            episodeRepository.getEpisodesByAnimeIds(animeIds)
        } else {
            emptyMap()
        }
        val categoriesByAnimeId = if (options.categories) {
            categoryRepository.getCategoriesByAnimeIds(animeIds)
        } else {
            emptyMap()
        }
        val tracksByAnimeId = if (options.tracking) {
            trackRepository.getTracksByAnimeIds(animeIds).groupBy { it.animeId }
        } else {
            emptyMap()
        }
        val historyByAnimeId = if (options.history) historyRepository.getHistoryByAnimeIds(animeIds) else emptyMap()

        return animes.map { anime ->
            val animeObject = anime.toBackupAnime()
            val episodes = episodesByAnimeId[anime.id].orEmpty()

            if (options.chapters) {
                animeObject.episodes = episodes.map { it.toBackupEpisode() }
            }

            if (options.categories) {
                animeObject.categories = categoriesByAnimeId[anime.id].orEmpty().map { it.order }
            }

            if (options.tracking) {
                animeObject.tracking = tracksByAnimeId[anime.id].orEmpty().map { it.toBackupTracking() }
            }

            if (options.history) {
                val episodeUrlsById = episodes.associate { it.id to it.url }
                animeObject.history = historyByAnimeId[anime.id].orEmpty().mapNotNull { history ->
                    // An episode removed since its history was seen takes that history with it
                    val url = episodeUrlsById[history.episodeId] ?: return@mapNotNull null
                    BackupAnimeHistory(url, history.seenAt?.time ?: 0L)
                }
            }

            animeObject
        }
    }

    private companion object {
        const val CHUNK_SIZE = 100
    }
}

private fun Anime.toBackupAnime() =
    BackupAnime(
        url = this.url,
        title = this.title,
        artist = this.artist,
        author = this.author,
        description = this.description,
        genre = this.genre.orEmpty(),
        status = this.status.toInt(),
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewer_flags = this.viewerFlags.toInt(),
        episodeFlags = this.episodeFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        notes = this.notes,
        fetchType = this.fetchType,
        parentId = this.parentId,
        id = this.id,
        seasonFlags = this.seasonFlags,
        seasonNumber = this.seasonNumber,
        seasonSourceOrder = this.seasonSourceOrder,
        memo = MemoColumnAdapter.encode(this.memo),
    )
