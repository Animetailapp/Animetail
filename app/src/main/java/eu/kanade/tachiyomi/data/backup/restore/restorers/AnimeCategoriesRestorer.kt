package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.model.NewCategory
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class AnimeCategoriesRestorer(
    private val categoryRepository: AnimeCategoryRepository,
    private val getAnimeCategories: GetAnimeCategories,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isEmpty()) return

        val dbCategories = getAnimeCategories.await()
        val dbCategoryNames = dbCategories.mapTo(HashSet()) { it.name }

        val newCategories = backupCategories
            .filter { it.name !in dbCategoryNames }
            .sortedBy { it.order }
        categoryRepository.insertAllAnimeCategories(newCategories.map { NewCategory(name = it.name, flags = it.flags) })

        val flags = buildSet {
            dbCategories.mapTo(this) { it.flags }
            newCategories.mapTo(this) { it.flags }
        }
        libraryPreferences.categorizedDisplaySettings.set(flags.size > 1)
    }
}
