package tachiyomi.domain.category.manga.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category

@Inject
class HideMangaCategory(
    private val categoryRepository: MangaCategoryRepository,
) {

    suspend fun await(category: Category) = withNonCancellableContext {
        try {
            categoryRepository.updateMangaCategoryHidden(categoryId = category.id, hidden = !category.hidden)
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
