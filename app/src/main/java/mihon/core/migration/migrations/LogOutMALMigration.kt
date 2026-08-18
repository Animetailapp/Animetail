package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.track.TrackerManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

@Inject
@ContributesIntoSet(AppScope::class)
class LogOutMALMigration(
    private val trackerManager: TrackerManager,
) : Migration {
    override val version = 121f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        if (trackerManager.myAnimeList.isLoggedIn) {
            trackerManager.myAnimeList.logout()
        }

        return true
    }
}
