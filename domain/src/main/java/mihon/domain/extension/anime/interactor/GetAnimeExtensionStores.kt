package mihon.domain.extension.anime.interactor

import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extension.model.ExtensionStore

class GetAnimeExtensionStores(
    private val repository: AnimeExtensionStoreRepository,
) {
    fun subscribe(): Flow<List<ExtensionStore>> {
        return repository.getAllAsFlow()
    }

    suspend fun await(): List<ExtensionStore> {
        return repository.getAll()
    }
}
