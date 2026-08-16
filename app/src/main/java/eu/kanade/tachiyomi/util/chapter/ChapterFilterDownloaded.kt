package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.source.local.entries.manga.isLocal

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Chapter>.filterDownloaded(manga: Manga, downloadCache: MangaDownloadCache): List<Chapter> {
    if (manga.isLocal()) return this

    return filter {
        downloadCache.isChapterDownloaded(
            it.name,
            it.scanlator,
            it.url,
            manga.title,
            manga.source,
            false,
        )
    }
}
