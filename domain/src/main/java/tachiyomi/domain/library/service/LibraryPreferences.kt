package tachiyomi.domain.library.service

import aniyomi.domain.anime.SeasonDisplayMode
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.anime.model.AnimeGroupLibraryMode
import tachiyomi.domain.library.anime.model.AnimeLibraryGroup
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.manga.model.MangaGroupLibraryMode
import tachiyomi.domain.library.manga.model.MangaLibraryGroup
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode

class LibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val displayMode: Preference<LibraryDisplayMode> = preferenceStore.getObject(
        "pref_display_mode_library",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    val mangaSortingMode = preferenceStore.getObject(
        "library_sorting_mode",
        MangaLibrarySort.default,
        MangaLibrarySort.Serializer::serialize,
        MangaLibrarySort.Serializer::deserialize,
    )

    val sortingMode = mangaSortingMode

    val animeSortingMode = preferenceStore.getObject(
        "animelib_sorting_mode",
        AnimeLibrarySort.default,
        AnimeLibrarySort.Serializer::serialize,
        AnimeLibrarySort.Serializer::deserialize,
    )

    val randomSortSeed: Preference<Int> = preferenceStore.getInt("library_random_manga_sort_seed", 0)

    val portraitColumns: Preference<Int> = preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    val landscapeColumns: Preference<Int> = preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    val lastUpdatedTimestamp: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("library_update_last_timestamp"),
        0L,
    )
    val autoUpdateInterval: Preference<Int> = preferenceStore.getInt("pref_library_update_interval_key", 0)

    val autoUpdateDeviceRestrictions: Preference<Set<String>> = preferenceStore.getStringSet(
        "library_update_restriction",
        setOf(
            DEVICE_ONLY_ON_WIFI,
        ),
    )

    val autoUpdateMangaRestrictions: Preference<Set<String>> = preferenceStore.getStringSet(
        "library_update_manga_restriction",
        setOf(
            ENTRY_HAS_UNVIEWED,
            ENTRY_NON_COMPLETED,
            ENTRY_NON_VIEWED,
            ENTRY_OUTSIDE_RELEASE_PERIOD,
        ),
    )

    val autoUpdateMetadata: Preference<Boolean> = preferenceStore.getBoolean("auto_update_metadata", false)

    val showContinueReadingButton: Preference<Boolean> =
        preferenceStore.getBoolean("display_continue_reading_button", false)

    // Common Category

    val categoryTabs: Preference<Boolean> = preferenceStore.getBoolean("display_category_tabs", true)

    val categoryNumberOfItems: Preference<Boolean> = preferenceStore.getBoolean("display_number_of_items", false)

    val categorizedDisplaySettings: Preference<Boolean> = preferenceStore.getBoolean("categorized_display", false)

    val hideHiddenCategoriesSettings = preferenceStore.getBoolean("hidden_categories", false)

    val filterIntervalCustom: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_interval_custom",
        TriState.DISABLED,
    )

    // Common badges

    val downloadBadge: Preference<Boolean> = preferenceStore.getBoolean("display_download_badge", false)

    val unreadBadge: Preference<Boolean> = preferenceStore.getBoolean("display_unread_badge", true)

    val localBadge: Preference<Boolean> = preferenceStore.getBoolean("display_local_badge", true)

    val languageBadge: Preference<Boolean> = preferenceStore.getBoolean("display_language_badge", false)

    val newShowUpdatesCount: Preference<Boolean> = preferenceStore.getBoolean("library_show_updates_count", true)
    val newUpdatesCount: Preference<Int> = preferenceStore.getInt("library_unread_updates_count", 0)

    // Common Cache

    val autoClearItemCache = preferenceStore.getBoolean("auto_clear_chapter_cache", false)
    val autoClearChapterCache: Preference<Boolean> = autoClearItemCache
    val hideMissingChapters: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_hide_missing_chapter_indicators",
        false,
    )

    // Random Sort Seed

    val randomAnimeSortSeed = preferenceStore.getInt("library_random_anime_sort_seed", 0)
    val randomMangaSortSeed = preferenceStore.getInt("library_random_manga_sort_seed", 0)

    // Mixture Columns

    val animePortraitColumns = preferenceStore.getInt("pref_animelib_columns_portrait_key", 0)
    val mangaPortraitColumns = preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    val animeLandscapeColumns = preferenceStore.getInt("pref_animelib_columns_landscape_key", 0)
    val mangaLandscapeColumns = preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    // Mixture Filter

    val filterDownloadedAnime = preferenceStore.getEnum("pref_filter_animelib_downloaded_v2", TriState.DISABLED)

    val filterDownloaded: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_downloaded_v2",
        TriState.DISABLED,
    )

    val filterDownloadedManga = preferenceStore.getEnum("pref_filter_library_downloaded_v2", TriState.DISABLED)

    val filterUnseen = preferenceStore.getEnum("pref_filter_animelib_unread_v2", TriState.DISABLED)

    val filterUnread: Preference<TriState> =
        preferenceStore.getEnum("pref_filter_library_unread_v2", TriState.DISABLED)

    val filterStartedAnime = preferenceStore.getEnum("pref_filter_animelib_started_v2", TriState.DISABLED)

    val filterStarted: Preference<TriState> =
        preferenceStore.getEnum("pref_filter_library_started_v2", TriState.DISABLED)

    val filterBookmarkedAnime = preferenceStore.getEnum("pref_filter_animelib_bookmarked_v2", TriState.DISABLED)

    val filterBookmarked: Preference<TriState> =
        preferenceStore.getEnum("pref_filter_library_bookmarked_v2", TriState.DISABLED)

    val filterCompletedAnime = preferenceStore.getEnum("pref_filter_animelib_completed_v2", TriState.DISABLED)

    val filterCompleted: Preference<TriState> =
        preferenceStore.getEnum("pref_filter_library_completed_v2", TriState.DISABLED)

    fun filterTrackedAnime(id: Int) =
        preferenceStore.getEnum("pref_filter_animelib_tracked_${id}_v2", TriState.DISABLED)

    fun filterTrackedManga(id: Int) =
        preferenceStore.getEnum("pref_filter_library_tracked_${id}_v2", TriState.DISABLED)

    fun filterTracking(id: Int) = filterTrackedManga(id)

    // Mixture Update Count

    val newMangaUpdatesCount = preferenceStore.getInt("library_unread_updates_count", 0)
    val newAnimeUpdatesCount = preferenceStore.getInt("library_unseen_updates_count", 0)

    // Mixture Category

    val defaultAnimeCategory = preferenceStore.getInt(DEFAULT_ANIME_CATEGORY_PREF_KEY, -1)
    val defaultCategory: Preference<Int> = preferenceStore.getInt(DEFAULT_MANGA_CATEGORY_PREF_KEY, -1)
    val lastUsedAnimeCategory = preferenceStore.getInt(Preference.appStateKey("last_used_anime_category"), 0)
    val lastUsedCategory: Preference<Int> = preferenceStore.getInt(Preference.appStateKey("last_used_category"), 0)
    val animeUpdateCategories = preferenceStore.getStringSet(LIBRARY_UPDATE_ANIME_CATEGORIES_PREF_KEY, emptySet())

    val updateCategories: Preference<Set<String>> =
        preferenceStore.getStringSet(LIBRARY_UPDATE_MANGA_CATEGORIES_PREF_KEY, emptySet())
    val animeUpdateCategoriesExclude = preferenceStore.getStringSet(LIBRARY_UPDATE_ANIME_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    val updateCategoriesExclude: Preference<Set<String>> =
        preferenceStore.getStringSet(LIBRARY_UPDATE_MANGA_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())
    val updateMangaTitles: Preference<Boolean> = preferenceStore.getBoolean("pref_update_library_manga_titles", false)
    // Mixture Item

    val filterEpisodeBySeen = preferenceStore.getLong("default_episode_filter_by_seen", Anime.SHOW_ALL)

    val filterChapterByRead: Preference<Long> =
        preferenceStore.getLong("default_chapter_filter_by_read", Manga.SHOW_ALL)

    val filterEpisodeByDownloaded = preferenceStore.getLong("default_episode_filter_by_downloaded", Anime.SHOW_ALL)

    val filterChapterByDownloaded: Preference<Long> =
        preferenceStore.getLong("default_chapter_filter_by_downloaded", Manga.SHOW_ALL)

    val filterEpisodeByBookmarked = preferenceStore.getLong("default_episode_filter_by_bookmarked", Anime.SHOW_ALL)

    val filterChapterByBookmarked: Preference<Long> =
        preferenceStore.getLong("default_chapter_filter_by_bookmarked", Manga.SHOW_ALL)

    val filterEpisodeByFillermarked = preferenceStore.getLong("default_episode_filter_by_fillermarked", Anime.SHOW_ALL)

    // and upload date
    val sortEpisodeBySourceOrNumber = preferenceStore.getLong(
        "default_episode_sort_by_source_or_number",
        Anime.EPISODE_SORTING_SOURCE,
    )

    val sortChapterBySourceOrNumber: Preference<Long> = preferenceStore.getLong(
        "default_chapter_sort_by_source_or_number",
        Manga.CHAPTER_SORTING_SOURCE,
    )

    val displayEpisodeByNameOrNumber = preferenceStore.getLong(
        "default_chapter_display_by_name_or_number",
        Anime.EPISODE_DISPLAY_NAME,
    )

    val displayChapterByNameOrNumber: Preference<Long> = preferenceStore.getLong(
        "default_chapter_display_by_name_or_number",
        Manga.CHAPTER_DISPLAY_NAME,
    )

    val sortEpisodeByAscendingOrDescending = preferenceStore.getLong(
        "default_chapter_sort_by_ascending_or_descending",
        Anime.EPISODE_SORT_DESC,
    )

    val sortChapterByAscendingOrDescending: Preference<Long> = preferenceStore.getLong(
        "default_chapter_sort_by_ascending_or_descending",
        Manga.CHAPTER_SORT_DESC,
    )

    val showEpisodeThumbnailPreviews = preferenceStore.getLong(
        "default_episode_show_thumbnail_previews",
        Anime.EPISODE_SHOW_PREVIEWS,
    )

    val showEpisodeSummaries = preferenceStore.getLong(
        "default_episode_show_summaries",
        Anime.EPISODE_SHOW_SUMMARIES,
    )

    fun setEpisodeSettingsDefault(anime: Anime) {
        filterEpisodeBySeen.set(anime.unseenFilterRaw)
        filterEpisodeByDownloaded.set(anime.downloadedFilterRaw)
        filterEpisodeByBookmarked.set(anime.bookmarkedFilterRaw)
        filterEpisodeByFillermarked.set(anime.fillermarkedFilterRaw)
        sortEpisodeBySourceOrNumber.set(anime.sorting)
        displayEpisodeByNameOrNumber.set(anime.displayMode)
        sortEpisodeByAscendingOrDescending.set(
            if (anime.sortDescending()) Anime.EPISODE_SORT_DESC else Anime.EPISODE_SORT_ASC,
        )
        showEpisodeThumbnailPreviews.set(anime.showPreviewsRaw)
        showEpisodeSummaries.set(anime.showSummariesRaw)
    }

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead.set(manga.unreadFilterRaw)
        filterChapterByDownloaded.set(manga.downloadedFilterRaw)
        filterChapterByBookmarked.set(manga.bookmarkedFilterRaw)
        sortChapterBySourceOrNumber.set(manga.sorting)
        displayChapterByNameOrNumber.set(manga.displayMode)
        sortChapterByAscendingOrDescending.set(
            if (manga.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC,
        )
    }

    // Seasons

    val filterSeasonByDownload = preferenceStore.getLong("default_season_filter_by_downloaded", Anime.SHOW_ALL)

    val filterSeasonByUnseen = preferenceStore.getLong("default_season_filter_by_unseen", Anime.SHOW_ALL)

    val filterSeasonByStarted = preferenceStore.getLong("default_season_filter_by_started", Anime.SHOW_ALL)

    val filterSeasonByCompleted = preferenceStore.getLong("default_season_filter_by_completed", Anime.SHOW_ALL)

    val filterSeasonByBookmarked = preferenceStore.getLong("default_season_filter_by_bookmarked", Anime.SHOW_ALL)

    val filterSeasonByFillermarked = preferenceStore.getLong("default_season_filter_by_fillermarked", Anime.SHOW_ALL)

    val sortSeasonBySourceOrNumber = preferenceStore.getLong(
        "default_season_sort_by_source_or_number",
        Anime.SEASON_SORT_SOURCE,
    )

    val sortSeasonByAscendingOrDescending = preferenceStore.getLong(
        "default_season_sort_by_ascending_or_descending",
        Anime.SEASON_SORT_DESC,
    )

    val seasonDisplayGridMode = preferenceStore.getLong(
        "default_season_grid_display_mode",
        SeasonDisplayMode.toLong(SeasonDisplayMode.CompactGrid),
    )

    val seasonDisplayGridSize = preferenceStore.getInt(
        "default_season_grid_display_size",
        0,
    )

    val seasonDownloadOverlay = preferenceStore.getBoolean(
        "default_season_download_overlay",
        false,
    )

    val seasonUnseenOverlay = preferenceStore.getBoolean(
        "default_season_unseen_overlay",
        true,
    )

    val seasonLocalOverlay = preferenceStore.getBoolean(
        "default_season_local_overlay",
        true,
    )

    val seasonLangOverlay = preferenceStore.getBoolean(
        "default_season_lang_overlay",
        false,
    )

    val seasonContinueOverlay = preferenceStore.getBoolean(
        "default_season_continue_overlay",
        true,
    )

    val seasonDisplayMode = preferenceStore.getLong(
        "default_season_display_mode",
        Anime.SEASON_DISPLAY_MODE_SOURCE,
    )

    fun setSeasonSettingsDefault(anime: Anime) {
        filterSeasonByDownload.set(anime.seasonUnseenFilterRaw)
        filterSeasonByUnseen.set(anime.seasonUnseenFilterRaw)
        filterSeasonByStarted.set(anime.seasonStartedFilterRaw)
        filterSeasonByCompleted.set(anime.seasonCompletedFilterRaw)
        filterSeasonByBookmarked.set(anime.seasonBookmarkedFilterRaw)
        filterSeasonByFillermarked.set(anime.seasonFillermarkedFilterRaw)
        sortSeasonBySourceOrNumber.set(anime.seasonSorting)
        sortSeasonByAscendingOrDescending.set(
            if (anime.seasonSortDescending()) Anime.SEASON_SORT_DESC else Anime.SEASON_SORT_ASC,
        )
        seasonDisplayGridMode.set(SeasonDisplayMode.toLong(anime.seasonDisplayGridMode))
        seasonDisplayGridSize.set(anime.seasonDisplayGridSize)
        seasonDownloadOverlay.set(anime.seasonDownloadedOverlay)
        seasonUnseenOverlay.set(anime.seasonUnseenOverlay)
        seasonLocalOverlay.set(anime.seasonLocalOverlay)
        seasonLangOverlay.set(anime.seasonLangOverlay)
        seasonContinueOverlay.set(anime.seasonContinueOverlay)
        seasonDisplayMode.set(anime.seasonDisplayMode)
    }

    // Season behavior

    val updateSeasonOnRefresh = preferenceStore.getBoolean("pref_update_season_on_refresh", false)

    val updateSeasonOnLibraryUpdate = preferenceStore.getBoolean("pref_update_season_on_library_update", false)

    // region Item behavior

    val swipeEpisodeStartAction = preferenceStore.getEnum("pref_episode_swipe_end_action", EpisodeSwipeAction.ToggleSeen)

    val swipeEpisodeEndAction = preferenceStore.getEnum(
        "pref_episode_swipe_start_action",
        EpisodeSwipeAction.ToggleBookmark,
    )

    val swipeChapterStartAction = preferenceStore.getEnum("pref_chapter_swipe_end_action", ChapterSwipeAction.ToggleRead)

    val swipeChapterEndAction = preferenceStore.getEnum(
        "pref_chapter_swipe_start_action",
        ChapterSwipeAction.ToggleBookmark,
    )

    val markDuplicateReadChapterAsRead: Preference<Set<String>> =
        preferenceStore.getStringSet("mark_duplicate_read_chapter_read", emptySet())

    val markDuplicateSeenEpisodeAsSeen = preferenceStore.getStringSet("mark_duplicate_seen_episode_seen", emptySet())

    // endregion

    enum class EpisodeSwipeAction {
        ToggleSeen,
        ToggleBookmark,
        ToggleFillermark,
        Download,
        Disabled,
    }

    enum class ChapterSwipeAction {
        ToggleRead,
        ToggleBookmark,
        Download,
        Disabled,
    }

    // SY -->

    val groupAnimeLibraryUpdateType = preferenceStore.getEnum(
        "group_anime_library_update_type",
        AnimeGroupLibraryMode.GLOBAL,
    )

    val groupMangaLibraryUpdateType = preferenceStore.getEnum(
        "group_library_update_type",
        MangaGroupLibraryMode.GLOBAL,
    )

    val groupAnimeLibraryBy = preferenceStore.getInt(
        "group_anime_library_by",
        AnimeLibraryGroup.BY_DEFAULT,
    )

    val groupMangaLibraryBy = preferenceStore.getInt(
        "group_library_by",
        MangaLibraryGroup.BY_DEFAULT,
    )

    // SY <--

    companion object {
        const val DEVICE_ONLY_ON_WIFI = "wifi"
        const val DEVICE_NETWORK_NOT_METERED = "network_not_metered"
        const val DEVICE_CHARGING = "ac"

        const val ENTRY_NON_COMPLETED = "manga_ongoing"
        const val ENTRY_HAS_UNVIEWED = "manga_fully_read"
        const val ENTRY_NON_VIEWED = "manga_started"
        const val ENTRY_OUTSIDE_RELEASE_PERIOD = "manga_outside_release_period"

        const val MARK_DUPLICATE_CHAPTER_READ_NEW = "new"
        const val MARK_DUPLICATE_CHAPTER_READ_EXISTING = "existing"
        const val MARK_DUPLICATE_EPISODE_SEEN_NEW = "new_episode"
        const val MARK_DUPLICATE_EPISODE_SEEN_EXISTING = "existing_episode"

        const val DEFAULT_MANGA_CATEGORY_PREF_KEY = "default_category"
        const val DEFAULT_ANIME_CATEGORY_PREF_KEY = "default_anime_category"
        private const val LIBRARY_UPDATE_MANGA_CATEGORIES_PREF_KEY = "library_update_categories"
        private const val LIBRARY_UPDATE_ANIME_CATEGORIES_PREF_KEY = "animelib_update_categories"
        private const val LIBRARY_UPDATE_MANGA_CATEGORIES_EXCLUDE_PREF_KEY = "library_update_categories_exclude"
        private const val LIBRARY_UPDATE_ANIME_CATEGORIES_EXCLUDE_PREF_KEY = "animelib_update_categories_exclude"
        val categoryPreferenceKeys = setOf(
            DEFAULT_MANGA_CATEGORY_PREF_KEY,
            DEFAULT_ANIME_CATEGORY_PREF_KEY,
            LIBRARY_UPDATE_MANGA_CATEGORIES_PREF_KEY,
            LIBRARY_UPDATE_ANIME_CATEGORIES_PREF_KEY,
            LIBRARY_UPDATE_MANGA_CATEGORIES_EXCLUDE_PREF_KEY,
            LIBRARY_UPDATE_ANIME_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
