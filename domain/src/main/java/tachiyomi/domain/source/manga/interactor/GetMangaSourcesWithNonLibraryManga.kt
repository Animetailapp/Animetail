package tachiyomi.domain.source.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.manga.model.MangaSourceWithCount
import tachiyomi.domain.source.manga.repository.MangaSourceRepository

@Inject
class GetMangaSourcesWithNonLibraryManga(
    private val repository: MangaSourceRepository,
) {

    fun subscribe(): Flow<List<MangaSourceWithCount>> {
        return repository.getMangaSourcesWithNonLibraryManga()
    }
}
