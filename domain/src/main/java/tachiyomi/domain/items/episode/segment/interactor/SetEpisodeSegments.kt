package tachiyomi.domain.items.episode.segment.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.items.episode.segment.model.EpisodeSegment
import tachiyomi.domain.items.episode.segment.repository.EpisodeSegmentRepository

@Inject
class SetEpisodeSegments(
    private val repository: EpisodeSegmentRepository,
) {

    /** Replaces the episode's segments of [source] with [segments] (none clears them). */
    suspend fun await(episodeId: Long, source: EpisodeSegment.Source, segments: List<EpisodeSegment>) {
        require(segments.all { it.episodeId == episodeId && it.source == source })
        repository.replace(episodeId, source, segments)
    }
}
