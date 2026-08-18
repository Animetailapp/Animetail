package mihon.app.di.injekt

import animetail.feature.mpvfiles.MpvConfig
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import dev.zacsweers.metro.Inject
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
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.domain.track.manga.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.manga.interactor.TrackChapter
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.LocalHttpServerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import nl.adaptivity.xmlutil.serialization.XML
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
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import eu.kanade.domain.extension.manga.interactor.GetExtensionSources as GetMangaExtensionSources

@Inject
class MetroInteropModule(
    private val json: Json,
    private val protoBuf: ProtoBuf,
    private val xml: XML,

    private val networkHelper: NetworkHelper,
    private val javaScriptEngine: JavaScriptEngine,

    private val preferenceStore: PreferenceStore,
    private val basePreferences: BasePreferences,
    private val uiPreferences: UiPreferences,
    private val readerPreferences: ReaderPreferences,
    private val playerPreferences: PlayerPreferences,
    private val gesturePreferences: GesturePreferences,
    private val advancedPlayerPreferences: AdvancedPlayerPreferences,
    private val decoderPreferences: DecoderPreferences,
    private val audioPreferences: AudioPreferences,
    private val subtitlePreferences: SubtitlePreferences,
    private val torrentPreferences: TorrentPreferences,
    private val connectionsPreferences: ConnectionsPreferences,
    private val syncPreferences: SyncPreferences,
    private val networkPreferences: NetworkPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val sourcePreferences: SourcePreferences,
    private val trackPreferences: TrackPreferences,
    private val backupPreferences: BackupPreferences,
    private val storagePreferences: StoragePreferences,
    private val privacyPreferences: PrivacyPreferences,
    private val securityPreferences: SecurityPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val storageManager: StorageManager,
    private val localHttpServerHolder: LocalHttpServerHolder,
    private val torrentServerApi: TorrentServerApi,
    private val torrentServerUtils: TorrentServerUtils,

    private val connectionsManager: ConnectionsManager,

    private val coroutineScope: CoroutineScope,

    private val getMangaCategories: GetMangaCategories,
    private val getAnimeCategories: GetAnimeCategories,

    private val getEpisode: GetEpisode,
    private val getChapter: GetChapter,
    private val getAnime: GetAnime,
    private val getManga: GetManga,

    private val getCustomButtons: GetCustomButtons,
    private val createCustomButton: CreateCustomButton,
    private val deleteCustomButton: DeleteCustomButton,
    private val updateCustomButton: UpdateCustomButton,
    private val reorderCustomButton: ReorderCustomButton,
    private val toggleFavoriteCustomButton: ToggleFavoriteCustomButton,
    private val mpvConfig: MpvConfig,

    private val getAnimeHistory: GetAnimeHistory,
    private val getMangaHistory: GetMangaHistory,
    private val getLibraryAnime: GetLibraryAnime,
    private val getLibraryManga: GetLibraryManga,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val networkToLocalManga: NetworkToLocalManga,
    private val getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal,
    private val getFeedSavedSearchBySourceId: GetFeedSavedSearchBySourceId,
    private val getSavedSearchGlobalFeed: GetSavedSearchGlobalFeed,
    private val getSavedSearchBySourceIdFeed: GetSavedSearchBySourceIdFeed,
    private val countFeedSavedSearchGlobal: CountFeedSavedSearchGlobal,
    private val countFeedSavedSearchBySourceId: CountFeedSavedSearchBySourceId,
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId,
    private val insertFeedSavedSearch: InsertFeedSavedSearch,
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById,
    private val reorderFeed: ReorderFeed,
    private val getExhSavedSearch: GetExhSavedSearch,
    private val getVisibleAnimeCategories: GetVisibleAnimeCategories,
    private val getVisibleMangaCategories: GetVisibleMangaCategories,
    private val getTracksPerAnime: GetTracksPerAnime,
    private val getTracksPerManga: GetTracksPerManga,
    private val getNextEpisodes: GetNextEpisodes,
    private val getNextChapters: GetNextChapters,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val setSeenStatus: SetSeenStatus,
    private val setReadStatus: SetReadStatus,
    private val updateAnime: UpdateAnime,
    private val updateManga: UpdateManga,
    private val setAnimeCategories: SetAnimeCategories,
    private val setMangaCategories: SetMangaCategories,
    private val removeAnimeHistory: RemoveAnimeHistory,
    private val removeMangaHistory: RemoveMangaHistory,
    private val getDuplicateLibraryAnime: GetDuplicateLibraryAnime,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getMangaTracks: GetMangaTracks,
    private val getAnimeTracks: GetAnimeTracks,
    private val refreshAnimeTracks: RefreshAnimeTracks,
    private val refreshMangaTracks: RefreshMangaTracks,

    private val createAnimeCategoryWithName: CreateAnimeCategoryWithName,
    private val deleteAnimeCategory: DeleteAnimeCategory,
    private val hideAnimeCategory: HideAnimeCategory,
    private val renameAnimeCategory: RenameAnimeCategory,
    private val reorderAnimeCategory: ReorderAnimeCategory,
    private val setAnimeDisplayMode: SetAnimeDisplayMode,
    private val setSortModeForAnimeCategory: SetSortModeForAnimeCategory,

    private val createMangaCategoryWithName: CreateMangaCategoryWithName,
    private val deleteMangaCategory: DeleteMangaCategory,
    private val hideMangaCategory: HideMangaCategory,
    private val renameMangaCategory: RenameMangaCategory,
    private val reorderMangaCategory: ReorderMangaCategory,
    private val setMangaDisplayMode: SetMangaDisplayMode,
    private val setSortModeForMangaCategory: SetSortModeForMangaCategory,

    private val deleteAnimeTrack: DeleteAnimeTrack,
    private val deleteMangaTrack: DeleteMangaTrack,
    private val trackEpisode: TrackEpisode,
    private val trackChapter: TrackChapter,
    private val syncEpisodeProgressWithTrack: SyncEpisodeProgressWithTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    private val delayedMangaTrackingStore: DelayedMangaTrackingStore,

    private val updateAnimeNotes: UpdateAnimeNotes,
    private val updateMangaNotes: UpdateMangaNotes,
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
    private val getEpisodeByUrlAndAnimeId: GetEpisodeByUrlAndAnimeId,
    private val getChapterByUrlAndMangaId: GetChapterByUrlAndMangaId,
    private val updateAnimeFromRemote: UpdateAnimeFromRemote,
    private val imageSaver: ImageSaver,
    private val setAnimeViewerFlags: SetAnimeViewerFlags,
    private val setMangaViewerFlags: SetMangaViewerFlags,
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags,
    private val setMangaChapterFlags: SetMangaChapterFlags,
    private val setAnimeSeasonFlags: SetAnimeSeasonFlags,
    private val setCustomAnimeInfo: SetCustomAnimeInfo,
    private val setCustomMangaInfo: SetCustomMangaInfo,
    private val getCustomAnimeInfo: GetCustomAnimeInfo,
    private val getCustomMangaInfo: GetCustomMangaInfo,
    private val getAnimeWithEpisodesAndSeasons: GetAnimeWithEpisodesAndSeasons,
    private val getMangaWithChapters: GetMangaWithChapters,
    private val animeFetchInterval: AnimeFetchInterval,
    private val mangaFetchInterval: MangaFetchInterval,

    private val getExcludedScanlators: GetExcludedScanlators,
    private val setExcludedScanlators: SetExcludedScanlators,
    private val getAvailableScanlators: GetAvailableScanlators,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val syncSeasonsWithSource: SyncSeasonsWithSource,
    private val updateChapter: UpdateChapter,
    private val setAnimeDefaultEpisodeFlags: SetAnimeDefaultEpisodeFlags,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    private val setAnimeDefaultSeasonFlags: SetAnimeDefaultSeasonFlags,
    private val getAnimeSeasonsByParentId: GetAnimeSeasonsByParentId,
    private val getBookmarkedChaptersByMangaId: GetBookmarkedChaptersByMangaId,

    private val toggleAnimeSourcePin: ToggleAnimeSourcePin,
    private val toggleMangaSourcePin: ToggleMangaSourcePin,
    private val toggleAnimeSource: ToggleAnimeSource,
    private val toggleMangaSource: ToggleMangaSource,
    private val toggleAnimeIncognito: ToggleAnimeIncognito,
    private val toggleMangaIncognito: ToggleMangaIncognito,
    private val getAnimeSourcesWithFavoriteCount: GetAnimeSourcesWithFavoriteCount,
    private val getMangaSourcesWithFavoriteCount: GetMangaSourcesWithFavoriteCount,
    private val getAnimeIncognitoState: GetAnimeIncognitoState,
    private val getMangaIncognitoState: GetMangaIncognitoState,
    private val getLanguagesWithAnimeSources: GetLanguagesWithAnimeSources,
    private val getLanguagesWithMangaSources: GetLanguagesWithMangaSources,
    private val getEnabledAnimeSources: GetEnabledAnimeSources,
    private val getEnabledMangaSources: GetEnabledMangaSources,
    private val toggleLanguage: ToggleLanguage,
    private val setMigrateSorting: SetMigrateSorting,
    private val insertSavedSearch: InsertSavedSearch,
    private val getAnimeExtensionSources: GetAnimeExtensionSources,
    private val getMangaExtensionSources: GetMangaExtensionSources,
    private val getAnimeExtensionsByType: GetAnimeExtensionsByType,
    private val getMangaExtensionsByType: GetMangaExtensionsByType,
    private val getAnimeExtensionLanguages: GetAnimeExtensionLanguages,
    private val getMangaExtensionLanguages: GetMangaExtensionLanguages,
    private val getAnimeUpdates: GetAnimeUpdates,
    private val getMangaUpdates: GetMangaUpdates,
    private val getApplicationRelease: GetApplicationRelease,
    private val toggleExcludeFromMangaDataSaver: ToggleExcludeFromMangaDataSaver,

    private val mangaExtensionManager: MangaExtensionManager,
    private val animeExtensionManager: AnimeExtensionManager,

    private val trackerManager: TrackerManager,
    private val syncManager: SyncManager,
    private val updateChecker: AppUpdateChecker,

    private val mangaCoverCache: MangaCoverCache,
    private val animeCoverCache: AnimeCoverCache,
    private val animeBackgroundCache: AnimeBackgroundCache,
    private val chapterCache: ChapterCache,

    private val mangaSourceManager: MangaSourceManager,
    private val animeSourceManager: AnimeSourceManager,

    private val mangaDownloadManager: MangaDownloadManager,
    private val animeDownloadManager: AnimeDownloadManager,

    private val mangaDownloadCache: MangaDownloadCache,
    private val animeDownloadCache: AnimeDownloadCache,
) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(json)
        addSingleton(protoBuf)
        addSingleton(xml)

        addSingleton(networkHelper)
        addSingleton(javaScriptEngine)

        addSingleton(preferenceStore)
        addSingleton(basePreferences)
        addSingleton(uiPreferences)
        addSingleton(readerPreferences)
        addSingleton(playerPreferences)
        addSingleton(gesturePreferences)
        addSingleton(advancedPlayerPreferences)
        addSingleton(decoderPreferences)
        addSingleton(audioPreferences)
        addSingleton(subtitlePreferences)
        addSingleton(torrentPreferences)
        addSingleton(connectionsPreferences)
        addSingleton(syncPreferences)
        addSingleton(networkPreferences)
        addSingleton(libraryPreferences)
        addSingleton(sourcePreferences)
        addSingleton(trackPreferences)
        addSingleton(backupPreferences)
        addSingleton(storagePreferences)
        addSingleton(privacyPreferences)
        addSingleton(securityPreferences)
        addSingleton(downloadPreferences)
        addSingleton(storageManager)
        addSingleton(localHttpServerHolder)
        addSingleton(torrentServerApi)
        addSingleton(torrentServerUtils)

        addSingleton(connectionsManager)

        addSingleton(coroutineScope)

        addSingleton(getMangaCategories)
        addSingleton(getAnimeCategories)

        addSingleton(getEpisode)
        addSingleton(getChapter)
        addSingleton(getAnime)
        addSingleton(getManga)

        addSingleton(getCustomButtons)
        addSingleton(createCustomButton)
        addSingleton(deleteCustomButton)
        addSingleton(updateCustomButton)
        addSingleton(reorderCustomButton)
        addSingleton(toggleFavoriteCustomButton)
        addSingleton(mpvConfig)

        addSingleton(getAnimeHistory)
        addSingleton(getMangaHistory)
        addSingleton(getLibraryAnime)
        addSingleton(getLibraryManga)
        addSingleton(networkToLocalAnime)
        addSingleton(networkToLocalManga)
        addSingleton(getFeedSavedSearchGlobal)
        addSingleton(getFeedSavedSearchBySourceId)
        addSingleton(getSavedSearchGlobalFeed)
        addSingleton(getSavedSearchBySourceIdFeed)
        addSingleton(countFeedSavedSearchGlobal)
        addSingleton(countFeedSavedSearchBySourceId)
        addSingleton(getSavedSearchBySourceId)
        addSingleton(insertFeedSavedSearch)
        addSingleton(deleteFeedSavedSearchById)
        addSingleton(reorderFeed)
        addSingleton(getExhSavedSearch)
        addSingleton(getVisibleAnimeCategories)
        addSingleton(getVisibleMangaCategories)
        addSingleton(getTracksPerAnime)
        addSingleton(getTracksPerManga)
        addSingleton(getNextEpisodes)
        addSingleton(getNextChapters)
        addSingleton(getChaptersByMangaId)
        addSingleton(setSeenStatus)
        addSingleton(setReadStatus)
        addSingleton(updateAnime)
        addSingleton(updateManga)
        addSingleton(setAnimeCategories)
        addSingleton(setMangaCategories)
        addSingleton(removeAnimeHistory)
        addSingleton(removeMangaHistory)
        addSingleton(getDuplicateLibraryAnime)
        addSingleton(getDuplicateLibraryManga)
        addSingleton(getMangaTracks)
        addSingleton(getAnimeTracks)
        addSingleton(refreshAnimeTracks)
        addSingleton(refreshMangaTracks)

        addSingleton(createAnimeCategoryWithName)
        addSingleton(deleteAnimeCategory)
        addSingleton(hideAnimeCategory)
        addSingleton(renameAnimeCategory)
        addSingleton(reorderAnimeCategory)
        addSingleton(setAnimeDisplayMode)
        addSingleton(setSortModeForAnimeCategory)

        addSingleton(createMangaCategoryWithName)
        addSingleton(deleteMangaCategory)
        addSingleton(hideMangaCategory)
        addSingleton(renameMangaCategory)
        addSingleton(reorderMangaCategory)
        addSingleton(setMangaDisplayMode)
        addSingleton(setSortModeForMangaCategory)

        addSingleton(deleteAnimeTrack)
        addSingleton(deleteMangaTrack)
        addSingleton(trackEpisode)
        addSingleton(trackChapter)
        addSingleton(syncEpisodeProgressWithTrack)
        addSingleton(syncChapterProgressWithTrack)
        addSingleton(delayedMangaTrackingStore)

        addSingleton(updateAnimeNotes)
        addSingleton(updateMangaNotes)
        addSingleton(getAnimeByUrlAndSourceId)
        addSingleton(getMangaByUrlAndSourceId)
        addSingleton(getEpisodeByUrlAndAnimeId)
        addSingleton(getChapterByUrlAndMangaId)
        addSingleton(updateAnimeFromRemote)
        addSingleton(imageSaver)
        addSingleton(setAnimeViewerFlags)
        addSingleton(setMangaViewerFlags)
        addSingleton(setAnimeEpisodeFlags)
        addSingleton(setMangaChapterFlags)
        addSingleton(setAnimeSeasonFlags)
        addSingleton(setCustomAnimeInfo)
        addSingleton(setCustomMangaInfo)
        addSingleton(getCustomAnimeInfo)
        addSingleton(getCustomMangaInfo)
        addSingleton(getAnimeWithEpisodesAndSeasons)
        addSingleton(getMangaWithChapters)
        addSingleton(animeFetchInterval)
        addSingleton(mangaFetchInterval)

        addSingleton(getExcludedScanlators)
        addSingleton(setExcludedScanlators)
        addSingleton(getAvailableScanlators)
        addSingleton(syncChaptersWithSource)
        addSingleton(syncEpisodesWithSource)
        addSingleton(syncSeasonsWithSource)
        addSingleton(updateChapter)
        addSingleton(setAnimeDefaultEpisodeFlags)
        addSingleton(setMangaDefaultChapterFlags)
        addSingleton(setAnimeDefaultSeasonFlags)
        addSingleton(getAnimeSeasonsByParentId)
        addSingleton(getBookmarkedChaptersByMangaId)

        addSingleton(toggleAnimeSourcePin)
        addSingleton(toggleMangaSourcePin)
        addSingleton(toggleAnimeSource)
        addSingleton(toggleMangaSource)
        addSingleton(toggleAnimeIncognito)
        addSingleton(toggleMangaIncognito)
        addSingleton(getAnimeSourcesWithFavoriteCount)
        addSingleton(getMangaSourcesWithFavoriteCount)
        addSingleton(getAnimeIncognitoState)
        addSingleton(getMangaIncognitoState)
        addSingleton(getLanguagesWithAnimeSources)
        addSingleton(getLanguagesWithMangaSources)
        addSingleton(getEnabledAnimeSources)
        addSingleton(getEnabledMangaSources)
        addSingleton(toggleLanguage)
        addSingleton(setMigrateSorting)
        addSingleton(insertSavedSearch)
        addSingleton(getAnimeExtensionSources)
        addSingleton(getMangaExtensionSources)
        addSingleton(getAnimeExtensionsByType)
        addSingleton(getMangaExtensionsByType)
        addSingleton(getAnimeExtensionLanguages)
        addSingleton(getMangaExtensionLanguages)
        addSingleton(getAnimeUpdates)
        addSingleton(getMangaUpdates)
        addSingleton(getApplicationRelease)
        addSingleton(toggleExcludeFromMangaDataSaver)

        addSingleton(mangaExtensionManager)
        addSingleton(animeExtensionManager)

        addSingleton(trackerManager)
        addSingleton(syncManager)
        addSingleton(updateChecker)

        addSingleton(mangaCoverCache)
        addSingleton(animeCoverCache)
        addSingleton(animeBackgroundCache)
        addSingleton(chapterCache)

        addSingleton(mangaSourceManager)
        addSingleton(animeSourceManager)

        addSingleton(mangaDownloadManager)
        addSingleton(animeDownloadManager)

        addSingleton(mangaDownloadCache)
        addSingleton(animeDownloadCache)
    }
}
