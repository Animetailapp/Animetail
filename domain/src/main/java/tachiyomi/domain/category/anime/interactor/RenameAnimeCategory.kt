package tachiyomi.domain.category.anime.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.model.Category

class RenameAnimeCategory(
    private val categoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(categoryId: Long, name: String) = withNonCancellableContext {
        try {
            categoryRepository.updateAnimeCategoryName(categoryId = categoryId, name = name)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    suspend fun await(category: Category, name: String) = await(category.id, name)

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
