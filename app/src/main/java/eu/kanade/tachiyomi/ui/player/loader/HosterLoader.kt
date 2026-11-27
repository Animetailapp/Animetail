package eu.kanade.tachiyomi.ui.player.loader

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import kotlin.coroutines.cancellation.CancellationException

class HosterLoader {
    companion object {
        /**
         * Check for the best video from the current hosterState.
         *
         * The first video with the `preferred` attribute is selected, however
         * if no such video is selected the first video with a non-empty url is selected.
         * If there are no viable videos at all, an error is thrown.
         *
         * @return the indices of the hoster & video
         */
        fun selectBestVideo(hosterState: List<HosterState>): Pair<Int, Int> {
            val availableHosters = hosterState.withIndex()
                .filter { (_, state) -> state is HosterState.Ready }

            // Check for first preferred
            val isPreferred: (Pair<Video, Video.State>) -> Boolean = { (v, s) ->
                v.preferred && (s == Video.State.READY || s == Video.State.QUEUE)
            }
            val prefHosterIdx = availableHosters.indexOfFirst {
                (it.value as HosterState.Ready).let { hoster ->
                    hoster.videoList zip hoster.videoState
                }.any(isPreferred)
            }
            if (prefHosterIdx != -1) {
                val videoList = (availableHosters[prefHosterIdx].value as HosterState.Ready).let { hoster ->
                    hoster.videoList zip hoster.videoState
                }
                val prefVideoIdx = videoList.indexOfFirst(isPreferred)
                return availableHosters[prefHosterIdx].index to prefVideoIdx
            }

            // Check for first video with non-empty url
            val firstValid: (Pair<Video, Video.State>) -> Boolean = { (v, s) ->
                v.videoUrl.isNotEmpty() && (s == Video.State.READY || s == Video.State.QUEUE)
            }
            val firstAvailableHosterIdx = availableHosters.indexOfFirst {
                (it.value as HosterState.Ready).let { hoster ->
                    hoster.videoList zip hoster.videoState
                }.any(firstValid)
            }
            if (firstAvailableHosterIdx != -1) {
                val videoList = (availableHosters[firstAvailableHosterIdx].value as HosterState.Ready).let { hoster ->
                    hoster.videoList zip hoster.videoState
                }
                val firstVideoIdx = videoList.indexOfFirst(firstValid)
                return availableHosters[firstAvailableHosterIdx].index to firstVideoIdx
            }

            // No success
            return Pair(-1, -1)
        }

        /**
         * Picks the video to play next while hosters are still loading, honouring the hoster order.
         *
         * A `preferred` video is returned as soon as its hoster is ready. Otherwise the first
         * playable video is taken from the first hoster that is ready, but only once every hoster
         * before it has settled, so a fast hoster never jumps ahead of a higher priority one.
         *
         * @return the indices of the hoster & video, `null` when an earlier hoster is still
         * loading, or `(-1, -1)` when every hoster settled without a playable video.
         */
        fun selectNextInOrder(hosterState: List<HosterState>): Pair<Int, Int>? {
            hosterState.forEachIndexed { hosterIdx, state ->
                if (state !is HosterState.Ready) return@forEachIndexed
                val videoIdx = state.indexOfFirstPlayable { it.preferred }
                if (videoIdx != -1) return hosterIdx to videoIdx
            }

            hosterState.forEachIndexed { hosterIdx, state ->
                when (state) {
                    is HosterState.Loading -> return null

                    is HosterState.Ready -> {
                        val videoIdx = state.indexOfFirstPlayable { it.videoUrl.isNotEmpty() }
                        if (videoIdx != -1) return hosterIdx to videoIdx
                    }

                    is HosterState.Idle, is HosterState.Error -> {}
                }
            }
            return -1 to -1
        }

        private fun HosterState.Ready.indexOfFirstPlayable(predicate: (Video) -> Boolean): Int {
            return videoList.indices.firstOrNull { idx ->
                val state = videoState[idx]
                (state == Video.State.QUEUE || state == Video.State.READY) && predicate(videoList[idx])
            } ?: -1
        }

        /**
         * Return the first video that resolves, trying hosters one at a time in priority order so
         * hosters after the working one are never extracted. Within a hoster, `preferred` videos
         * are tried first.
         *
         * @param source The source for the episode
         * @param hosterList the list of hosters
         * @return the video, or null if no valid video was found
         */
        suspend fun getBestVideo(context: Context, source: AnimeSource, hosterList: List<Hoster>): Video? =
            getBestHosterVideo(context, source, hosterList)?.second

        /**
         * Like [getBestVideo], but also returns the hoster of the video.
         *
         * @param isExcluded skips videos that already failed, e.g. in a previous download attempt.
         * It receives the video before it's resolved.
         */
        suspend fun getBestHosterVideo(
            context: Context,
            source: AnimeSource,
            hosterList: List<Hoster>,
            isExcluded: (Hoster, Video) -> Boolean = { _, _ -> false },
        ): Pair<Hoster, Video>? {
            hosterList.forEach { hoster ->
                val hosterState = EpisodeLoader.loadHosterVideos(context, source, hoster)
                    .markExcluded { isExcluded(hoster, it) }
                if (hosterState !is HosterState.Ready) return@forEach

                val candidates = hosterState.videoList.indices
                    .filter { hosterState.videoState[it] == Video.State.QUEUE }
                    .sortedByDescending { hosterState.videoList[it].preferred }
                candidates.forEach { videoIdx ->
                    val resolvedVideo = getResolvedVideo(source, hosterState.videoList[videoIdx])
                    if (resolvedVideo?.videoUrl?.isNotEmpty() == true) return hoster to resolvedVideo
                }
            }
            return null
        }

        private fun HosterState.markExcluded(isExcluded: (Video) -> Boolean): HosterState {
            if (this !is HosterState.Ready) return this
            return copy(
                videoState = videoState.mapIndexed { idx, state ->
                    if (isExcluded(videoList[idx])) Video.State.ERROR else state
                },
            )
        }

        suspend fun getResolvedVideo(source: AnimeSource?, video: Video): Video? {
            val resolvedVideo = if (source is AnimeHttpSource && !video.initialized) {
                try {
                    source.resolveVideo(video)
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        throw e
                    }

                    null
                }
            } else {
                video
            }

            return resolvedVideo?.copy(initialized = true)
        }
    }
}
