package eu.kanade.tachiyomi.extension.anime.api

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import mihon.domain.extension.anime.interactor.UpdateAnimeExtensionStores
import mihon.domain.extension.anime.repository.AnimeExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@SingleIn(AppScope::class)
class AnimeExtensionApi(
    private val repository: AnimeExtensionStoreRepository,
    private val updateExtensionStores: UpdateAnimeExtensionStores,
    private val extensionUpdateNotifier: ExtensionUpdateNotifier,
) {

    @Suppress("UNCHECKED_CAST")
    suspend fun findExtensions(): List<AnimeExtension.Available> {
        return withIOContext { repository.fetchExtensions() as List<AnimeExtension.Available> }
    }

    /**
     * @param loadedExtensions Extensions already loaded by [eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager].
     * Only their versions are read, so there's nothing to gain from loading them a second time.
     */
    suspend fun checkForUpdates(loadedExtensions: List<AnimeExtension.Loaded>) {
        updateExtensionStores()

        val extensions = findExtensions()

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
    }
}
