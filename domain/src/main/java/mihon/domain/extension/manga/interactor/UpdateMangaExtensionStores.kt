package mihon.domain.extension.manga.interactor

import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository

class UpdateMangaExtensionStores(
    private val repository: MangaExtensionStoreRepository,
) {
    suspend operator fun invoke() {
        repository.refreshAll()
    }
}
