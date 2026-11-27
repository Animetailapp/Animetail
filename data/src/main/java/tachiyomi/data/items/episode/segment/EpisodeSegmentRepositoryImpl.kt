package tachiyomi.data.items.episode.segment

import dataanime.Episode_segments
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.items.episode.segment.model.EpisodeSegment
import tachiyomi.domain.items.episode.segment.repository.EpisodeSegmentRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class EpisodeSegmentRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : EpisodeSegmentRepository {

    override suspend fun getByEpisodeId(episodeId: Long): List<EpisodeSegment> {
        return handler.awaitList { episode_segmentsQueries.getByEpisodeId(episodeId) }
            .mapNotNull { mapSegment(it) }
    }

    override suspend fun replace(episodeId: Long, source: EpisodeSegment.Source, segments: List<EpisodeSegment>) {
        handler.await(inTransaction = true) {
            episode_segmentsQueries.deleteByEpisodeIdAndSource(episodeId, source.dbValue)
            segments.forEach {
                episode_segmentsQueries.insert(
                    episodeId = it.episodeId,
                    type = it.type.dbValue,
                    source = it.source.dbValue,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    fileSize = it.fileSize,
                    updatedAt = it.updatedAt,
                )
            }
        }
    }

    private fun mapSegment(row: Episode_segments): EpisodeSegment? {
        return EpisodeSegment(
            episodeId = row.episode_id,
            type = EpisodeSegment.Type.fromDbValue(row.type) ?: return null,
            source = EpisodeSegment.Source.fromDbValue(row.source) ?: return null,
            startMs = row.start_ms,
            endMs = row.end_ms,
            fileSize = row.file_size,
            updatedAt = row.updated_at,
        )
    }
}
