package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import tachiyomi.data.Database
import tachiyomi.mi.data.AnimeDatabase
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionStoreRestorer(
    private val database: Database = Injekt.get(),
    private val animeDatabase: AnimeDatabase = Injekt.get(),
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
