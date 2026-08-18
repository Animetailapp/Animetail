package mihon.domain.extension.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository

@Inject
class GetMangaExtensionStoreCountAsFlow(
    private val repository: MangaExtensionStoreRepository,
) {
    fun subscribe(): Flow<Long> {
        return repository.getCountAsFlow()
    }
}
