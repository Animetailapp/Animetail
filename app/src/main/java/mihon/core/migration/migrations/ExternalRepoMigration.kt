package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.getAndSet

@Inject
@ContributesIntoSet(AppScope::class)
class ExternalRepoMigration(
    private val sourcePreferences: SourcePreferences,
) : Migration {
    override val version = 114f

    // Clean up external repos
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        sourcePreferences.extensionRepos.getAndSet {
            it.map { repo -> "https://raw.githubusercontent.com/$repo/repo" }.toSet()
        }

        return true
    }
}
