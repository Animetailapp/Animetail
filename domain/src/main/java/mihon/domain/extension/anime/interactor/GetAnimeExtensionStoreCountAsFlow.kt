package mihon.domain.extension.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.anime.repository.AnimeExtensionStoreRepository

@Inject
class GetAnimeExtensionStoreCountAsFlow(
    private val repository: AnimeExtensionStoreRepository,
) {
    fun subscribe(): Flow<Long> {
        return repository.getCountAsFlow()
    }
}
