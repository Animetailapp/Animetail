package mihon.data.extension.service

import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
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
import okio.buffer
import okio.gzip
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

class AnimeExtensionStoreService(
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
            val response = network.client.newCall(GET(indexUrl)).awaitSuccess()
            val store = response.body.source().decompressIfGzipped().use { source ->
                source.stripBom()
                val networkStore = when (source.peek().readByte()) {
                    // "[..."
                    0x5B.toByte() -> run {
                        if (!indexUrl.endsWith("/index.min.json")) {
                            throw IllegalArgumentException("Provided legacy store url is not valid")
                        }
                        updatedIndexUrl = indexUrl.replace("/index.min.json", "/repo.json")
                        network.client.newCall(
                            GET(updatedIndexUrl),
                        ).awaitSuccess().body.source().decompressIfGzipped().use {
                            it.stripBom()
                            json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(it)
                        }
                    }

                    // "{..."
                    0x7B.toByte() -> try {
                        json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(source.peek())
                    } catch (_: Exception) {
                        json.decodeFromBufferedSource<NetworkExtensionStore>(source)
                    }

                    else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                }

                if (networkStore is NetworkLegacyExtensionRepo) {
                    val indexV2 = networkStore.indexV2
                    if (indexV2 != null) {
                        return fetch(indexV2, forceV2 = true)
                    }
                }

                networkStore.toExtensionStore(updatedIndexUrl)
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

    suspend fun getExtensions(store: ExtensionStore): Result<List<AnimeExtension.Available>> {
        return try {
            val extensions = if (store.extensionListUrl != null) {
                val response = network.client.newCall(GET(store.extensionListUrl!!)).awaitSuccess()
                response.body.source().decompressIfGzipped().use { source ->
                    source.stripBom()
                    when (source.peek().readByte()) {
                        // "{..."
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkExtensionStore.ExtensionList>(source)

                        else -> protoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(
                            source.readByteArray(),
                        )
                    }
                        .let { toAvailableExtensions(it, store) }
                }
            } else if (!store.isLegacy) {
                val response = network.client.newCall(GET(store.indexUrl)).awaitSuccess()
                response.body.source().decompressIfGzipped().use { source ->
                    source.stripBom()
                    val netStore = when (source.peek().readByte()) {
                        // "{..."
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkExtensionStore>(source)

                        else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                    }
                    val extensionList = netStore.extensionList
                    if (extensionList != null) {
                        toAvailableExtensions(extensionList, store)
                    } else {
                        emptyList()
                    }
                }
            } else {
                val storeBaseUrl = store.indexUrl.removeSuffix("/repo.json")
                val response = network.client.newCall(GET("$storeBaseUrl/index.min.json")).awaitSuccess()
                response.body.source().use { source ->
                    source.stripBom()
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
        extensionList: NetworkExtensionStore.ExtensionList,
        store: ExtensionStore,
    ): List<AnimeExtension.Available> {
        return extensionList.extensions.map { extension ->
            val lang = extension.sources.map { it.language }.toSet()
            AnimeExtension.Available(
                name = extension.name,
                pkgName = extension.packageName,
                apkUrl = extension.resources.apkUrl,
                iconUrl = extension.resources.iconUrl,
                libVersion = extension.extensionLib.toDouble(),
                versionCode = extension.versionCode,
                versionName = extension.versionName,
                lang = if (lang.size == 1) lang.first() else "all",
                isNsfw = extension.contentWarning >= NetworkExtensionStore.ContentWarning.MIXED,
                isTorrent = false,
                sources = extension.sources.map { source ->
                    AnimeExtension.Available.AnimeSource(
                        id = source.id,
                        name = source.name,
                        lang = source.language,
                        baseUrl = source.homeUrl,
                    )
                },
                store = store,
                signatureHash = "NO_SIGNING_KEY",
                repoName = store.name,
            )
        }
    }

    private fun toAvailableExtension(
        netExt: NetworkLegacyExtension,
        store: ExtensionStore,
        storeBaseUrl: String,
    ): AnimeExtension.Available {
        return AnimeExtension.Available(
            name = netExt.name.substringAfter("Tachiyomi: "),
            pkgName = netExt.pkg,
            apkUrl = "$storeBaseUrl/apk/${netExt.apk}",
            iconUrl = "$storeBaseUrl/icon/${netExt.pkg}.png",
            libVersion = netExt.version.substringBeforeLast('.').toDouble(),
            versionCode = netExt.code,
            versionName = netExt.version,
            lang = netExt.lang,
            isNsfw = netExt.nsfw == 1,
            isTorrent = false,
            sources = run {
                val sources = netExt.sources
                if (sources.isNullOrEmpty()) {
                    listOf(
                        AnimeExtension.Available.AnimeSource(
                            id = 0,
                            name = netExt.name,
                            lang = netExt.lang,
                            baseUrl = "",
                        ),
                    )
                } else {
                    sources.map { source ->
                        AnimeExtension.Available.AnimeSource(
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

private fun okio.BufferedSource.decompressIfGzipped(): okio.BufferedSource {
    val isGzip = peek().use { peeked ->
        try {
            peeked.readShort().toInt() == 0x1f8b
        } catch (_: Exception) {
            false
        }
    }

    return if (isGzip) gzip().buffer() else this
}

private fun okio.BufferedSource.stripBom() {
    val bom = okio.ByteString.of(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    if (rangeEquals(0, bom)) {
        skip(bom.size.toLong())
    }
}
