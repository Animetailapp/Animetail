package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import mihon.domain.extension.manga.interactor.UpdateMangaExtensionStores
import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

internal class MangaExtensionApi {

    private val repository: MangaExtensionStoreRepository by injectLazy()
    private val updateExtensionStores: UpdateMangaExtensionStores by injectLazy()

    @Suppress("UNCHECKED_CAST")
    suspend fun findExtensions(): List<MangaExtension.Available> {
        return withIOContext { repository.fetchExtensions() as List<MangaExtension.Available> }
    }

    suspend fun checkForUpdates(context: Context) {
        updateExtensionStores()

        val extensions = findExtensions()

        val installedExtensions = MangaExtensionLoader.loadMangaExtensions(context)
            .filterIsInstance<MangaLoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<MangaExtension.Installed>()
        for (installedExt in installedExtensions) {
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
            ExtensionUpdateNotifier(context).promptUpdates(extensionsWithUpdate.map { it.name })
        }
    }
}
