package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
@ContributesIntoSet(AppScope::class)
class CategoryPreferencesCleanupMigration(
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val getAnimeCategories: GetAnimeCategories,
    private val getMangaCategories: GetMangaCategories,
) : Migration {
    override val version: Float = 129f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val allAnimeCategories = getAnimeCategories.await().map { it.id.toString() }.toSet()
        val allMangaCategories = getMangaCategories.await().map { it.id.toString() }.toSet()

        val defaultAnimeCategory = libraryPreferences.defaultAnimeCategory.get()
        if (defaultAnimeCategory.toString() !in allAnimeCategories) {
            libraryPreferences.defaultAnimeCategory.delete()
        }
        val defaultCategory = libraryPreferences.defaultCategory.get()
        if (defaultCategory.toString() !in allMangaCategories) {
            libraryPreferences.defaultCategory.delete()
        }

        val categoryPreferences = listOf(
            libraryPreferences.animeUpdateCategories,
            libraryPreferences.updateCategories,
            libraryPreferences.animeUpdateCategoriesExclude,
            libraryPreferences.updateCategoriesExclude,
            downloadPreferences.removeExcludeCategories,
            downloadPreferences.removeExcludeAnimeCategories,
            downloadPreferences.downloadNewChapterCategories,
            downloadPreferences.downloadNewEpisodeCategories,
            downloadPreferences.downloadNewChapterCategoriesExclude,
            downloadPreferences.downloadNewEpisodeCategoriesExclude,
        )
        categoryPreferences.forEach { preference ->
            val ids = preference.get()
            val garbageIds = ids
                .minus(allAnimeCategories)
                .minus(allMangaCategories)
            if (garbageIds.isEmpty()) return@forEach
            preference.set(ids.minus(garbageIds))
        }
        return@withIOContext true
    }
}
