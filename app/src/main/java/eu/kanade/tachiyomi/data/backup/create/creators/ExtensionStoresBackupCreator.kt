package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.backupExtensionStoreMapper
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStores
import mihon.domain.extension.manga.interactor.GetMangaExtensionStores

@Inject
class AnimeExtensionStoresBackupCreator(
    private val getAnimeExtensionStores: GetAnimeExtensionStores,
) {

    suspend operator fun invoke(): List<BackupExtensionStore> {
        return getAnimeExtensionStores.await()
            .map(backupExtensionStoreMapper)
    }
}

@Inject
class MangaExtensionStoresBackupCreator(
    private val getMangaExtensionStores: GetMangaExtensionStores,
) {

    suspend operator fun invoke(): List<BackupExtensionStore> {
        return getMangaExtensionStores.await()
            .map(backupExtensionStoreMapper)
    }
}
