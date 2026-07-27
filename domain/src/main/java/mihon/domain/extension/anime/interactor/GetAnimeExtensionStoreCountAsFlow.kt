package mihon.domain.extension.anime.interactor

import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.anime.repository.AnimeExtensionStoreRepository

class GetAnimeExtensionStoreCountAsFlow(
    private val repository: AnimeExtensionStoreRepository,
) {
    fun subscribe(): Flow<Long> {
        return repository.getCountAsFlow()
    }
}
