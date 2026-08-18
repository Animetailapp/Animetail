package mihon.core.migration.migrations

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.ui.player.settings.SubtitleAssOverride
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

@Inject
@ContributesIntoSet(AppScope::class)
class SubtitleAssEnumMigration(
    private val context: Context,
    private val preferenceStore: PreferenceStore,
) : Migration {
    override val version = 131f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val overrideAss = preferenceStore.getBoolean("pref_override_subtitles_ass", false).get()
        prefs.edit {
            remove("pref_override_subtitles_ass")
            preferenceStore.getEnum("pref_override_subtitles_ass_enum", SubtitleAssOverride.No).set(
                if (overrideAss) SubtitleAssOverride.Force else SubtitleAssOverride.No,
            )
        }

        return true
    }
}
