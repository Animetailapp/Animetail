package mihon.domain.extension.manga.interactor

import dev.zacsweers.metro.Inject
import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository

@Inject
class RemoveMangaExtensionStore(
    private val repository: MangaExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String) {
        repository.remove(indexUrl)
    }
}
