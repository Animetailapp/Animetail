package mihon.app.di

import android.content.Context
import animetail.feature.mpvfiles.MpvConfig
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.SyncSeasonsWithSource
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionLanguages
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionSources
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionLanguages
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.items.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.anime.interactor.GetAnimeSourcesWithFavoriteCount
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.anime.interactor.GetExhSavedSearch
import eu.kanade.domain.source.anime.interactor.GetLanguagesWithAnimeSources
import eu.kanade.domain.source.anime.interactor.InsertSavedSearch
import eu.kanade.domain.source.anime.interactor.ToggleAnimeIncognito
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.manga.interactor.GetLanguagesWithMangaSources
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.source.manga.interactor.GetMangaSourcesWithFavoriteCount
import eu.kanade.domain.source.manga.interactor.ToggleExcludeFromMangaDataSaver
import eu.kanade.domain.source.manga.interactor.ToggleMangaIncognito
import eu.kanade.domain.source.manga.interactor.ToggleMangaSource
import eu.kanade.domain.source.manga.interactor.ToggleMangaSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.anime.interactor.SyncEpisodeProgressWithTrack
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.anime.service.DelayedAnimeTrackingUpdateJob
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.domain.track.manga.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.manga.interactor.TrackChapter
import eu.kanade.domain.track.manga.service.DelayedMangaTrackingUpdateJob
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
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
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadJob
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadJob
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.anime.AnimeMetadataUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaMetadataUpdateJob
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.saver.ImageSaver
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
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.setting.track.BaseOAuthLoginActivity
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.LocalHttpServerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import mihon.core.metro.IsDebugBuild
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStoreCountAsFlow
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStores
import mihon.domain.extension.manga.interactor.GetMangaExtensionStoreCountAsFlow
import mihon.domain.extension.manga.interactor.GetMangaExtensionStores
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.HideAnimeCategory
import tachiyomi.domain.category.anime.interactor.RenameAnimeCategory
import tachiyomi.domain.category.anime.interactor.ReorderAnimeCategory
import tachiyomi.domain.category.anime.interactor.ResetAnimeCategoryFlags
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeDisplayMode
import tachiyomi.domain.category.anime.interactor.SetSortModeForAnimeCategory
import tachiyomi.domain.category.manga.interactor.CreateMangaCategoryWithName
import tachiyomi.domain.category.manga.interactor.DeleteMangaCategory
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.manga.interactor.HideMangaCategory
import tachiyomi.domain.category.manga.interactor.RenameMangaCategory
import tachiyomi.domain.category.manga.interactor.ReorderMangaCategory
import tachiyomi.domain.category.manga.interactor.ResetMangaCategoryFlags
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaDisplayMode
import tachiyomi.domain.category.manga.interactor.SetSortModeForMangaCategory
import tachiyomi.domain.custombuttons.interactor.CreateCustomButton
import tachiyomi.domain.custombuttons.interactor.DeleteCustomButton
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.interactor.ReorderCustomButton
import tachiyomi.domain.custombuttons.interactor.ToggleFavoriteCustomButton
import tachiyomi.domain.custombuttons.interactor.UpdateCustomButton
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.entries.anime.interactor.GetCustomAnimeInfo
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.interactor.ResetAnimeViewerFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.entries.anime.interactor.SetCustomAnimeInfo
import tachiyomi.domain.entries.anime.interactor.UpdateAnimeNotes
import tachiyomi.domain.entries.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.entries.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.interactor.ResetMangaViewerFlags
import tachiyomi.domain.entries.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.interactor.UpdateMangaNotes
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.history.manga.interactor.RemoveMangaHistory
import tachiyomi.domain.items.chapter.interactor.GetBookmarkedChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.GetEpisodeByUrlAndAnimeId
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.items.season.interactor.SetAnimeDefaultSeasonFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.source.anime.interactor.CountFeedSavedSearchBySourceId
import tachiyomi.domain.source.anime.interactor.CountFeedSavedSearchGlobal
import tachiyomi.domain.source.anime.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.anime.interactor.GetFeedSavedSearchBySourceId
import tachiyomi.domain.source.anime.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.anime.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.anime.interactor.GetSavedSearchBySourceIdFeed
import tachiyomi.domain.source.anime.interactor.GetSavedSearchGlobalFeed
import tachiyomi.domain.source.anime.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.anime.interactor.ReorderFeed
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.manga.interactor.DeleteMangaTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import eu.kanade.domain.extension.manga.interactor.GetExtensionSources as GetMangaExtensionSources

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [AppBindings::class],
)
interface AppGraph : ViewModelGraph {
    fun inject(app: App)
    fun inject(mainActivity: MainActivity)
    fun inject(readerActivity: ReaderActivity)
    fun inject(playerActivity: PlayerActivity)
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
    val decoderPreferences: DecoderPreferences
    val audioPreferences: AudioPreferences
    val subtitlePreferences: SubtitlePreferences
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
    val storageManager: StorageManager
    val localHttpServerHolder: LocalHttpServerHolder
    val torrentServerApi: TorrentServerApi
    val torrentServerUtils: TorrentServerUtils

