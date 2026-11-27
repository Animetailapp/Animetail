package eu.kanade.tachiyomi.ui.player.utils

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.items.episode.segment.interactor.GetEpisodeSegments
import tachiyomi.domain.items.episode.segment.model.EpisodeSegment

/**
 * The stored openings/endings of a downloaded episode as player timestamps: the source's
 * timestamps saved when it was downloaded ([EpisodeSegment.Source.Source]), and those found by
 * opening/ending detection ([EpisodeSegment.Source.Detected], see
 * [eu.kanade.tachiyomi.data.opdetect.OpeningEndingDetectionJob]).
 */
@Inject
class DownloadSegmentStamps(
    private val context: Context,
    private val preferences: AdvancedPlayerPreferences,
    private val getEpisodeSegments: GetEpisodeSegments,
) {

    /**
     * Timestamps of [source] to add for [video] of [episodeId], given the chapter types it already
     * has (from the source's timestamps, AniSkip or the file). Empty unless the video is the
     * downloaded file the segments were stored for; detected ones only with detection enabled.
     */
    suspend fun forVideo(
        episodeId: Long,
        video: Video,
        existingTypes: Set<ChapterType>,
        source: EpisodeSegment.Source,
    ): List<TimeStamp> {
        if (source == EpisodeSegment.Source.Detected && !preferences.detectOpeningsEndings().get()) {
            return emptyList()
        }
        if (!video.videoTitle.startsWith(AnimeDownloadManager.DOWNLOADED_VIDEO_TITLE_PREFIX)) return emptyList()
        val segments = getEpisodeSegments.await(episodeId).filter { it.source == source }
        if (segments.isEmpty()) return emptyList()
        val fileSize = withIOContext { UniFile.fromUri(context, video.videoUrl.toUri())?.length() }
            ?: return emptyList()
        return missingStamps(segments, existingTypes, fileSize)
    }

    companion object {
        /**
         * The [segments] stored for the file of size [fileSize] whose type isn't covered by
         * [existingTypes]; timestamps added earlier (the video's own, then the stored source ones,
         * then AniSkip) win.
         */
        fun missingStamps(
            segments: List<EpisodeSegment>,
            existingTypes: Set<ChapterType>,
            fileSize: Long,
        ): List<TimeStamp> {
            return segments
                .filter { it.fileSize == fileSize }
                .mapNotNull { segment ->
                    val type = segment.type.toChapterType()
                    if (existingTypes.covers(type)) return@mapNotNull null
                    TimeStamp(
                        start = segment.startMs / 1000.0,
                        end = segment.endMs / 1000.0,
                        name = "",
                        type = type,
                    )
                }
                .sortedBy { it.start }
        }

        /** The segment type a timestamp of [this] type is stored as; null for untyped chapters. */
        fun ChapterType.toSegmentType(): EpisodeSegment.Type? = when (this) {
            ChapterType.Opening -> EpisodeSegment.Type.Opening
            ChapterType.Ending -> EpisodeSegment.Type.Ending
            ChapterType.Recap -> EpisodeSegment.Type.Recap
            ChapterType.MixedOp -> EpisodeSegment.Type.MixedOpening
            ChapterType.Other -> null
        }

        private fun EpisodeSegment.Type.toChapterType(): ChapterType = when (this) {
            EpisodeSegment.Type.Opening -> ChapterType.Opening
            EpisodeSegment.Type.Ending -> ChapterType.Ending
            EpisodeSegment.Type.Recap -> ChapterType.Recap
            EpisodeSegment.Type.MixedOpening -> ChapterType.MixedOp
        }

        /** An opening and a mixed opening stand for each other. */
        private fun Set<ChapterType>.covers(type: ChapterType): Boolean {
            val openings = setOf(ChapterType.Opening, ChapterType.MixedOp)
            return type in this || (type in openings && any { it in openings })
        }
    }
}
