package mihon.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data class WebViewRoute(
    val url: String,
    val initialTitle: String? = null,
    val sourceId: Long? = null,
) : NavKey

@Serializable
data object CategoryRoute : NavKey

@Serializable
data object DownloadQueueRoute : NavKey

@Serializable
data object StatsRoute : NavKey

@Serializable
data object StorageRoute : NavKey

@Serializable
data object NetworkStreamRoute : NavKey

@Serializable
data class NewUpdateRoute(
    val versionName: String,
    val changelogInfo: String,
    val releaseLink: String,
    val downloadLink: String,
) : NavKey

@Serializable
data object OnboardingRoute : NavKey

@Serializable
data object SupportUsRoute : NavKey

// Manga routes

@Serializable
data class DeepLinkMangaRoute(val query: String) : NavKey

@Serializable
data class MangaRoute(
    val mangaId: Long,
    val fromSource: Boolean = false,
) : NavKey

@Serializable
data class MangaNotesRoute(val mangaId: Long) : NavKey

@Serializable
data class MangaTrackInfoDialogRoute(
    val mangaId: Long,
    val mangaTitle: String,
    val sourceId: Long,
) : NavKey

@Serializable
data class GlobalMangaSearchRoute(
    val searchQuery: String = "",
    val extensionFilter: String? = null,
) : NavKey

@Serializable
data object UpcomingMangaRoute : NavKey

@Serializable
data class BrowseMangaSourceRoute(
    val sourceId: Long,
    val listingQuery: String?,
) : NavKey

@Serializable
data class MangaExtensionDetailsRoute(val pkgName: String) : NavKey

@Serializable
data object MangaExtensionFilterRoute : NavKey

@Serializable
data object MangaSourcesFilterRoute : NavKey

@Serializable
data class MangaSourcePreferencesRoute(val sourceId: Long) : NavKey

@Serializable
data class MigrateMangaRoute(val sourceId: Long) : NavKey

@Serializable
data class MigrateMangaSearchRoute(val mangaId: Long) : NavKey

@Serializable
data class MangaSourceSearchRoute(
    val currentMangaId: Long,
    val sourceId: Long,
    val query: String?,
) : NavKey

@Serializable
data class MangaMigrationConfigRoute(val mangaIds: Collection<Long>) : NavKey {
    constructor(mangaId: Long) : this(listOf(mangaId))
}

@Serializable
data class MangaMigrationListRoute(
    val mangaIds: Collection<Long>,
    val extraSearchQuery: String?,
) : NavKey

// Anime routes

@Serializable
data class DeepLinkAnimeRoute(val query: String) : NavKey

@Serializable
data class AnimeRoute(
    val animeId: Long,
    val fromSource: Boolean = false,
) : NavKey

@Serializable
data class AnimeNotesRoute(val animeId: Long) : NavKey

@Serializable
data class AnimeTrackInfoDialogRoute(
    val animeId: Long,
    val animeTitle: String,
    val sourceId: Long,
    val isSeason: Boolean = false,
) : NavKey

@Serializable
data class EpisodeOptionsDialogRoute(
    val useExternalDownloader: Boolean,
    val episodeTitle: String,
    val episodeId: Long,
    val animeId: Long,
    val sourceId: Long,
) : NavKey

@Serializable
data class GlobalAnimeSearchRoute(
    val searchQuery: String = "",
    val extensionFilter: String? = null,
) : NavKey

@Serializable
data object UpcomingAnimeRoute : NavKey

@Serializable
data class BrowseAnimeSourceRoute(
    val sourceId: Long,
    val listingQuery: String?,
    val filtersJson: String? = null,
    val savedSearch: Long? = null,
) : NavKey

@Serializable
data class SourceFeedRoute(val sourceId: Long) : NavKey

@Serializable
data class AnimeExtensionDetailsRoute(val pkgName: String) : NavKey

@Serializable
data object AnimeExtensionFilterRoute : NavKey

@Serializable
data object AnimeSourcesFilterRoute : NavKey

@Serializable
data class AnimeSourcePreferencesRoute(val sourceId: Long) : NavKey

@Serializable
data class MigrateAnimeRoute(val sourceId: Long) : NavKey

@Serializable
data class MigrateSeasonSelectRoute(
    val oldAnimeId: Long,
    val animeId: Long,
) : NavKey

@Serializable
data class MigrateAnimeSearchRoute(val animeId: Long) : NavKey

@Serializable
data class AnimeSourceSearchRoute(
    val currentAnimeId: Long,
    val sourceId: Long,
    val query: String?,
) : NavKey

@Serializable
data class AnimeMigrationConfigRoute(val animeIds: Collection<Long>) : NavKey {
    constructor(animeId: Long) : this(listOf(animeId))
}

@Serializable
data class AnimeMigrationListRoute(
    val animeIds: Collection<Long>,
    val extraSearchQuery: String?,
) : NavKey

// Settings routes

@Serializable
data object AboutRoute : NavKey

@Serializable
data object AppLanguageRoute : NavKey

@Serializable
data object AppCustomThemeColorPickerRoute : NavKey

@Serializable
data object BackupSchemaRoute : NavKey {
    const val TITLE = "Backup file schema"
}

@Serializable
data object ClearDatabaseRoute : NavKey

@Serializable
data object ClearAnimeDatabaseRoute : NavKey

@Serializable
data object CreateBackupRoute : NavKey

@Serializable
data object DebugInfoRoute : NavKey

@Serializable
data class ExtensionStoresRoute(val isManga: Boolean) : NavKey

@Serializable
data object OpenSourceLicensesRoute : NavKey

@Serializable
data class RestoreBackupRoute(val uri: String) : NavKey

@Serializable
data class SettingsSearchRoute(val isPlayer: Boolean = false) : NavKey

@Serializable
data object WorkerInfoRoute : NavKey {
    const val TITLE = "Worker info"
}

@Serializable
data object SyncSettingsSelectorRoute : NavKey

@Serializable
data object SyncTriggerOptionsRoute : NavKey

@Serializable
data object SettingsDiscordAccountsRoute : NavKey

@Serializable
data object PlayerSettingsCustomButtonRoute : NavKey

@Serializable
data object PlayerSettingsEditorRoute : NavKey

@Serializable
data class CodeEditRoute(val filePath: String) : NavKey
