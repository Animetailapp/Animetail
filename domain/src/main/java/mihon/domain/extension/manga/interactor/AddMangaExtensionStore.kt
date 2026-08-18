package mihon.domain.extension.manga.interactor

import dev.zacsweers.metro.Inject
import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository

@Inject
class AddMangaExtensionStore(
    private val repository: MangaExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String): Result<Unit> {
        return repository.insert(indexUrl)
    }
}
