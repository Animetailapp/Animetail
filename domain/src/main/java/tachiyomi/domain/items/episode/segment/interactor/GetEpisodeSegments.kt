package tachiyomi.domain.items.episode.segment.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.episode.segment.model.EpisodeSegment
import tachiyomi.domain.items.episode.segment.repository.EpisodeSegmentRepository

@Inject
class GetEpisodeSegments(
    private val repository: EpisodeSegmentRepository,
) {

    suspend fun await(episodeId: Long): List<EpisodeSegment> {
        return try {
            repository.getByEpisodeId(episodeId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
