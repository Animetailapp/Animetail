package eu.kanade.tachiyomi.ui.player.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class AdvancedPlayerPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun mpvUserFiles() = preferenceStore.getBoolean("mpv_scripts", false)
    fun mpvConf() = preferenceStore.getString("pref_mpv_conf", "")
    fun mpvInput() = preferenceStore.getString("pref_mpv_input", "")

    /** Experimental: find openings/endings of downloaded episodes from the audio they share. */
    fun detectOpeningsEndings() = preferenceStore.getBoolean("pref_detect_openings_endings", false)

    // Non-preference

    fun playerStatisticsPage() = preferenceStore.getInt("pref_player_statistics_page", 0)
}