    val crashLogUtil: CrashLogUtil

    val mangaDownloadManager: MangaDownloadManager
    val animeDownloadManager: AnimeDownloadManager

    val mangaDownloadProvider: MangaDownloadProvider
    val animeDownloadProvider: AnimeDownloadProvider

    val updateChecker: AppUpdateChecker
    val syncManager: SyncManager

    val trustMangaExtension: TrustMangaExtension
    val trustAnimeExtension: TrustAnimeExtension

    val mangaSourceManager: MangaSourceManager
    val animeSourceManager: AnimeSourceManager
    val trackerManager: TrackerManager
    val connectionsManager: ConnectionsManager
    val mangaExtensionManager: MangaExtensionManager
    val animeExtensionManager: AnimeExtensionManager
    val chapterCache: ChapterCache
    val mangaDownloadCache: MangaDownloadCache
    val animeDownloadCache: AnimeDownloadCache
    val mangaCoverCache: MangaCoverCache
    val animeCoverCache: AnimeCoverCache
    val animeBackgroundCache: AnimeBackgroundCache

    val localMangaCoverManager: LocalMangaCoverManager
    val localAnimeCoverManager: LocalAnimeCoverManager
    val localAnimeBackgroundManager: LocalAnimeBackgroundManager
    val localEpisodeThumbnailManager: LocalEpisodeThumbnailManager

    val json: Json
    val networkHelper: NetworkHelper
    val coroutineScope: CoroutineScope

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
    val upsertAnimeHistory: UpsertAnimeHistory
    val updateEpisode: UpdateEpisode
    val getEpisodesByAnimeId: GetEpisodesByAnimeId
    val getAnimeTracks: GetAnimeTracks
    val delayedAnimeTrackingStore: DelayedAnimeTrackingStore
    val getEpisode: GetEpisode
    val getChapter: GetChapter
    val getCustomButtons: GetCustomButtons
    val createCustomButton: CreateCustomButton
    val deleteCustomButton: DeleteCustomButton
    val updateCustomButton: UpdateCustomButton
    val reorderCustomButton: ReorderCustomButton
    val toggleFavoriteCustomButton: ToggleFavoriteCustomButton
    val mpvConfig: MpvConfig

    val getAnimeHistory: GetAnimeHistory
    val getMangaHistory: GetMangaHistory
    val getLibraryAnime: GetLibraryAnime
    val getLibraryManga: GetLibraryManga
    val networkToLocalAnime: NetworkToLocalAnime
    val networkToLocalManga: NetworkToLocalManga
    val getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal
    val getFeedSavedSearchBySourceId: GetFeedSavedSearchBySourceId
    val getSavedSearchGlobalFeed: GetSavedSearchGlobalFeed
    val getSavedSearchBySourceIdFeed: GetSavedSearchBySourceIdFeed
    val countFeedSavedSearchGlobal: CountFeedSavedSearchGlobal
    val countFeedSavedSearchBySourceId: CountFeedSavedSearchBySourceId
    val getSavedSearchBySourceId: GetSavedSearchBySourceId
    val insertFeedSavedSearch: InsertFeedSavedSearch
    val deleteFeedSavedSearchById: DeleteFeedSavedSearchById
    val reorderFeed: ReorderFeed
    val getExhSavedSearch: GetExhSavedSearch
    val getVisibleAnimeCategories: GetVisibleAnimeCategories
    val getVisibleMangaCategories: GetVisibleMangaCategories
    val getTracksPerAnime: GetTracksPerAnime
    val getTracksPerManga: GetTracksPerManga
    val getNextEpisodes: GetNextEpisodes
    val getNextChapters: GetNextChapters
    val getChaptersByMangaId: GetChaptersByMangaId
    val setSeenStatus: SetSeenStatus
    val setReadStatus: SetReadStatus
    val updateAnime: UpdateAnime
    val updateManga: UpdateManga
    val setAnimeCategories: SetAnimeCategories
    val setMangaCategories: SetMangaCategories
    val removeAnimeHistory: RemoveAnimeHistory
    val removeMangaHistory: RemoveMangaHistory
    val getDuplicateLibraryAnime: GetDuplicateLibraryAnime
    val getDuplicateLibraryManga: GetDuplicateLibraryManga
    val getMangaTracks: GetMangaTracks
    val refreshAnimeTracks: RefreshAnimeTracks
    val refreshMangaTracks: RefreshMangaTracks

    val createAnimeCategoryWithName: CreateAnimeCategoryWithName
    val deleteAnimeCategory: DeleteAnimeCategory
    val hideAnimeCategory: HideAnimeCategory
    val renameAnimeCategory: RenameAnimeCategory
    val reorderAnimeCategory: ReorderAnimeCategory
    val setAnimeDisplayMode: SetAnimeDisplayMode
    val setSortModeForAnimeCategory: SetSortModeForAnimeCategory

