package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val downloadOnlyOverWifi: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    val useExternalDownloader: Preference<Boolean> = preferenceStore.getBoolean("use_external_downloader", false)

    val externalDownloaderSelection: Preference<String> = preferenceStore.getString(
        "external_downloader_selection",
        "",
    )

    val saveChaptersAsCBZ: Preference<Boolean> = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    val splitTallImages: Preference<Boolean> = preferenceStore.getBoolean("split_tall_images", true)

    val autoDownloadWhileReading: Preference<Int> = preferenceStore.getInt("auto_download_while_reading", 0)
    val autoDownloadWhileWatching: Preference<Int> = preferenceStore.getInt("auto_download_while_watching", 0)

    val removeAfterReadSlots: Preference<Int> = preferenceStore.getInt("remove_after_read_slots", -1)

    val removeAfterMarkedAsRead: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    val removeBookmarkedChapters: Preference<Boolean> = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    val downloadFillermarkedItems: Preference<Boolean> = preferenceStore.getBoolean("pref_download_fillermarked", false)

    val removeExcludeCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        REMOVE_EXCLUDE_MANGA_CATEGORIES_PREF_KEY,
        emptySet(),
    )
    val removeExcludeAnimeCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        REMOVE_EXCLUDE_ANIME_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    val downloadNewChapters: Preference<Boolean> = preferenceStore.getBoolean("download_new", false)
    val downloadNewEpisodes: Preference<Boolean> = preferenceStore.getBoolean("download_new_episode", false)

    val downloadNewChapterCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        DOWNLOAD_NEW_MANGA_CATEGORIES_PREF_KEY,
        emptySet(),
    )
    val downloadNewEpisodeCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        DOWNLOAD_NEW_ANIME_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    val downloadNewChapterCategoriesExclude: Preference<Set<String>> = preferenceStore.getStringSet(
        DOWNLOAD_NEW_MANGA_CATEGORIES_EXCLUDE_PREF_KEY,
        emptySet(),
    )
    val downloadNewEpisodeCategoriesExclude: Preference<Set<String>> = preferenceStore.getStringSet(
        DOWNLOAD_NEW_ANIME_CATEGORIES_EXCLUDE_PREF_KEY,
        emptySet(),
    )

    val numberOfDownloads: Preference<Int> = preferenceStore.getInt("download_slots", 1)
    val downloadSpeedLimit: Preference<Int> = preferenceStore.getInt("download_speed_limit", 0)

    val downloadNewUnreadChaptersOnly: Preference<Boolean> = preferenceStore.getBoolean(
        "download_new_unread_chapters_only",
        false,
    )
    val downloadNewUnseenEpisodesOnly: Preference<Boolean> = preferenceStore.getBoolean(
        "download_new_unread_episodes_only",
        false,
    )
    companion object {
        private const val REMOVE_EXCLUDE_MANGA_CATEGORIES_PREF_KEY = "remove_exclude_categories"
        private const val REMOVE_EXCLUDE_ANIME_CATEGORIES_PREF_KEY = "remove_exclude_anime_categories"
        private const val DOWNLOAD_NEW_MANGA_CATEGORIES_PREF_KEY = "download_new_categories"
        private const val DOWNLOAD_NEW_ANIME_CATEGORIES_PREF_KEY = "download_new_anime_categories"
        private const val DOWNLOAD_NEW_MANGA_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_categories_exclude"
        private const val DOWNLOAD_NEW_ANIME_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_anime_categories_exclude"

        val categoryPreferenceKeys = setOf(
            REMOVE_EXCLUDE_MANGA_CATEGORIES_PREF_KEY,
            REMOVE_EXCLUDE_ANIME_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_MANGA_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_ANIME_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_MANGA_CATEGORIES_EXCLUDE_PREF_KEY,
            DOWNLOAD_NEW_ANIME_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
