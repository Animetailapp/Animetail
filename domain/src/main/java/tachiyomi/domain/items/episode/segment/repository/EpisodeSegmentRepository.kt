package tachiyomi.domain.items.episode.segment.repository

import tachiyomi.domain.items.episode.segment.model.EpisodeSegment

interface EpisodeSegmentRepository {

    suspend fun getByEpisodeId(episodeId: Long): List<EpisodeSegment>

    /** Replaces the episode's segments of [source] with [segments]. */
    suspend fun replace(episodeId: Long, source: EpisodeSegment.Source, segments: List<EpisodeSegment>)
}
