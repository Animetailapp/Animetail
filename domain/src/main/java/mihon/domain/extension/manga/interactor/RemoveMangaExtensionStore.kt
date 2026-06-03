package mihon.domain.extension.manga.interactor

import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository

class RemoveMangaExtensionStore(
    private val repository: MangaExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String) {
        repository.remove(indexUrl)
    }
}
