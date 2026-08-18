package mihon.domain.extension.manga.interactor

import dev.zacsweers.metro.Inject
import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository

@Inject
class UpdateMangaExtensionStores(
    private val repository: MangaExtensionStoreRepository,
) {
    suspend operator fun invoke() {
        repository.refreshAll()
    }
}
