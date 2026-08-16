package mihon.core.migration.migrations

import android.content.Context
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

@Inject
@ContributesIntoSet(AppScope::class)
class RemoveReaderTapMigration(
    private val context: Context,
    private val readerPreferences: ReaderPreferences,
) : Migration {
    override val version = 77f

    // Remove reader tapping option in favor of disabled nav layouts
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val oldReaderTap = prefs.getBoolean("reader_tap", false)
        if (!oldReaderTap) {
            readerPreferences.navigationModePager.set(5)
            readerPreferences.navigationModeWebtoon.set(5)
        }

        return true
    }
}
