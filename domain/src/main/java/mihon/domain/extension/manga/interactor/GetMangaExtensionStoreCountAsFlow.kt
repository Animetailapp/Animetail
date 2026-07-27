package mihon.domain.extension.manga.interactor

import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository

class GetMangaExtensionStoreCountAsFlow(
    private val repository: MangaExtensionStoreRepository,
) {
    fun subscribe(): Flow<Long> {
        return repository.getCountAsFlow()
    }
}
