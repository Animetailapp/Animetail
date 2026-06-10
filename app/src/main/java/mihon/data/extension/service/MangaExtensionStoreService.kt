package mihon.data.extension.service

import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.data.extension.model.NetworkExtensionStore
import mihon.data.extension.model.NetworkLegacyExtension
import mihon.data.extension.model.NetworkLegacyExtensionRepo
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

class MangaExtensionStoreService(
    private val network: NetworkHelper,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) {
    suspend fun fetch(indexUrl: String): Result<ExtensionStore> {
        return fetch(indexUrl, forceV2 = false)
    }

    private suspend fun fetch(indexUrl: String, forceV2: Boolean): Result<ExtensionStore> {
        var updatedIndexUrl: String = indexUrl
        return try {
            val store = network.client.newCall(GET(indexUrl)).awaitSuccess().body.source().use { source ->
                try {
                    protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.peek().readByteArray())
                } catch (e: IllegalArgumentException) {
                    logcat(LogPriority.ERROR, e) {
                        "Failed to add extension store '$updatedIndexUrl'"
                    }
                    try {
                        json.decodeFromBufferedSource<NetworkExtensionStore>(source.peek())
                    } catch (e: IllegalArgumentException) {
                        if (forceV2) throw e
                        logcat(LogPriority.ERROR, e) {
                            "Failed to add extension store '$updatedIndexUrl'"
                        }
                        val legacyIndex = try {
                            json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(source.peek())
                        } catch (e: IllegalArgumentException) {
                            if (!indexUrl.endsWith("/index.min.json")) {
                                throw e
                            }
                            logcat(LogPriority.ERROR, e) {
                                "Failed to add extension store '$updatedIndexUrl'"
                            }
                            updatedIndexUrl = indexUrl.replace("/index.min.json", "/repo.json")
                            network.client.newCall(GET(updatedIndexUrl)).awaitSuccess().body.source().use {
                                json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(it)
                            }
                        }

                        if (legacyIndex.indexV2 != null) {
                            return fetch(legacyIndex.indexV2, forceV2 = true)
                        } else {
                            legacyIndex
                        }
                    }
                }
                    .toExtensionStore(updatedIndexUrl)
            }
            Result.success(store)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Failed to add extension store '$updatedIndexUrl'"
            }
            Result.failure(e)
        }
    }

    suspend fun getExtensions(store: ExtensionStore): Result<List<MangaExtension.Available>> {
        return try {
            val extensions = if (!store.isLegacy) {
                val response = network.client.newCall(GET(store.indexUrl)).awaitSuccess()
                response.body.source().use { source ->
                    try {
                        protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.peek().readByteArray())
                            .let { toAvailableExtensions(it, store) }
                    } catch (_: IllegalArgumentException) {
                        json.decodeFromBufferedSource<NetworkExtensionStore>(source.peek())
                            .let { toAvailableExtensions(it, store) }
                    }
                }
            } else {
                val storeBaseUrl = store.indexUrl.removeSuffix("/repo.json")
                val response = network.client.newCall(GET("$storeBaseUrl/index.min.json")).awaitSuccess()
                response.body.source().use { source ->
                    json.decodeFromBufferedSource<List<NetworkLegacyExtension>>(source)
                        .map { toAvailableExtension(it, store, storeBaseUrl) }
                }
            }
            Result.success(extensions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun toAvailableExtensions(
        netStore: NetworkExtensionStore,
        store: ExtensionStore,
    ): List<MangaExtension.Available> {
        return netStore.extensions.map { extension ->
            val lang = extension.sources.map { it.language }.toSet()
            MangaExtension.Available(
                name = extension.name,
                pkgName = extension.packageName,
                apkUrl = extension.resources.apkUrl,
                iconUrl = extension.resources.iconUrl,
                libVersion = extension.extensionLib.toDouble(),
                versionCode = extension.versionCode,
                versionName = extension.versionName,
                lang = if (lang.size == 1) lang.first() else "all",
                isNsfw =
                extension.sources.maxOfOrNull { it.contentRating } ==
                    NetworkExtensionStore.ContentRating.PORNOGRAPHIC,
                sources = extension.sources.map { source ->
                    MangaExtension.Available.MangaSource(
                        id = source.id,
                        name = source.name,
                        lang = source.language,
                        baseUrl = source.homeUrl,
                    )
                },
                store = store,
                signatureHash = "NO_SIGNING_KEY", // Will be filled/verified on load/trust
                repoName = store.name,
            )
        }
    }

    private fun toAvailableExtension(
        netExt: NetworkLegacyExtension,
        store: ExtensionStore,
        storeBaseUrl: String,
    ): MangaExtension.Available {
        return MangaExtension.Available(
            name = netExt.name.substringAfter("Tachiyomi: "),
            pkgName = netExt.pkg,
            apkUrl = "$storeBaseUrl/apk/${netExt.apk}",
            iconUrl = "$storeBaseUrl/icon/${netExt.pkg}.png",
            libVersion = netExt.version.substringBeforeLast('.').toDouble(),
            versionCode = netExt.code,
            versionName = netExt.version,
            lang = netExt.lang,
            isNsfw = netExt.nsfw == 1,
            sources = run {
                val sources = netExt.sources
                if (sources.isNullOrEmpty()) {
                    listOf(
                        MangaExtension.Available.MangaSource(
                            id = 0,
                            name = netExt.name,
                            lang = netExt.lang,
                            baseUrl = "",
                        ),
                    )
                } else {
                    sources.map { source ->
                        MangaExtension.Available.MangaSource(
                            id = source.id,
                            name = source.name,
                            lang = source.lang,
                            baseUrl = source.baseUrl,
                        )
                    }
                }
            },
            store = store,
            signatureHash = "NO_SIGNING_KEY",
            repoName = store.name,
        )
    }
}
