package mihon.data.extension.repository

import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.data.extension.service.MangaExtensionStoreService
import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.handlers.manga.MangaDatabaseHandler

class MangaExtensionStoreRepositoryImpl(
    private val service: MangaExtensionStoreService,
    private val handler: MangaDatabaseHandler,
) : MangaExtensionStoreRepository {
    override suspend fun insert(indexUrl: String): Result<Unit> {
        return service.fetch(indexUrl).mapCatching { upsert(it) }
    }

    override suspend fun insertFromPreference(indexUrl: String, name: String) {
        handler.await {
            extension_storeQueries.upsert(
                indexUrl = indexUrl,
                name = name,
                badgeLabel = name,
                signingKey = "NO_SIGNING_KEY",
                contactWebsite = indexUrl,
                contactDiscord = null,
                isLegacy = false,
            )
        }
    }

    override suspend fun refreshAll() {
        try {
            getAll().forEach { store ->
                service.fetch(store.indexUrl)
                    .mapCatching { upsert(it) }
                    .onFailure {
                        logcat(LogPriority.ERROR, it) {
                            "Failed to refresh extension store '${store.name} (${store.indexUrl})'"
                        }
                    }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    private suspend fun upsert(store: ExtensionStore) {
        handler.await {
            extension_storeQueries.upsert(
                indexUrl = store.indexUrl,
                name = store.name,
                badgeLabel = store.badgeLabel,
                signingKey = store.signingKey,
                contactWebsite = store.contact.website,
                contactDiscord = store.contact.discord,
                isLegacy = store.isLegacy,
            )
        }
    }

    override suspend fun fetchExtensions(): List<MangaExtension.Available> {
        return try {
            supervisorScope {
                getAll().map { store ->
                    async {
                        service.getExtensions(store).onFailure {
                            this@MangaExtensionStoreRepositoryImpl.logcat(LogPriority.ERROR, it) {
                                "Failed to fetch extensions for store '${store.name} (${store.indexUrl})'"
                            }
                        }
                    }
                }
                    .awaitAll()
                    .flatMap { it.getOrDefault(emptyList()) }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun getAll(): List<ExtensionStore> {
        return handler.awaitList { extension_storeQueries.getAll(::extensionStoreMapper) }
    }

    override fun getAllAsFlow(): Flow<List<ExtensionStore>> {
        return handler.subscribeToList { extension_storeQueries.getAll(::extensionStoreMapper) }
    }

    override fun getCountAsFlow(): Flow<Long> {
        return handler.subscribeToOne { extension_storeQueries.getCount() }
    }

    override suspend fun remove(indexUrl: String) {
        handler.await { extension_storeQueries.delete(indexUrl) }
    }

    private fun extensionStoreMapper(
        indexUrl: String,
        name: String,
        badgeLabel: String,
        signingKey: String,
        contactWebsite: String,
        contactDiscord: String?,
        isLegacy: Boolean,
    ): ExtensionStore = ExtensionStore(
        indexUrl = indexUrl,
        name = name,
        badgeLabel = badgeLabel,
        signingKey = signingKey,
        contact = ExtensionStore.Contact(
            website = contactWebsite,
            discord = contactDiscord,
        ),
        isLegacy = isLegacy,
    )
}
