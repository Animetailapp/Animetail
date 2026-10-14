package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.NewCategory
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class MangaCategoriesRestorer(
    private val categoryRepository: MangaCategoryRepository,
    private val getMangaCategories: GetMangaCategories,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isEmpty()) return

        val dbCategories = getMangaCategories.await()
        val dbCategoryNames = dbCategories.mapTo(HashSet()) { it.name }

        val newCategories = backupCategories
            .filter { it.name !in dbCategoryNames }
            .sortedBy { it.order }
        categoryRepository.insertAllMangaCategories(newCategories.map { NewCategory(name = it.name, flags = it.flags) })

        val flags = buildSet {
            dbCategories.mapTo(this) { it.flags }
            newCategories.mapTo(this) { it.flags }
        }
        libraryPreferences.categorizedDisplaySettings.set(flags.size > 1)
    }
}
