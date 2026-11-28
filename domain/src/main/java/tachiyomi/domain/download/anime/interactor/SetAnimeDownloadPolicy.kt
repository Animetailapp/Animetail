package tachiyomi.domain.download.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.download.anime.model.AnimeDownloadPolicyOverride
import tachiyomi.domain.download.anime.repository.AnimeDownloadPolicyRepository

@Inject
class SetAnimeDownloadPolicy(
    private val repository: AnimeDownloadPolicyRepository,
) {

    suspend fun await(override: AnimeDownloadPolicyOverride) {
        repository.set(override)
    }
}
