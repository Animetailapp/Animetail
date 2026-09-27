package eu.kanade.tachiyomi.extension.manga.api

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import mihon.domain.extension.manga.interactor.UpdateMangaExtensionStores
import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@SingleIn(AppScope::class)
class MangaExtensionApi(
    private val repository: MangaExtensionStoreRepository,
    private val updateExtensionStores: UpdateMangaExtensionStores,
    private val extensionUpdateNotifier: ExtensionUpdateNotifier,
) {

    @Suppress("UNCHECKED_CAST")
    suspend fun findExtensions(): List<MangaExtension.Available> {
        return withIOContext { repository.fetchExtensions() as List<MangaExtension.Available> }
    }

    /**
     * @param installedExtensions Extensions already discovered by [eu.kanade.tachiyomi.extension.manga.MangaExtensionManager].
     * Only their versions are read, so there's nothing to gain from loading them a second time.
     */
    suspend fun checkForUpdates(installedExtensions: List<MangaExtension.Installed>) {
        updateExtensionStores()

        val extensions = findExtensions()

        val extensionsWithUpdate = installedExtensions.filter { it.findUpdate(extensions) != null }

        if (extensionsWithUpdate.isNotEmpty()) {
            extensionUpdateNotifier.promptUpdates(extensionsWithUpdate.map { it.name })
        }
    }
}
