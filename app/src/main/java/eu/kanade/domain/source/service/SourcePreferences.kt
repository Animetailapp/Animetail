package eu.kanade.domain.source.service

import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getObjectFromString
import tachiyomi.domain.library.model.LibraryDisplayMode

class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    // Common options

    val sourceDisplayMode: Preference<LibraryDisplayMode> = preferenceStore.getObjectFromString(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    val enabledLanguages: Preference<Set<String>> = preferenceStore.getStringSet(
        "source_languages",
        LocaleHelper.getDefaultEnabledLanguages(),
    )

    val showNsfwSource: Preference<Boolean> = preferenceStore.getBoolean("show_nsfw_source", true)

    val migrationSortingMode: Preference<SetMigrateSorting.Mode> = preferenceStore.getEnum(
        "pref_migration_sorting",
        SetMigrateSorting.Mode.ALPHABETICAL,
    )

    val migrationSortingDirection: Preference<SetMigrateSorting.Direction> = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    val extensionRepos: Preference<Set<String>> = preferenceStore.getStringSet("extension_repos", emptySet())

    val extensionUpdatesCount: Preference<Int> = preferenceStore.getInt("ext_updates_count", 0)

    val hideInLibraryItems: Preference<Boolean> = preferenceStore.getBoolean("browse_hide_in_library_items", false)

    val lastUsedSource: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )

    val trustedExtensions: Preference<Set<String>> = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    val globalSearchFilterState: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    // Mixture sources

    val animeExtensionRepos = preferenceStore.getStringSet("anime_extension_repos", emptySet())
    val disabledAnimeSources = preferenceStore.getStringSet("hidden_anime_catalogues", emptySet())
    val disabledMangaSources = preferenceStore.getStringSet("hidden_catalogues", emptySet())
    val disabledSources: Preference<Set<String>> = disabledMangaSources

    val incognitoAnimeExtensions = preferenceStore.getStringSet("incognito_anime_extensions", emptySet())
    val incognitoMangaExtensions = preferenceStore.getStringSet("incognito_manga_extensions", emptySet())
    val incognitoExtensions: Preference<Set<String>> = incognitoMangaExtensions

    val pinnedAnimeSources = preferenceStore.getStringSet("pinned_anime_catalogues", emptySet())
    val pinnedMangaSources = preferenceStore.getStringSet("pinned_catalogues", emptySet())
    val pinnedSources: Preference<Set<String>> = pinnedMangaSources

    val lastUsedAnimeSource = preferenceStore.getLong(
        Preference.appStateKey("last_anime_catalogue_source"),
        -1,
    )
    val animeExtensionUpdatesCount = preferenceStore.getInt("animeext_updates_count", 0)
    val hideInAnimeLibraryItems = preferenceStore.getBoolean(
        "browse_hide_in_anime_library_items",
        false,
    )

    // KMK -->
    val hideInLibraryFeedItems = preferenceStore.getBoolean("feed_hide_in_library_items", false)

    val disabledRepos = preferenceStore.getStringSet("disabled_repos", emptySet())
    // KMK <--

    // SY -->
    val dataSaver = preferenceStore.getEnum("data_saver", DataSaver.NONE)

    val dataSaverIgnoreJpeg = preferenceStore.getBoolean("ignore_jpeg", false)

    val dataSaverIgnoreGif = preferenceStore.getBoolean("ignore_gif", true)

    val dataSaverImageQuality = preferenceStore.getInt("data_saver_image_quality", 80)

    val dataSaverImageFormatJpeg = preferenceStore.getBoolean(
        "data_saver_image_format_jpeg",
        false,
    )

    val dataSaverServer = preferenceStore.getString("data_saver_server", "")

    val dataSaverColorBW = preferenceStore.getBoolean("data_saver_color_bw", false)

    val dataSaverExcludedSources = preferenceStore.getStringSet("data_saver_excluded", emptySet())

    val dataSaverDownloader = preferenceStore.getBoolean("data_saver_downloader", true)

    enum class DataSaver {
        NONE,
        BANDWIDTH_HERO,
        WSRV_NL,
        RESMUSH_IT,
    }
    // SY <--

    // KMK -->
    val relatedAnimes = preferenceStore.getBoolean("related_animes", true)
    // KMK <--
}
