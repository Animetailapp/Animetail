package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.backupExtensionStoreMapper
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStores
import mihon.domain.extension.manga.interactor.GetMangaExtensionStores
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionStoresBackupCreator(
    private val getAnimeExtensionStores: GetAnimeExtensionStores = Injekt.get<GetAnimeExtensionStores>(),
) {

    suspend operator fun invoke(): List<BackupExtensionStore> {
        return getAnimeExtensionStores.await()
            .map(backupExtensionStoreMapper)
    }
}

class MangaExtensionStoresBackupCreator(
    private val getMangaExtensionStores: GetMangaExtensionStores = Injekt.get<GetMangaExtensionStores>(),
) {

    suspend operator fun invoke(): List<BackupExtensionStore> {
        return getMangaExtensionStores.await()
            .map(backupExtensionStoreMapper)
    }
}
