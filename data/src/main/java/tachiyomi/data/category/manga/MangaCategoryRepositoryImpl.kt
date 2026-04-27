package tachiyomi.data.category.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate

class MangaCategoryRepositoryImpl(
    private val database: Database,
) : MangaCategoryRepository {

    override suspend fun getMangaCategory(id: Long): Category? {
        return database.categoriesQueries
            .getCategory(id, ::mapCategory)
            .awaitAsOneOrNull()
    }

    override suspend fun getAllMangaCategories(): List<Category> {
        return database.categoriesQueries
            .getCategories(::mapCategory)
            .awaitAsList()
    }

    override suspend fun getAllVisibleMangaCategories(): List<Category> {
        return database.categoriesQueries
            .getVisibleCategories(::mapCategory)
            .awaitAsList()
    }

    override fun getAllMangaCategoriesAsFlow(): Flow<List<Category>> {
        return database.categoriesQueries
            .getCategories(::mapCategory)
            .subscribeToList()
    }

    override fun getAllVisibleMangaCategoriesAsFlow(): Flow<List<Category>> {
        return database.categoriesQueries
            .getVisibleCategories(::mapCategory)
            .subscribeToList()
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return database.categoriesQueries
            .getCategoriesByMangaId(mangaId, ::mapCategory)
            .awaitAsList()
    }

    override suspend fun getVisibleCategoriesByMangaId(mangaId: Long): List<Category> {
        return database.categoriesQueries
            .getVisibleCategoriesByMangaId(mangaId, ::mapCategory)
            .awaitAsList()
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return database.categoriesQueries
            .getCategoriesByMangaId(mangaId, ::mapCategory)
            .subscribeToList()
    }

    override fun getVisibleCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return database.categoriesQueries
            .getVisibleCategoriesByMangaId(mangaId, ::mapCategory)
            .subscribeToList()
    }

    override suspend fun insertMangaCategory(category: Category) {
        database.categoriesQueries.insert(
            name = category.name,
            order = category.order,
            flags = category.flags,
        )
    }

    override suspend fun updatePartialMangaCategory(update: CategoryUpdate) {
        database.updatePartialBlocking(update)
    }

    override suspend fun updatePartialMangaCategories(updates: List<CategoryUpdate>) {
        updates.forEach { update ->
            updatePartialMangaCategory(update)
        }
    }

    private suspend fun Database.updatePartialBlocking(update: CategoryUpdate) {
        categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            hidden = update.hidden?.let { if (it) 1L else 0L },
            categoryId = update.id,
        )
    }

    override suspend fun updateAllMangaCategoryFlags(flags: Long?) {
        database.categoriesQueries.updateAllFlags(flags)
    }

    override suspend fun deleteMangaCategory(categoryId: Long) {
        database.categoriesQueries.delete(categoryId = categoryId)
    }

    private fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            hidden = hidden == 1L,
        )
    }
}
