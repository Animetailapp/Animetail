package tachiyomi.domain.download.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
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

    val includeHashInDownloadFilenames: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_download_filename_hash_key",
        true,
    )

    val autoDownloadWhileReading: Preference<Int> = preferenceStore.getInt("auto_download_while_reading", 0)
    val autoDownloadWhileWatching: Preference<Int> = preferenceStore.getInt("auto_download_while_watching", 0)

    val removeAfterReadSlots: Preference<Int> = preferenceStore.getInt("remove_after_read_slots", -1)

    val removeAfterMarkedAsRead: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    val removeBookmarkedChapters: Preference<Boolean> = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    /** How many of the next unseen episodes of a show to keep downloaded, 0 for off. */
    val keepUnseenEpisodesDownloaded: Preference<Int> = preferenceStore.getInt("download_keep_unseen_episodes", 0)

    /** Keep anime downloads under this many GB by deleting the oldest watched episodes, 0 for no limit. */
    val animeDownloadStorageLimitGb: Preference<Int> = preferenceStore.getInt("anime_download_storage_limit_gb", 0)

    /**
     * The storage limit the user reviewed the cleanup of and applied. The limit is only enforced
     * automatically once it has been applied, so nothing is deleted before its first preview.
     */
    val animeDownloadStorageLimitAppliedGb: Preference<Int> = preferenceStore.getInt(
        "anime_download_storage_limit_applied_gb",
        0,
    )

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

    val parallelSourceLimit: Preference<Int> = preferenceStore.getInt("download_parallel_source_limit", 5)
    val parallelPageLimit: Preference<Int> = preferenceStore.getInt("download_parallel_page_limit", 5)

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
