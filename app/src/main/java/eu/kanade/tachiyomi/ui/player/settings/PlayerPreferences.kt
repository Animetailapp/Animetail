package eu.kanade.tachiyomi.ui.player.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.ui.player.PlayerOrientation
import eu.kanade.tachiyomi.ui.player.VideoAspect
import eu.kanade.tachiyomi.ui.player.utils.ChapterSkipMode
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

@Inject
@SingleIn(AppScope::class)
class PlayerPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun preserveWatchingPosition() = preferenceStore.getBoolean(
        "pref_preserve_watching_position",
        false,
    )
    fun progressPreference() = preferenceStore.getFloat("pref_progress_preference", 0.85F)
    fun defaultPlayerOrientationType() = preferenceStore.getEnum(
        "pref_default_player_orientation_type_key",
        PlayerOrientation.SensorLandscape,
    )

    // Controls

    fun allowGestures() = preferenceStore.getBoolean("pref_allow_gestures_in_panels", false)
    fun showLoadingCircle() = preferenceStore.getBoolean("pref_show_loading", true)
    fun showCurrentChapter() = preferenceStore.getBoolean("pref_show_current_chapter", true)
    fun rememberPlayerBrightness() = preferenceStore.getBoolean("pref_remember_brightness", false)
    fun playerBrightnessValue() = preferenceStore.getFloat("player_brightness_value", -1.0F)
    fun rememberPlayerVolume() = preferenceStore.getBoolean("pref_remember_volume", false)
    fun playerVolumeValue() = preferenceStore.getFloat("player_volume_value", -1.0F)

    fun showDoubleTapOvals() = preferenceStore.getBoolean("show_double_tap_ovals", true)
    fun showSeekIcon() = preferenceStore.getBoolean("show_seek_icons", true)
    fun showSeekTimeWhileSeeking() = preferenceStore.getBoolean("show_seek_time_while_seeking", true)

    // Hoster

    fun showFailedHosters() = preferenceStore.getBoolean("pref_show_failed_hosters", false)
    fun showEmptyHosters() = preferenceStore.getBoolean("pref_show_empty_hosters", false)
    fun hosterPriority() = preferenceStore.getString("pref_hoster_priority", "")
    fun disabledHosters() = preferenceStore.getString("pref_disabled_hosters", "")
    fun hosterCacheMinutes() = preferenceStore.getInt("pref_hoster_cache_minutes", 120)
    fun hosterListCacheMinutes() = preferenceStore.getInt("pref_hoster_list_cache_minutes", 1440)

    // Display

    fun playerFullscreen() = preferenceStore.getBoolean("player_fullscreen", true)
    fun hideControls() = preferenceStore.getBoolean("player_hide_controls", false)
    fun displayVolPer() = preferenceStore.getBoolean("pref_display_vol_as_per", true)
    fun showSystemStatusBar() = preferenceStore.getBoolean("pref_show_system_status_bar", false)
    fun reduceMotion() = preferenceStore.getBoolean("pref_reduce_motion", false)
    fun playerTimeToDisappear() = preferenceStore.getInt("pref_player_time_to_disappear", 4000)
    fun panelOpacity() = preferenceStore.getInt("pref_panel_opacity", 60)

    // Skip intro button

    fun openingSkipMode() = preferenceStore.getEnum("pref_opening_skip_mode", legacyChapterSkipMode())
    fun endingSkipMode() = preferenceStore.getEnum("pref_ending_skip_mode", legacyChapterSkipMode())

    /**
     * Before the opening and ending modes, one set of switches applied to every typed chapter;
     * the modes default to what those switches did until they are set.
     */
    private fun legacyChapterSkipMode(): ChapterSkipMode = when {
        !preferenceStore.getBoolean("pref_enable_skip_intro", true).get() -> ChapterSkipMode.Off
        preferenceStore.getBoolean("pref_enable_netflixStyle_aniskip", false).get() -> ChapterSkipMode.Countdown
        preferenceStore.getBoolean("pref_enable_auto_skip_ani_skip", false).get() -> ChapterSkipMode.AutoSkip
        else -> ChapterSkipMode.Button
    }
    fun waitingTimeIntroSkip() = preferenceStore.getInt("pref_waiting_time_aniskip", 5)
    fun aniSkipEnabled() = preferenceStore.getBoolean("pref_enable_ani_skip", false)
    fun disableAniSkipOnChapters() = preferenceStore.getBoolean("pref_disabled_ani_skip_chapters", true)

    // PiP

    fun enablePip() = preferenceStore.getBoolean("pref_enable_pip", true)
    fun pipEpisodeToasts() = preferenceStore.getBoolean("pref_pip_episode_toasts", true)
    fun pipOnExit() = preferenceStore.getBoolean("pref_pip_on_exit", false)
    fun pipReplaceWithPrevious() = preferenceStore.getBoolean("pip_replace_with_previous", false)

    // External player

    fun enableCast() = preferenceStore.getBoolean("pref_enable_cast", false)

    fun alwaysUseExternalPlayer() = preferenceStore.getBoolean(
        "pref_always_use_external_player",
        false,
    )
    fun externalPlayerPreference() = preferenceStore.getString("external_player_preference", "")

    // Non-preferences

    fun playerSpeed() = preferenceStore.getFloat("pref_player_speed", 1f)
    fun speedPresets() = preferenceStore.getStringSet(
        "default_speed_presets",
        setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0", "3.5", "4.0"),
    )
    fun invertDuration() = preferenceStore.getBoolean("invert_duration", false)
    fun aspectState() = preferenceStore.getEnum("pref_player_aspect_state", VideoAspect.Fit)

    // Old

    fun autoplayEnabled() = preferenceStore.getBoolean("pref_auto_play_enabled", false)

    fun switchOnFailure() = preferenceStore.getBoolean("pref_switch_on_failure", true)
}
