package mihon.domain.extension.manga.interactor

import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository

class AddMangaExtensionStore(
    private val repository: MangaExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String): Result<Unit> {
        return repository.insert(indexUrl)
    }
}
