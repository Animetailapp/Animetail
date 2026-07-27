package eu.kanade.domain.ui

import com.materialkolor.PaletteStyle
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    preferenceStore: PreferenceStore,
) {

    val themeMode: Preference<ThemeMode> = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    val appTheme: Preference<AppTheme> = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },
    )

    val colorTheme: Preference<Int> = preferenceStore.getInt("pref_color_theme", 0)

    val themeDarkAmoled: Preference<Boolean> = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)
    val imagesInDescription: Preference<Boolean> = preferenceStore.getBoolean("pref_render_images_description", true)

    val relativeTime: Preference<Boolean> = preferenceStore.getBoolean("relative_time_v2", true)

    val dateFormat: Preference<String> = preferenceStore.getString("app_date_format", "")

    val showEpisodeTimestamps: Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_episode_release_timestamp", true)

    val showChapterTimestamps: Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_chapter_release_timestamp", true)

    val tabletUiMode: Preference<TabletUiMode> = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    val startScreen: Preference<StartScreen> = preferenceStore.getEnum("start_screen", StartScreen.ANIME)

    val navStyle: Preference<NavStyle> = preferenceStore.getEnum("bottom_rail_nav_style", NavStyle.MOVE_HISTORY_TO_MORE)

    // SY -->
    val showNavUpdates: Preference<Boolean> = preferenceStore.getBoolean("pref_show_updates_button", true)
    val showNavHistory: Preference<Boolean> = preferenceStore.getBoolean("pref_show_history_button", true)
    val bottomBarLabels: Preference<Boolean> = preferenceStore.getBoolean("pref_show_bottom_bar_labels", true)
    val hideFeedTab: Preference<Boolean> = preferenceStore.getBoolean("hide_latest_tab", false)
    val feedTabInFront: Preference<Boolean> = preferenceStore.getBoolean("latest_tab_position", false)
    val expandFilters: Preference<Boolean> = preferenceStore.getBoolean("eh_expand_filters", false)
    val useNewSourceNavigation: Preference<Boolean> = preferenceStore.getBoolean("use_new_source_navigation", true)

    // SY <--
    // KMK -->
    val expandRelatedAnimes: Preference<Boolean> = preferenceStore.getBoolean("expand_related_animes", true)

    val relatedAnimesInOverflow: Preference<Boolean> = preferenceStore.getBoolean("related_animes_in_overflow", false)

    val showHomeOnRelatedAnimes: Preference<Boolean> = preferenceStore.getBoolean("show_home_on_related_animes", true)

    val showCast: Preference<Boolean> = preferenceStore.getBoolean("show_cast", true)

    // KMK - Cover-based theme -->
    val customThemeStyle: Preference<PaletteStyle> =
        preferenceStore.getEnum("pref_custom_theme_style_key", PaletteStyle.Fidelity)

    val themeCoverBased: Preference<Boolean> =
        preferenceStore.getBoolean("pref_theme_cover_based_key", true)

    val themeCoverBasedStyle: Preference<PaletteStyle> =
        preferenceStore.getEnum("pref_theme_cover_based_style_key", PaletteStyle.Vibrant)

    val usePanoramaCoverMangaInfo: Preference<Boolean> =
        preferenceStore.getBoolean("use_panorama_cover_manga_info", false)

    val topAlignCover: Preference<Boolean> =
        preferenceStore.getBoolean("top_align_cover", false)
    // KMK <--

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
