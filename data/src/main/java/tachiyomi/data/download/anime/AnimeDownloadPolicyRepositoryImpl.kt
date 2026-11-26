package tachiyomi.data.download.anime

import dataanime.Anime_download_policies
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.download.anime.model.AnimeDownloadPolicyOverride
import tachiyomi.domain.download.anime.repository.AnimeDownloadPolicyRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeDownloadPolicyRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : AnimeDownloadPolicyRepository {

    override suspend fun getByAnimeId(animeId: Long): AnimeDownloadPolicyOverride? {
        return handler.awaitOneOrNull { anime_download_policiesQueries.getByAnimeId(animeId) }?.let(::map)
    }

    override suspend fun getAll(): List<AnimeDownloadPolicyOverride> {
        return handler.awaitList { anime_download_policiesQueries.getAll() }.map(::map)
    }

    override fun subscribeByAnimeId(animeId: Long): Flow<AnimeDownloadPolicyOverride?> {
        return handler.subscribeToOneOrNull { anime_download_policiesQueries.getByAnimeId(animeId) }
            .map { it?.let(::map) }
    }

    override suspend fun set(override: AnimeDownloadPolicyOverride) {
        handler.await {
            if (override.isDefault) {
                anime_download_policiesQueries.deleteByAnimeId(override.animeId)
            } else {
                anime_download_policiesQueries.upsert(
                    animeId = override.animeId,
                    deleteAfterSeen = override.deleteAfterSeen,
                    keepUnseen = override.keepUnseen?.toLong(),
                )
            }
        }
    }

    private fun map(row: Anime_download_policies): AnimeDownloadPolicyOverride {
        return AnimeDownloadPolicyOverride(
            animeId = row.anime_id,
            deleteAfterSeen = row.delete_after_seen,
            keepUnseen = row.keep_unseen?.toInt(),
        )
    }
}
