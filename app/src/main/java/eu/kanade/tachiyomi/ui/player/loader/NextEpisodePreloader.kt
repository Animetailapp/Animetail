package eu.kanade.tachiyomi.ui.player.loader

import android.content.Context
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

    suspend fun preload(context: Context, episode: Episode, anime: Anime, source: AnimeSource) {
        if (source !is AnimeHttpSource || EpisodeLoader.isDownload(context, episode, anime)) return
        val hosters = EpisodeLoader.getHosters(context, episode, anime, source)
        val (hoster, video) = HosterLoader.getBestHosterVideo(context, source, hosters) ?: return
        val cacheKey = VideoCacheProxy.cacheKey(source.id, episode.url, hoster.hosterName, video.videoTitle)
        VideoCacheProxy.shared(context)?.warmUp(video.videoUrl, video.headers ?: source.headers, cacheKey)
    }
}