    val createMangaCategoryWithName: CreateMangaCategoryWithName
    val deleteMangaCategory: DeleteMangaCategory
    val hideMangaCategory: HideMangaCategory
    val renameMangaCategory: RenameMangaCategory
    val reorderMangaCategory: ReorderMangaCategory
    val setMangaDisplayMode: SetMangaDisplayMode
    val setSortModeForMangaCategory: SetSortModeForMangaCategory

    val deleteAnimeTrack: DeleteAnimeTrack
    val deleteMangaTrack: DeleteMangaTrack
    val trackEpisode: TrackEpisode
    val trackChapter: TrackChapter
    val syncEpisodeProgressWithTrack: SyncEpisodeProgressWithTrack
    val syncChapterProgressWithTrack: SyncChapterProgressWithTrack
    val delayedMangaTrackingStore: DelayedMangaTrackingStore

    val updateAnimeNotes: UpdateAnimeNotes
    val updateMangaNotes: UpdateMangaNotes
    val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId
    val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId
    val getEpisodeByUrlAndAnimeId: GetEpisodeByUrlAndAnimeId
    val getChapterByUrlAndMangaId: GetChapterByUrlAndMangaId
    val updateAnimeFromRemote: UpdateAnimeFromRemote
    val imageSaver: ImageSaver
    val setAnimeViewerFlags: SetAnimeViewerFlags
    val setMangaViewerFlags: SetMangaViewerFlags
    val setAnimeEpisodeFlags: SetAnimeEpisodeFlags
    val setMangaChapterFlags: SetMangaChapterFlags
    val setAnimeSeasonFlags: SetAnimeSeasonFlags
    val setCustomAnimeInfo: SetCustomAnimeInfo
    val setCustomMangaInfo: SetCustomMangaInfo
    val getCustomAnimeInfo: GetCustomAnimeInfo
    val getCustomMangaInfo: GetCustomMangaInfo
    val getAnimeWithEpisodesAndSeasons: GetAnimeWithEpisodesAndSeasons
    val getMangaWithChapters: GetMangaWithChapters
    val animeFetchInterval: AnimeFetchInterval
    val mangaFetchInterval: MangaFetchInterval

    val getExcludedScanlators: GetExcludedScanlators
    val setExcludedScanlators: SetExcludedScanlators
    val getAvailableScanlators: GetAvailableScanlators
    val syncChaptersWithSource: SyncChaptersWithSource
    val syncEpisodesWithSource: SyncEpisodesWithSource
    val syncSeasonsWithSource: SyncSeasonsWithSource
    val updateChapter: UpdateChapter
    val setAnimeDefaultEpisodeFlags: SetAnimeDefaultEpisodeFlags
    val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags
    val setAnimeDefaultSeasonFlags: SetAnimeDefaultSeasonFlags
    val getAnimeSeasonsByParentId: GetAnimeSeasonsByParentId
    val getBookmarkedChaptersByMangaId: GetBookmarkedChaptersByMangaId

    val toggleAnimeSourcePin: ToggleAnimeSourcePin
    val toggleMangaSourcePin: ToggleMangaSourcePin
    val toggleAnimeSource: ToggleAnimeSource
    val toggleMangaSource: ToggleMangaSource
    val toggleAnimeIncognito: ToggleAnimeIncognito
    val toggleMangaIncognito: ToggleMangaIncognito
    val getAnimeSourcesWithFavoriteCount: GetAnimeSourcesWithFavoriteCount
    val getMangaSourcesWithFavoriteCount: GetMangaSourcesWithFavoriteCount
    val getAnimeIncognitoState: GetAnimeIncognitoState
    val getMangaIncognitoState: GetMangaIncognitoState
    val getLanguagesWithAnimeSources: GetLanguagesWithAnimeSources
    val getLanguagesWithMangaSources: GetLanguagesWithMangaSources
    val getEnabledAnimeSources: GetEnabledAnimeSources
    val getEnabledMangaSources: GetEnabledMangaSources
    val toggleLanguage: ToggleLanguage
    val setMigrateSorting: SetMigrateSorting
    val insertSavedSearch: InsertSavedSearch
    val getAnimeExtensionSources: GetAnimeExtensionSources
    val getMangaExtensionSources: GetMangaExtensionSources
    val getAnimeExtensionsByType: GetAnimeExtensionsByType
    val getMangaExtensionsByType: GetMangaExtensionsByType
    val getAnimeExtensionLanguages: GetAnimeExtensionLanguages
    val getMangaExtensionLanguages: GetMangaExtensionLanguages
    val getAnimeUpdates: GetAnimeUpdates
    val getMangaUpdates: GetMangaUpdates
    val getApplicationRelease: GetApplicationRelease
    val toggleExcludeFromMangaDataSaver: ToggleExcludeFromMangaDataSaver

    val getMangaExtensionStoreCountAsFlow: GetMangaExtensionStoreCountAsFlow
    val getAnimeExtensionStoreCountAsFlow: GetAnimeExtensionStoreCountAsFlow
    val getMangaExtensionStores: GetMangaExtensionStores
    val getAnimeExtensionStores: GetAnimeExtensionStores

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context, @Provides @IsDebugBuild isDebugBuild: Boolean): AppGraph
    }
}
