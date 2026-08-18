package tachiyomi.domain.category.anime.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class DeleteAnimeCategory(
    private val categoryRepository: AnimeCategoryRepository,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
) {

    suspend fun await(categoryId: Long) = withNonCancellableContext {
        try {
            categoryRepository.deleteAnimeCategory(categoryId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val orderedIds = categoryRepository.getAllAnimeCategories().map { it.id }

        val defaultCategory = libraryPreferences.defaultAnimeCategory.get()
        if (defaultCategory == categoryId.toInt()) {
            libraryPreferences.defaultAnimeCategory.delete()
        }

        val categoryPreferences = listOf(
            libraryPreferences.animeUpdateCategories,
            libraryPreferences.animeUpdateCategoriesExclude,
            downloadPreferences.removeExcludeAnimeCategories,
            downloadPreferences.downloadNewEpisodeCategories,
            downloadPreferences.downloadNewEpisodeCategoriesExclude,
        )
        val categoryIdString = categoryId.toString()
        categoryPreferences.forEach { preference ->
            val ids = preference.get()
            if (categoryIdString !in ids) return@forEach
            preference.set(ids.minus(categoryIdString))
        }

        try {
            categoryRepository.updateAnimeCategoryAllOrders(orderedIds = orderedIds)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
