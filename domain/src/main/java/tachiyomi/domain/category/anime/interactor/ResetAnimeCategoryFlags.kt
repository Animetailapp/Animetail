package tachiyomi.domain.category.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class ResetAnimeCategoryFlags(
    private val preferences: LibraryPreferences,
    private val categoryRepository: AnimeCategoryRepository,
) {

    suspend fun await() {
        val sort = preferences.animeSortingMode.get()
        categoryRepository.updateAllAnimeCategoryFlags(sort.type + sort.direction)
    }
}
