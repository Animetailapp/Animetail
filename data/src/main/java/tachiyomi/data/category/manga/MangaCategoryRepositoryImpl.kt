package tachiyomi.data.category.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MangaCategoryRepositoryImpl(
    private val database: Database,
) : MangaCategoryRepository {

    override suspend fun getMangaCategory(id: Long): Category? {
        return database.categoryQueries
            .getCategory(id, ::mapCategory)
            .awaitAsOneOrNull()
    }

    override suspend fun getAllMangaCategories(): List<Category> {
        return database.categoryQueries
            .getCategories(::mapCategory)
            .awaitAsList()
    }

    override suspend fun getAllVisibleMangaCategories(): List<Category> {
        return database.categoryQueries
            .getVisibleCategories(::mapCategory)
            .awaitAsList()
    }

    override fun getAllMangaCategoriesAsFlow(): Flow<List<Category>> {
        return database.categoryQueries
            .getCategories(::mapCategory)
            .subscribeToList()
    }

    override fun getAllVisibleMangaCategoriesAsFlow(): Flow<List<Category>> {
        return database.categoryQueries
            .getVisibleCategories(::mapCategory)
            .subscribeToList()
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return database.categoryQueries
            .getCategoriesByMangaId(mangaId, ::mapCategory)
            .awaitAsList()
    }

    override suspend fun getVisibleCategoriesByMangaId(mangaId: Long): List<Category> {
        return database.categoryQueries
            .getVisibleCategoriesByMangaId(mangaId, ::mapCategory)
            .awaitAsList()
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return database.categoryQueries
            .getCategoriesByMangaId(mangaId, ::mapCategory)
            .subscribeToList()
    }

    override fun getVisibleCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return database.categoryQueries
            .getVisibleCategoriesByMangaId(mangaId, ::mapCategory)
            .subscribeToList()
    }

    override suspend fun insertMangaCategory(category: NewCategory) {
        database.categoryQueries.insert(
            name = category.name,
            flags = category.flags,
        )
    }

    override suspend fun insertAllMangaCategories(categories: List<NewCategory>) {
        database.transaction {
            categories.forEach { insertMangaCategory(it) }
        }
    }

    override suspend fun updateMangaCategoryName(categoryId: Long, name: String) {
        database.categoryQueries.updateName(name = name, id = categoryId)
    }

    override suspend fun updateMangaCategoryFlags(categoryId: Long, flags: Long) {
        database.categoryQueries.updateFlags(flags = flags, id = categoryId)
    }

    override suspend fun updateMangaCategoryHidden(categoryId: Long, hidden: Boolean) {
        database.categoryQueries.updateHidden(hidden = if (hidden) 1L else 0L, id = categoryId)
    }

    override suspend fun updateAllMangaCategoryFlags(flags: Long) {
        database.categoryQueries.updateAllFlags(flags = flags)
    }

    override suspend fun updateMangaCategoryAllOrders(orderedIds: List<Long>) {
        database.transaction {
            val current = database.categoryQueries.getUserCategoryIds().awaitAsList()
            val ids = orderedIds.filter { it in current } + current.filterNot { it in orderedIds }
            ids.forEachIndexed { index, id ->
                database.categoryQueries.updateOrder(order = -index - 2L, id = id)
            }
            ids.forEachIndexed { index, id ->
                database.categoryQueries.updateOrder(order = index.toLong(), id = id)
            }
        }
    }

    override suspend fun deleteMangaCategory(categoryId: Long) {
        database.categoryQueries.delete(id = categoryId)
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
