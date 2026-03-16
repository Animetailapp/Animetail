package eu.kanade.domain.source.service

import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.library.model.LibraryDisplayMode

class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    // Common options

    fun sourceDisplayMode() = preferenceStore.getObject(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun enabledLanguages() = preferenceStore.getStringSet(
        "source_languages",
        LocaleHelper.getDefaultEnabledLanguages(),
    )

    fun showNsfwSource() = preferenceStore.getBoolean("show_nsfw_source", true)

    fun migrationSortingMode() = preferenceStore.getEnum(
        "pref_migration_sorting",
        SetMigrateSorting.Mode.ALPHABETICAL,
    )

    fun migrationSortingDirection() = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    fun animeExtensionRepos() = preferenceStore.getStringSet("anime_extension_repos", emptySet())

    fun mangaExtensionRepos() = preferenceStore.getStringSet("extension_repos", emptySet())

    // KMK -->
    fun hideInLibraryFeedItems() = preferenceStore.getBoolean("feed_hide_in_library_items", false)
    fun lastUsedSource() = preferenceStore.getLong(
        Preference.appStateKey("last_anime_catalogue_source"),
        -1,
    )
    // KMK <--

    fun trustedExtensions() = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    fun globalSearchFilterState() = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    // Mixture Sources

    fun disabledAnimeSources() = preferenceStore.getStringSet("hidden_anime_catalogues", emptySet())
    fun disabledMangaSources() = preferenceStore.getStringSet("hidden_catalogues", emptySet())

    fun incognitoAnimeExtensions() = preferenceStore.getStringSet("incognito_anime_extensions", emptySet())
    fun incognitoMangaExtensions() = preferenceStore.getStringSet("incognito_manga_extensions", emptySet())

    fun pinnedAnimeSources() = preferenceStore.getStringSet("pinned_anime_catalogues", emptySet())
    fun pinnedMangaSources() = preferenceStore.getStringSet("pinned_catalogues", emptySet())

    fun lastUsedAnimeSource() = preferenceStore.getLong(
        Preference.appStateKey("last_anime_catalogue_source"),
        -1,
    )
    fun lastUsedMangaSource() = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )

    fun animeExtensionUpdatesCount() = preferenceStore.getInt("animeext_updates_count", 0)
    fun mangaExtensionUpdatesCount() = preferenceStore.getInt("ext_updates_count", 0)

    fun hideInAnimeLibraryItems() = preferenceStore.getBoolean(
        "browse_hide_in_anime_library_items",
        false,
    )

    fun hideInMangaLibraryItems() = preferenceStore.getBoolean(
        "browse_hide_in_library_items",
        false,
    )

    // KMK -->
    fun disabledRepos() = preferenceStore.getStringSet("disabled_repos", emptySet())
    fun disabledSources() = preferenceStore.getStringSet("hidden_anime_catalogues", emptySet())

    fun pinnedSources() = preferenceStore.getStringSet("pinned_anime_catalogues", emptySet())
    // KMK <--

    // SY -->

    // fun enableSourceBlacklist() = preferenceStore.getBoolean("eh_enable_source_blacklist", true)

    // fun sourcesTabCategories() = preferenceStore.getStringSet("sources_tab_categories", mutableSetOf())

    // fun sourcesTabCategoriesFilter() = preferenceStore.getBoolean("sources_tab_categories_filter", false)

    // fun sourcesTabSourcesInCategories() = preferenceStore.getStringSet("sources_tab_source_categories", mutableSetOf())

    fun dataSaver() = preferenceStore.getEnum("data_saver", DataSaver.NONE)

    fun dataSaverIgnoreJpeg() = preferenceStore.getBoolean("ignore_jpeg", false)

    fun dataSaverIgnoreGif() = preferenceStore.getBoolean("ignore_gif", true)

    fun dataSaverImageQuality() = preferenceStore.getInt("data_saver_image_quality", 80)

    fun dataSaverImageFormatJpeg() = preferenceStore.getBoolean(
        "data_saver_image_format_jpeg",
        false,
    )

    fun dataSaverServer() = preferenceStore.getString("data_saver_server", "")

    fun dataSaverColorBW() = preferenceStore.getBoolean("data_saver_color_bw", false)

    fun dataSaverExcludedSources() = preferenceStore.getStringSet("data_saver_excluded", emptySet())

    fun dataSaverDownloader() = preferenceStore.getBoolean("data_saver_downloader", true)

    enum class DataSaver {
        NONE,
        BANDWIDTH_HERO,
        WSRV_NL,
        RESMUSH_IT,
    }

    // SY <--

    // TLMR -->
    fun enableIntegratedHentaiFeatures() = preferenceStore.getBoolean("enable_integrated_hentai_features", true)

    /** Stores the ipb_member_id cookie for E-Hentai/ExHentai authentication. */
    fun ehMemberId() = preferenceStore.getString("eh_ipb_member_id", "")

    /** Stores the igneous cookie required for ExHentai access. */
    fun ehIgneous() = preferenceStore.getString("eh_igneous", "")

    // ── E-Hentai source preferences ──────────────────────────────────────────

    /** Use ExHentai (requires login) instead of E-Hentai. */
    fun ehUseExHentai() = preferenceStore.getBoolean("eh_use_exhentai", false)

    /**
     * Default excluded category bitmask for browsing (0 = show all).
     * See [eu.kanade.tachiyomi.source.manga.builtin.ehentai.EHentaiConstants] for bit values.
     */
    fun ehDefaultCategories() = preferenceStore.getInt("eh_default_categories", 0)

    /** Tags to watch — galleries containing these tags will be highlighted. */
    fun ehWatchedTags() = preferenceStore.getStringSet("eh_watched_tags", emptySet())

    /** Tags to ignore — galleries containing these tags will be hidden from browse results. */
    fun ehIgnoredTags() = preferenceStore.getStringSet("eh_ignored_tags", emptySet())

    /** Gallery title display preference: "english" (default) or "japanese". */
    fun ehTitleDisplayMode() = preferenceStore.getString("eh_title_display_mode", "english")

    /** Whether to sync E-Hentai favorites to the app library on startup. */
    fun ehFavoritesSync() = preferenceStore.getBoolean("eh_favorites_sync", false)

    /** Whether the periodic gallery checker is enabled. */
    fun ehGalleryChecker() = preferenceStore.getBoolean("eh_gallery_checker", false)

    /** Interval in hours between gallery checker runs. */
    fun ehGalleryCheckerInterval() = preferenceStore.getInt("eh_gallery_checker_interval", 12)

    /** Use original (full-resolution) images instead of resampled ones. */
    fun ehOriginalImages() = preferenceStore.getBoolean("eh_original_images", false)

    /** Enable improved browsing mode (extended gallery info in browse list). */
    fun ehImprovedBrowsing() = preferenceStore.getBoolean("eh_improved_browsing", false)

    /**
     * E-Hentai settings profile number ("0"–"9", "0" = default).
     * Sent as the `sp` cookie — controls display settings saved on the EH server.
     * Mirrors Komikku's ehSettingsProfile / exhSettingsProfile.
     */
    fun ehSettingsProfile() = preferenceStore.getString("eh_settings_profile", "0")

    /**
     * EH/ExH settings key cookie (`sk`).
     * Optional — only sent when non-blank. Mirrors Komikku's exhSettingsKey.
     */
    fun exhSettingsKey() = preferenceStore.getString("eh_settings_key", "")

    /**
     * EH/ExH session cookie (`s`).
     * Optional — only sent when non-blank. Mirrors Komikku's exhSessionCookie.
     */
    fun exhSessionCookie() = preferenceStore.getString("eh_session_cookie", "")

    /**
     * Hath-at-Home perks cookie (`hath_perks`).
     * Optional — only sent when non-blank. Mirrors Komikku's exhHathPerksCookies.
     */
    fun exhHathPerksCookies() = preferenceStore.getString("eh_hath_perks_cookie", "")
    // TLMR <--

    // KMK -->
    fun relatedAnimes() = preferenceStore.getBoolean("related_animes", true)
    // KMK <--
}
