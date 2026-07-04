package mihon.domain.extension.manga.interactor

import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extension.model.ExtensionStore

class GetMangaExtensionStores(
    private val repository: MangaExtensionStoreRepository,
) {
    fun subscribe(): Flow<List<ExtensionStore>> {
        return repository.getAllAsFlow()
    }

    suspend fun await(): List<ExtensionStore> {
        return repository.getAll()
    }
}
