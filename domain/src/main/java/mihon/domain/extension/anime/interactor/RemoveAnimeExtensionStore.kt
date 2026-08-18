package mihon.domain.extension.anime.interactor

import dev.zacsweers.metro.Inject
import mihon.domain.extension.anime.repository.AnimeExtensionStoreRepository

@Inject
class RemoveAnimeExtensionStore(
    private val repository: AnimeExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String) {
        repository.remove(indexUrl)
    }
}
