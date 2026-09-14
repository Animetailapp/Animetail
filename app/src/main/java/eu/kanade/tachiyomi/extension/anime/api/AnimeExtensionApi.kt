package eu.kanade.tachiyomi.extension.anime.api

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader
import kotlinx.serialization.Serializable
import mihon.domain.extension.anime.interactor.UpdateAnimeExtensionStores
import mihon.domain.extension.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extension.model.ContentWarning
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import java.time.Instant
import kotlin.time.Duration.Companion.days

@Inject
@SingleIn(AppScope::class)
class AnimeExtensionApi(
    private val repository: AnimeExtensionStoreRepository,
    private val preferenceStore: PreferenceStore,
    private val updateExtensionStores: UpdateAnimeExtensionStores,
    private val extensionManager: Lazy<AnimeExtensionManager>,
    private val extensionUpdateNotifier: ExtensionUpdateNotifier,
) {

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun findExtensions(): List<AnimeExtension.Available> {
        return withIOContext { repository.fetchExtensions() as List<AnimeExtension.Available> }
    }

    /**
     * @param loadedExtensions Extensions already loaded by [eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager].
     * Only their versions are read, so there's nothing to gain from loading them a second time.
     */
    suspend fun checkForUpdates(
        loadedExtensions: List<AnimeExtension.Loaded>,
        fromAvailableExtensionList: Boolean = false,
    ): List<AnimeExtension.Loaded>? {
        // Limit checks to once a day at most
        if (fromAvailableExtensionList &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        updateExtensionStores()

        val extensions = if (fromAvailableExtensionList) {
            extensionManager.value.availableExtensionsFlow.value
        } else {
            findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
        }

        val extensionsWithUpdate = mutableListOf<AnimeExtension.Loaded>()
        for (installedExt in loadedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            extensionUpdateNotifier.promptUpdates(
                names = extensionsWithUpdate.map { it.name },
                anime = true,
            )
        }

        return extensionsWithUpdate
    }
    private fun List<AnimeExtensionJsonObject>.toExtensions(repoUrl: String): List<AnimeExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= AnimeExtensionLoader.LIB_VERSION_MIN && libVersion <= AnimeExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                AnimeExtension.Available(
                    name = it.name.substringAfter("Aniyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    contentWarning = if (it.nsfw == 1) ContentWarning.NSFW else ContentWarning.SAFE,
                    isTorrent = it.torrent == 1,
                    sources = it.sources?.map(extensionAnimeSourceMapper).orEmpty(),
                    apkUrl = "$repoUrl/apk/${it.apk}",
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    store = mihon.domain.extension.model.ExtensionStore(
                        indexUrl = repoUrl,
                        name = "Aniyomi",
                        badgeLabel = "Aniyomi",
                        signingKey = "NO_SIGNING_KEY",
                        contact = mihon.domain.extension.model.ExtensionStore.Contact(website = "", discord = null),
                        isLegacy = true,
                    ),
                    signatureHash = "NO_SIGNING_KEY",
                    repoName = "Aniyomi",
                )
            }
    }

    fun getApkUrl(extension: AnimeExtension.Available): String {
        return extension.apkUrl
    }

    private fun AnimeExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDouble()
    }
}

@Serializable
private data class AnimeExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val torrent: Int = 0,
    val sources: List<AnimeExtensionSourceJsonObject>?,
)

@Serializable
private data class AnimeExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private val extensionAnimeSourceMapper: (AnimeExtensionSourceJsonObject) -> AnimeExtension.Available.AnimeSource = {
    AnimeExtension.Available.AnimeSource(
        id = it.id,
        lang = it.lang,
        name = it.name,
        baseUrl = it.baseUrl,
    )
}
