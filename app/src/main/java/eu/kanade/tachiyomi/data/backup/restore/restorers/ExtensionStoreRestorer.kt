package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import tachiyomi.data.Database
import tachiyomi.mi.data.AnimeDatabase

@Inject
class ExtensionStoreRestorer(
    private val database: Database,
    private val animeDatabase: AnimeDatabase,
) {

    suspend fun restoreManga(
        backupStore: BackupExtensionStore,
    ) {
        database.extension_storeQueries.upsert(
            indexUrl = backupStore.indexUrl,
            name = backupStore.name,
            badgeLabel = backupStore.badgeLabel ?: backupStore.name,
            signingKey = backupStore.signingKey,
            contactWebsite = backupStore.contactWebsite,
            contactDiscord = backupStore.contactDiscord,
            isLegacy = backupStore.isLegacy ?: true,
        )
    }

    suspend fun restoreAnime(
        backupStore: BackupExtensionStore,
    ) {
        animeDatabase.extension_storeQueries.upsert(
            indexUrl = backupStore.indexUrl,
            name = backupStore.name,
            badgeLabel = backupStore.badgeLabel ?: backupStore.name,
            signingKey = backupStore.signingKey,
            contactWebsite = backupStore.contactWebsite,
            contactDiscord = backupStore.contactDiscord,
            isLegacy = backupStore.isLegacy ?: true,
        )
    }
}
