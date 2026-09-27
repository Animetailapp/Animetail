package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class MangaCategoriesRestorer(
    private val categoryRepository: MangaCategoryRepository,
    private val getMangaCategories: GetMangaCategories,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getMangaCategories.await()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            val categories = backupCategories
                .sortedBy { it.order }
                .filter { it.name !in dbCategoriesByName }
                .map { it.toCategory(id = 0).copy(order = nextOrder++) }
            categoryRepository.insertAllMangaCategories(categories)

            libraryPreferences.categorizedDisplaySettings.set(
                (dbCategories + categories)
                    .distinctBy { it.flags }
                    .size > 1,
            )
        }
    }
}
