package mihon.core.migration.migrations

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
class ForceSubtitleAssOverrideMigration(
    private val preferenceStore: PreferenceStore,
) : Migration {
    override val version = 132f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preference = preferenceStore.getEnum(
            "pref_override_subtitles_ass_enum",
            SubtitleAssOverride.Force,
        )

        if (preference.get() == SubtitleAssOverride.No) {
            preference.set(SubtitleAssOverride.Force)
        }

        return true
    }
}
