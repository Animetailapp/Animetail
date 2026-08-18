package tachiyomi.domain.category.anime.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.model.Category

@Inject
class HideAnimeCategory(
    private val categoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(category: Category) = withNonCancellableContext {
        try {
            categoryRepository.updateAnimeCategoryHidden(categoryId = category.id, hidden = !category.hidden)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed class Result {
        data object Success : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
