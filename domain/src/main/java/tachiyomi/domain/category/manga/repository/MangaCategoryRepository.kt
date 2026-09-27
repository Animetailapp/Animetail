package tachiyomi.domain.category.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory

interface MangaCategoryRepository {

    suspend fun getMangaCategory(id: Long): Category?

    suspend fun getAllMangaCategories(): List<Category>

    suspend fun getAllVisibleMangaCategories(): List<Category>

    fun getAllMangaCategoriesAsFlow(): Flow<List<Category>>

    fun getAllVisibleMangaCategoriesAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesByMangaId(mangaId: Long): List<Category>

    suspend fun getVisibleCategoriesByMangaId(mangaId: Long): List<Category>

    fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>>

    fun getVisibleCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>>

    suspend fun insertMangaCategory(category: NewCategory)

    suspend fun insertAllMangaCategories(categories: List<NewCategory>)

    suspend fun insertAll(categories: List<NewCategory>) = insertAllMangaCategories(categories)

    suspend fun updateMangaCategoryName(categoryId: Long, name: String)

    suspend fun updateMangaCategoryFlags(categoryId: Long, flags: Long)

    suspend fun updateMangaCategoryHidden(categoryId: Long, hidden: Boolean)

    suspend fun updateAllMangaCategoryFlags(flags: Long?)

    suspend fun updateMangaCategoryAllOrders(orderedIds: List<Long>)

    suspend fun deleteMangaCategory(categoryId: Long)
}
