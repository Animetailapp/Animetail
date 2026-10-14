package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.toBackupChapter
import eu.kanade.tachiyomi.data.backup.models.toBackupTracking
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mihon.core.common.extensions.toByteArray
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.track.manga.repository.MangaTrackRepository

@Inject
class MangaBackupCreator(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val categoryRepository: MangaCategoryRepository,
    private val historyRepository: MangaHistoryRepository,
    private val trackRepository: MangaTrackRepository,
) {

    /**
     * Builds the entries a chunk at a time, so only one chunk's chapters are held at once.
     */
    operator fun invoke(mangas: List<Manga>, options: BackupOptions): Flow<BackupManga> = flow {
        mangas.chunked(CHUNK_SIZE).forEach { chunk ->
            backupMangas(chunk, options).forEach { emit(it) }
        }
    }

    private suspend fun backupMangas(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        val mangaIds = mangas.map { it.id }

        val excludedScanlatorsByMangaId = mangaRepository.getExcludedScanlatorsByMangaIds(mangaIds)
        // History is backed up by chapter URL, so it needs the chapters too
        val chaptersByMangaId = if (options.chapters || options.history) {
            chapterRepository.getChaptersByMangaIds(mangaIds)
        } else {
            emptyMap()
        }
        val categoriesByMangaId = if (options.categories) {
            categoryRepository.getCategoriesByMangaIds(mangaIds)
        } else {
            emptyMap()
        }
        val tracksByMangaId = if (options.tracking) {
            trackRepository.getTracksByMangaIds(mangaIds).groupBy { it.mangaId }
        } else {
            emptyMap()
        }
        val historyByMangaId = if (options.history) historyRepository.getHistoryByMangaIds(mangaIds) else emptyMap()

        return mangas.map { manga ->
            val mangaObject = manga.toBackupManga()
            val chapters = chaptersByMangaId[manga.id].orEmpty()

            mangaObject.excludedScanlators = excludedScanlatorsByMangaId[manga.id].orEmpty()

            if (options.chapters) {
                mangaObject.chapters = chapters.map { it.toBackupChapter() }
            }

            if (options.categories) {
                mangaObject.categories = categoriesByMangaId[manga.id].orEmpty().map { it.order }
            }

            if (options.tracking) {
                mangaObject.tracking = tracksByMangaId[manga.id].orEmpty().map { it.toBackupTracking() }
            }

            if (options.history) {
                val chapterUrlsById = chapters.associate { it.id to it.url }
                mangaObject.history = historyByMangaId[manga.id].orEmpty().mapNotNull { history ->
                    // A chapter removed since its history was read takes that history with it
                    val url = chapterUrlsById[history.chapterId] ?: return@mapNotNull null
                    BackupHistory(url, history.readAt?.time ?: 0L, history.readDuration)
                }
            }

            mangaObject
        }
    }

    private companion object {
        const val CHUNK_SIZE = 100
    }
}

private fun Manga.toBackupManga() =
    BackupManga(
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
        dateAdded = this.favoriteAt ?: 0L,
        viewer = (this.viewerFlags.toInt() and ReadingMode.MASK),
        viewer_flags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        notes = this.notes,
        initialized = this.initialized,
        memo = this.memo.toByteArray(),
    )
