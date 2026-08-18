package eu.kanade.tachiyomi.util.episode

import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.source.local.entries.anime.isLocal

/**
 * Returns a copy of the list with not downloaded episodes removed.
 */
fun List<Episode>.filterDownloaded(anime: Anime, downloadCache: AnimeDownloadCache): List<Episode> {
    if (anime.isLocal()) return this

    return filter {
        downloadCache.isEpisodeDownloaded(
            it.name,
            it.scanlator,
            it.url,
            anime.title,
            anime.source,
            false,
        )
    }
}

fun List<Episode>.filterDownloadedEpisodes(anime: Anime, downloadCache: AnimeDownloadCache): List<Episode> {
    return filterDownloaded(anime, downloadCache)
}
