package tachiyomi.domain.category.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category

interface AnimeCategoryRepository {

    suspend fun getAnimeCategory(id: Long): Category?

    suspend fun getAllAnimeCategories(): List<Category>

    suspend fun getAllVisibleAnimeCategories(): List<Category>

    fun getAllAnimeCategoriesAsFlow(): Flow<List<Category>>

    fun getAllVisibleAnimeCategoriesAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesByAnimeId(animeId: Long): List<Category>

    suspend fun getVisibleCategoriesByAnimeId(animeId: Long): List<Category>

    fun getCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<Category>>

    fun getVisibleCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<Category>>

    suspend fun insertAnimeCategory(category: Category)

    suspend fun updateAnimeCategoryName(categoryId: Long, name: String)

    suspend fun updateAnimeCategoryFlags(categoryId: Long, flags: Long)

    suspend fun updateAnimeCategoryHidden(categoryId: Long, hidden: Boolean)

    suspend fun updateAllAnimeCategoryFlags(flags: Long?)

    suspend fun updateAnimeCategoryAllOrders(orderedIds: List<Long>)

    suspend fun deleteAnimeCategory(categoryId: Long)
}
