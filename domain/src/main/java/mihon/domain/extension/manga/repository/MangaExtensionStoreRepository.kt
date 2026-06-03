package mihon.domain.extension.manga.repository

import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.model.ExtensionStore

interface MangaExtensionStoreRepository {
    suspend fun insert(indexUrl: String): Result<Unit>

    suspend fun insertFromPreference(indexUrl: String, name: String)

    suspend fun refreshAll()

    suspend fun fetchExtensions(): List<Any>

    suspend fun getAll(): List<ExtensionStore>

    fun getAllAsFlow(): Flow<List<ExtensionStore>>

    fun getCountAsFlow(): Flow<Long>

    suspend fun remove(indexUrl: String)
}
