package tachiyomi.domain.download.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.download.anime.model.AnimeDownloadPolicyOverride

interface AnimeDownloadPolicyRepository {

    suspend fun getByAnimeId(animeId: Long): AnimeDownloadPolicyOverride?

    suspend fun getAll(): List<AnimeDownloadPolicyOverride>

    fun subscribeByAnimeId(animeId: Long): Flow<AnimeDownloadPolicyOverride?>

    /** Saves [override], or removes the show's row when it follows the global settings. */
    suspend fun set(override: AnimeDownloadPolicyOverride)
}
