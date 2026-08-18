package tachiyomi.domain.entries.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.entries.manga.repository.MangaRepository

@Inject
class ResetMangaViewerFlags(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): Boolean {
        return mangaRepository.resetMangaViewerFlags()
    }
}
