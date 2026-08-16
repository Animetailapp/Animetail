package mihon.domain.extension.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extension.model.ExtensionStore

@Inject
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
