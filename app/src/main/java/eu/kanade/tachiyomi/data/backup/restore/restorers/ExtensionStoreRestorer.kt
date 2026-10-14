package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import mihon.domain.extension.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extension.model.ExtensionStore

@Inject
class ExtensionStoreRestorer(
    private val mangaExtensionStoreRepository: MangaExtensionStoreRepository,
    private val animeExtensionStoreRepository: AnimeExtensionStoreRepository,
) {

    suspend fun restoreManga(
        backupStore: BackupExtensionStore,
    ) {
        mangaExtensionStoreRepository.upsert(
            ExtensionStore(
                indexUrl = backupStore.indexUrl,
                name = backupStore.name,
                badgeLabel = backupStore.badgeLabel ?: backupStore.name,
                signingKey = backupStore.signingKey,
                contact = ExtensionStore.Contact(
                    website = backupStore.contactWebsite,
                    discord = backupStore.contactDiscord,
                ),
                isLegacy = backupStore.isLegacy ?: true,
            ),
        )
    }

    suspend fun restoreAnime(
        backupStore: BackupExtensionStore,
    ) {
        animeExtensionStoreRepository.upsert(
            ExtensionStore(
                indexUrl = backupStore.indexUrl,
                name = backupStore.name,
                badgeLabel = backupStore.badgeLabel ?: backupStore.name,
                signingKey = backupStore.signingKey,
                contact = ExtensionStore.Contact(
                    website = backupStore.contactWebsite,
                    discord = backupStore.contactDiscord,
                ),
                isLegacy = backupStore.isLegacy ?: true,
            ),
        )
    }
}
