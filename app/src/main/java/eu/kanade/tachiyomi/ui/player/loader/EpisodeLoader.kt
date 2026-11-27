package eu.kanade.tachiyomi.ui.player.loader

import android.content.Context
import eu.kanade.domain.items.episode.model.toSEpisode
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import kotlinx.coroutines.CancellationException
import mihon.app.di.appGraph
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem

/**
 * Loader used to retrieve the hosters for a given episode.
 */
class EpisodeLoader {
    companion object {

        /**
         * Returns a list of hosters of an [episode] based on the type of [source] used.
         *
         * @param episode the episode being parsed.
         * @param anime the anime of the episode.
         * @param source the source of the anime.
         * @param useCache whether a cached hoster list from a previous call can be reused.
         */
        suspend fun getHosters(
            context: Context,
            episode: Episode,
            anime: Anime,
            source: AnimeSource,
            useCache: Boolean = true,
        ): List<Hoster> {
            val isDownloaded = isDownload(context, episode, anime)
            return when {
                isDownloaded -> getHostersOnDownloaded(context, episode, anime, source)
                source is AnimeHttpSource -> getHostersOnHttp(context, episode, source, useCache)
                source is LocalAnimeSource -> getHostersOnLocal(context, episode)
                else -> error("source not supported")
            }
        }

        /**
         * Returns true if the given [episode] is downloaded.
         *
         * @param episode the episode being parsed.
         * @param anime the anime of the episode.
         */
        fun isDownload(context: Context, episode: Episode, anime: Anime): Boolean {
            val downloadManager = context.applicationContext.appGraph.animeDownloadManager
            return downloadManager.isEpisodeDownloaded(
                episode.name,
                episode.scanlator,
                episode.url,
                anime.title,
                anime.source,
                skipCache = true,
            )
        }

        private fun checkHasHosters(source: AnimeHttpSource): Boolean {
            var current: Class<in AnimeHttpSource> = source.javaClass
            while (true) {
                if (current == ParsedAnimeHttpSource::class.java ||
                    current == AnimeHttpSource::class.java ||
                    current == AnimeSource::class.java
                ) {
                    return false
                }
                if (current.declaredMethods.any {
                        it.name in
                            listOf("getHosterList", "hosterListRequest", "hosterListParse")
                    }
                ) {
                    return true
                }
                current = current.superclass ?: return false
            }
        }

        /**
         * Drops the cached hoster and video lists of [episode], so the next load hits the source.
         */
        fun invalidateCache(context: Context, episode: Episode, source: AnimeSource) {
            HosterCache.shared(context).invalidateEpisode(source.id, episode.url)
        }

        /**
         * Drops the cached video list of [hoster], e.g. after one of its links failed to play.
         */
        fun invalidateCache(context: Context, hoster: Hoster, source: AnimeSource) {
            HosterCache.shared(context).invalidateVideos(source.id, hoster)
        }

        /**
         * Returns a list of hosters when the [episode] is online.
         *
         * @param episode the episode being parsed.
         * @param source the online source of the episode.
         * @param useCache whether a cached hoster list can be reused.
         */
        private suspend fun getHostersOnHttp(
            context: Context,
            episode: Episode,
            source: AnimeHttpSource,
            useCache: Boolean,
        ): List<Hoster> {
            val hosterCache = HosterCache.shared(context)
            val cached = if (useCache) hosterCache.getHosters(source.id, episode.url) else null
            val hosters = cached ?: fetchHosters(episode, source)
                .also { hosterCache.putHosters(source.id, episode.url, it) }

            // Applied after the cache so a changed preference takes effect right away.
            val preferences = context.applicationContext.appGraph.playerPreferences
            val priority = HosterPriority(preferences.hosterPriority().get(), preferences.disabledHosters().get())
            return if (hosters.singleOrNull()?.hosterName == Hoster.NO_HOSTER_LIST) {
                hosters.map { hoster ->
                    hoster.copy(videoList = hoster.videoList?.let { videos -> priority.sort(videos) { it.videoTitle } })
                }
            } else {
                // Disabled hosters become lazy: they are only extracted when tapped in the player.
                priority.sort(hosters) { it.hosterName }.map { hoster ->
                    if (priority.isDisabled(hoster.hosterName)) hoster.copy(lazy = true) else hoster
                }
            }
        }

        fun hasCachedVideos(context: Context, hoster: Hoster, source: AnimeSource): Boolean {
            return HosterCache.shared(context).getVideos(source.id, hoster) != null
        }

        private suspend fun fetchHosters(episode: Episode, source: AnimeHttpSource): List<Hoster> {
            // TODO(1.6): Remove else block when dropping support for ext lib <1.6
            return if (checkHasHosters(source)) {
                source.getHosterList(episode.toSEpisode())
                    .let { source.run { it.sortHosters() } }
            } else {
                source.getVideoList(episode.toSEpisode())
                    .let { source.run { it.sortVideos() } }
                    .toHosterList()
            }
        }

        /**
         * Returns the hoster when the [episode] is downloaded.
         *
         * @param episode the episode being parsed.
         * @param anime the anime of the episode.
         * @param source the source of the anime.
         */
        private fun getHostersOnDownloaded(
            context: Context,
            episode: Episode,
            anime: Anime,
            source: AnimeSource,
        ): List<Hoster> {
            val downloadManager = context.applicationContext.appGraph.animeDownloadManager
            return try {
                val video = downloadManager.buildVideo(source, anime, episode)
                listOf(video).toHosterList()
            } catch (e: Throwable) {
                emptyList()
            }
        }

        /**
         * Returns the hoster when the [episode] is from local source.
         *
         * @param episode the episode being parsed.
         */
        private fun getHostersOnLocal(
            context: Context,
            episode: Episode,
        ): List<Hoster> {
            return try {
                val episodePath = episode.url.replace('\\', '/')
                val animeDirName = episodePath.substringBeforeLast('/', "")
                val episodeName = episodePath.substringAfterLast('/')
                val storageManager = context.applicationContext.appGraph.storageManager
                val fileSystem = LocalAnimeSourceFileSystem(storageManager)
                val videoFile = when {
                    animeDirName.isBlank() -> fileSystem.getBaseDirectory()?.findFile(episodeName)
                    else -> fileSystem.getAnimeDirectory(animeDirName)?.findFile(episodeName)
                }
                val videoUri = videoFile!!.uri

                val video = Video(
                    videoUri.toString(),
                    "Local source: ${episode.url}",
                )
                listOf(video).toHosterList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * Returns a list of videos of a [hoster] based on the type of [source] used.
         * Note that for every type of episode except non-downloaded online, `videoList`
         * will be set to null.
         *
         * @param source the source of the anime.
         * @param hoster the hoster.
         */
        private suspend fun getVideos(
            context: Context,
            source: AnimeSource,
            hoster: Hoster,
            useCache: Boolean,
        ): List<Video> {
            val videos = when {
                hoster.videoList != null && source is AnimeHttpSource -> hoster.videoList!!.parseVideoUrls(source)
                hoster.videoList != null -> hoster.videoList!!
                source is AnimeHttpSource -> getVideosOnHttp(context, source, hoster, useCache)
                else -> error("source not supported")
            }

            return if (source is AnimeHttpSource) {
                source.run { videos.sortVideos() }
            } else {
                videos
            }
        }

        /**
         * Returns a list of hosters when the [episode] is online.
         *
         * @param source the online source of the episode.
         * @param hoster the hoster.
         * @param useCache whether a cached video list can be reused.
         */
        private suspend fun getVideosOnHttp(
            context: Context,
            source: AnimeHttpSource,
            hoster: Hoster,
            useCache: Boolean,
        ): List<Video> {
            val hosterCache = HosterCache.shared(context)
            if (useCache) hosterCache.getVideos(source.id, hoster)?.let { return it }
            return source.getVideoList(hoster)
                .parseVideoUrls(source)
                .also { hosterCache.putVideos(source.id, hoster, it) }
        }

        // TODO(1.6): Remove after ext lib bump
        private suspend fun List<Video>.parseVideoUrls(source: AnimeHttpSource): List<Video> {
            return this.map { video ->
                if (video.videoUrl != "null") return@map video

                val newVideoUrl = source.getVideoUrl(video)
                video.copy(videoUrl = newVideoUrl)
            }
        }

        /**
         * Loads the videos of [hoster] into a [HosterState]. Lazy hosters stay [HosterState.Idle]
         * unless [force] is set; extraction failures become [HosterState.Error].
         */
        suspend fun loadHosterVideos(
            context: Context,
            source: AnimeSource,
            hoster: Hoster,
            force: Boolean = false,
            useCache: Boolean = true,
        ): HosterState {
            if (!force && hoster.lazy) {
                return HosterState.Idle(hoster.hosterName)
            }

            return try {
                val videos = getVideos(context, source, hoster, useCache)
                HosterState.Ready(hoster.hosterName, videos, List(videos.size) { Video.State.QUEUE })
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }

                HosterState.Error(hoster.hosterName)
            }
        }
    }
}
