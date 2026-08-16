package mihon.app.di

import android.content.Context
import aniyomi.core.common.torrent.TorrentPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.service.DelayedAnimeTrackingUpdateJob
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.manga.service.DelayedMangaTrackingUpdateJob
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadJob
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadJob
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.anime.AnimeMetadataUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaMetadataUpdateJob
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionInstallActivity
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionInstallActivity
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegateImpl
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.setting.track.BaseOAuthLoginActivity
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.CrashLogUtil
import kotlinx.serialization.json.Json
import mihon.core.metro.IsDebugBuild
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStoreCountAsFlow
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStores
import mihon.domain.extension.manga.interactor.GetMangaExtensionStoreCountAsFlow
import mihon.domain.extension.manga.interactor.GetMangaExtensionStores
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.ResetAnimeCategoryFlags
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.ResetMangaCategoryFlags
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.ResetAnimeViewerFlags
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.ResetMangaViewerFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [AppBindings::class],
)
interface AppGraph : ViewModelGraph {
    fun inject(app: App)
    fun inject(mainActivity: MainActivity)
    fun inject(readerActivity: ReaderActivity)
    fun inject(webViewActivity: WebViewActivity)
    fun inject(baseOAuthLoginActivity: BaseOAuthLoginActivity)
    fun inject(libraryUpdateJob: MangaLibraryUpdateJob)
    fun inject(animeLibraryUpdateJob: AnimeLibraryUpdateJob)
    fun inject(metadataUpdateJob: MangaMetadataUpdateJob)
    fun inject(animeMetadataUpdateJob: AnimeMetadataUpdateJob)
    fun inject(backupRestoreJob: BackupRestoreJob)
    fun inject(backupCreateJob: BackupCreateJob)
    fun inject(delayedMangaTrackingUpdateJob: DelayedMangaTrackingUpdateJob)
    fun inject(delayedAnimeTrackingUpdateJob: DelayedAnimeTrackingUpdateJob)
    fun inject(downloadJob: MangaDownloadJob)
    fun inject(animeDownloadJob: AnimeDownloadJob)
    fun inject(notificationReceiver: NotificationReceiver)
    fun inject(secureActivityDelegate: SecureActivityDelegateImpl)
    fun inject(mangaExtensionInstallActivity: MangaExtensionInstallActivity)
    fun inject(animeExtensionInstallActivity: AnimeExtensionInstallActivity)
    fun inject(syncDataJob: SyncDataJob)

    val context: Context

    val viewModelFactory: MetroViewModelFactory

    val preferenceStore: PreferenceStore
    val basePreferences: BasePreferences
    val uiPreferences: UiPreferences
    val readerPreferences: ReaderPreferences
    val playerPreferences: PlayerPreferences
    val gesturePreferences: GesturePreferences
    val advancedPlayerPreferences: AdvancedPlayerPreferences
    val torrentPreferences: TorrentPreferences
    val connectionsPreferences: ConnectionsPreferences
    val syncPreferences: SyncPreferences
    val networkPreferences: NetworkPreferences
    val libraryPreferences: LibraryPreferences
    val sourcePreferences: SourcePreferences
    val trackPreferences: TrackPreferences
    val backupPreferences: BackupPreferences
    val storagePreferences: StoragePreferences
    val privacyPreferences: PrivacyPreferences
    val securityPreferences: SecurityPreferences
    val downloadPreferences: DownloadPreferences

    val crashLogUtil: CrashLogUtil

    val mangaDownloadManager: MangaDownloadManager
    val animeDownloadManager: AnimeDownloadManager

    val updateChecker: AppUpdateChecker
    val syncManager: SyncManager

    val trustMangaExtension: TrustMangaExtension
    val trustAnimeExtension: TrustAnimeExtension

    val mangaSourceManager: MangaSourceManager
    val animeSourceManager: AnimeSourceManager
    val trackerManager: TrackerManager
    val mangaExtensionManager: MangaExtensionManager
    val animeExtensionManager: AnimeExtensionManager
    val chapterCache: ChapterCache
    val mangaDownloadCache: MangaDownloadCache
    val animeDownloadCache: AnimeDownloadCache
    val mangaCoverCache: MangaCoverCache
    val animeCoverCache: AnimeCoverCache
    val animeBackgroundCache: AnimeBackgroundCache

    val json: Json
    val networkHelper: NetworkHelper

    val getManga: GetManga
    val getAnime: GetAnime
    val getMangaFavorites: GetMangaFavorites
    val getAnimeFavorites: GetAnimeFavorites
    val getMangaCategories: GetMangaCategories
    val getAnimeCategories: GetAnimeCategories
    val resetMangaViewerFlags: ResetMangaViewerFlags
    val resetAnimeViewerFlags: ResetAnimeViewerFlags
    val resetMangaCategoryFlags: ResetMangaCategoryFlags
    val resetAnimeCategoryFlags: ResetAnimeCategoryFlags
    val addMangaTracks: AddMangaTracks
    val addAnimeTracks: AddAnimeTracks
    val insertMangaTrack: InsertMangaTrack
    val insertAnimeTrack: InsertAnimeTrack

    val getMangaExtensionStoreCountAsFlow: GetMangaExtensionStoreCountAsFlow
    val getAnimeExtensionStoreCountAsFlow: GetAnimeExtensionStoreCountAsFlow
    val getMangaExtensionStores: GetMangaExtensionStores
    val getAnimeExtensionStores: GetAnimeExtensionStores

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context, @Provides @IsDebugBuild isDebugBuild: Boolean): AppGraph
    }
}
