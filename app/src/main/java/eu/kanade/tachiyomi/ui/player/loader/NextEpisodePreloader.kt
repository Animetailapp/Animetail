package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.player.cache.VideoCacheProxy
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

/**
 * Gets an episode ready before it's opened, so switching to it doesn't wait for link extraction.
 * Its hosters and the videos of the first working hoster end up in [HosterCache], picked in the
 * same priority order the player uses, and the start of that video is downloaded into the video
 * cache when it's enabled.
 */
object NextEpisodePreloader {

    suspend fun preload(episode: Episode, anime: Anime, source: AnimeSource) {
        if (source !is AnimeHttpSource || EpisodeLoader.isDownload(episode, anime)) return
        val hosters = EpisodeLoader.getHosters(episode, anime, source)
        val (_, video) = HosterLoader.getBestHosterVideo(source, hosters) ?: return
        VideoCacheProxy.shared?.warmUp(video.videoUrl, video.headers ?: source.headers)
    }
}
