package mihon.app.di.injekt

import android.app.Application
import android.content.Context
import animetail.feature.mpvfiles.MpvConfig
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
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
import eu.kanade.domain.extension.manga.interactor.GetExtensionSources as GetMangaExtensionSources
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
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
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
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
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
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.LocalHttpServerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.app.di.AppGraph
import mihon.core.metro.GraphProvider
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStoreCountAsFlow
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStores
import mihon.domain.extension.manga.interactor.GetMangaExtensionStoreCountAsFlow
import mihon.domain.extension.manga.interactor.GetMangaExtensionStores
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
import tachiyomi.domain.track.anime.interactor.UpsertAnimeTrack
import tachiyomi.domain.track.manga.interactor.DeleteMangaTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.interactor.UpsertMangaTrack
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.InjektionException
import uy.kohesive.injekt.api.TypeReference
import java.lang.reflect.Type

class MetroInjektRegistrar(
    private val application: Application,
    private val graphProvider: GraphProvider<AppGraph>,
) : InjektRegistrar {

    private val graph: AppGraph inline get() = graphProvider.graph

    private val bindings = mapOf<Type, () -> Any>(
        Application::class.java to { application },
        Context::class.java to { application },

        Json::class.java to { graph.json },
        ProtoBuf::class.java to { graph.protoBuf },
        XML::class.java to { graph.xml },

        NetworkHelper::class.java to { graph.networkHelper },
        JavaScriptEngine::class.java to { graph.javaScriptEngine },

        PreferenceStore::class.java to { graph.preferenceStore },
        BasePreferences::class.java to { graph.basePreferences },
        UiPreferences::class.java to { graph.uiPreferences },
        ReaderPreferences::class.java to { graph.readerPreferences },
        PlayerPreferences::class.java to { graph.playerPreferences },
        GesturePreferences::class.java to { graph.gesturePreferences },
        AdvancedPlayerPreferences::class.java to { graph.advancedPlayerPreferences },
        DecoderPreferences::class.java to { graph.decoderPreferences },
        AudioPreferences::class.java to { graph.audioPreferences },
        SubtitlePreferences::class.java to { graph.subtitlePreferences },
        TorrentPreferences::class.java to { graph.torrentPreferences },
        ConnectionsPreferences::class.java to { graph.connectionsPreferences },
        SyncPreferences::class.java to { graph.syncPreferences },
        NetworkPreferences::class.java to { graph.networkPreferences },
        LibraryPreferences::class.java to { graph.libraryPreferences },
        SourcePreferences::class.java to { graph.sourcePreferences },
        TrackPreferences::class.java to { graph.trackPreferences },
        BackupPreferences::class.java to { graph.backupPreferences },
        StoragePreferences::class.java to { graph.storagePreferences },
        PrivacyPreferences::class.java to { graph.privacyPreferences },
        SecurityPreferences::class.java to { graph.securityPreferences },
        DownloadPreferences::class.java to { graph.downloadPreferences },

        StorageManager::class.java to { graph.storageManager },
        LocalHttpServerHolder::class.java to { graph.localHttpServerHolder },
        TorrentServerApi::class.java to { graph.torrentServerApi },
        TorrentServerUtils::class.java to { graph.torrentServerUtils },
        CrashLogUtil::class.java to { graph.crashLogUtil },
        MpvConfig::class.java to { graph.mpvConfig },

        MangaDownloadManager::class.java to { graph.mangaDownloadManager },
        AnimeDownloadManager::class.java to { graph.animeDownloadManager },
        MangaDownloadProvider::class.java to { graph.mangaDownloadProvider },
        AnimeDownloadProvider::class.java to { graph.animeDownloadProvider },

        AppUpdateChecker::class.java to { graph.updateChecker },
        SyncManager::class.java to { graph.syncManager },

        TrustMangaExtension::class.java to { graph.trustMangaExtension },
        TrustAnimeExtension::class.java to { graph.trustAnimeExtension },

        MangaSourceManager::class.java to { graph.mangaSourceManager },
        AnimeSourceManager::class.java to { graph.animeSourceManager },
        TrackerManager::class.java to { graph.trackerManager },
        ConnectionsManager::class.java to { graph.connectionsManager },
        MangaExtensionManager::class.java to { graph.mangaExtensionManager },
        AnimeExtensionManager::class.java to { graph.animeExtensionManager },
        ChapterCache::class.java to { graph.chapterCache },
        MangaDownloadCache::class.java to { graph.mangaDownloadCache },
        AnimeDownloadCache::class.java to { graph.animeDownloadCache },
        MangaCoverCache::class.java to { graph.mangaCoverCache },
        AnimeCoverCache::class.java to { graph.animeCoverCache },
        AnimeBackgroundCache::class.java to { graph.animeBackgroundCache },

        LocalMangaCoverManager::class.java to { graph.localMangaCoverManager },
        LocalAnimeCoverManager::class.java to { graph.localAnimeCoverManager },
        LocalAnimeBackgroundManager::class.java to { graph.localAnimeBackgroundManager },
        LocalEpisodeThumbnailManager::class.java to { graph.localEpisodeThumbnailManager },

        CoroutineScope::class.java to { graph.coroutineScope },

        GetManga::class.java to { graph.getManga },
        GetAnime::class.java to { graph.getAnime },
        GetMangaFavorites::class.java to { graph.getMangaFavorites },
        GetAnimeFavorites::class.java to { graph.getAnimeFavorites },
        GetMangaCategories::class.java to { graph.getMangaCategories },
        GetAnimeCategories::class.java to { graph.getAnimeCategories },
        ResetMangaViewerFlags::class.java to { graph.resetMangaViewerFlags },
        ResetAnimeViewerFlags::class.java to { graph.resetAnimeViewerFlags },
        ResetMangaCategoryFlags::class.java to { graph.resetMangaCategoryFlags },
        ResetAnimeCategoryFlags::class.java to { graph.resetAnimeCategoryFlags },
        AddMangaTracks::class.java to { graph.addMangaTracks },
        AddAnimeTracks::class.java to { graph.addAnimeTracks },
        UpsertMangaTrack::class.java to { graph.upsertMangaTrack },
        InsertMangaTrack::class.java to { graph.insertMangaTrack },
        UpsertAnimeTrack::class.java to { graph.upsertAnimeTrack },
        InsertAnimeTrack::class.java to { graph.insertAnimeTrack },
        UpsertAnimeHistory::class.java to { graph.upsertAnimeHistory },
        UpdateEpisode::class.java to { graph.updateEpisode },
        GetEpisodesByAnimeId::class.java to { graph.getEpisodesByAnimeId },
        GetAnimeTracks::class.java to { graph.getAnimeTracks },
        DelayedAnimeTrackingStore::class.java to { graph.delayedAnimeTrackingStore },
        GetEpisode::class.java to { graph.getEpisode },
        GetChapter::class.java to { graph.getChapter },
        GetCustomButtons::class.java to { graph.getCustomButtons },
        CreateCustomButton::class.java to { graph.createCustomButton },
        DeleteCustomButton::class.java to { graph.deleteCustomButton },
        UpdateCustomButton::class.java to { graph.updateCustomButton },
        ReorderCustomButton::class.java to { graph.reorderCustomButton },
        ToggleFavoriteCustomButton::class.java to { graph.toggleFavoriteCustomButton },

        GetAnimeHistory::class.java to { graph.getAnimeHistory },
        GetMangaHistory::class.java to { graph.getMangaHistory },
        GetLibraryAnime::class.java to { graph.getLibraryAnime },
        GetLibraryManga::class.java to { graph.getLibraryManga },
        NetworkToLocalAnime::class.java to { graph.networkToLocalAnime },
        NetworkToLocalManga::class.java to { graph.networkToLocalManga },
        GetFeedSavedSearchGlobal::class.java to { graph.getFeedSavedSearchGlobal },
        GetFeedSavedSearchBySourceId::class.java to { graph.getFeedSavedSearchBySourceId },
        GetSavedSearchGlobalFeed::class.java to { graph.getSavedSearchGlobalFeed },
        GetSavedSearchBySourceIdFeed::class.java to { graph.getSavedSearchBySourceIdFeed },
        CountFeedSavedSearchGlobal::class.java to { graph.countFeedSavedSearchGlobal },
        CountFeedSavedSearchBySourceId::class.java to { graph.countFeedSavedSearchBySourceId },
        GetSavedSearchBySourceId::class.java to { graph.getSavedSearchBySourceId },
        InsertFeedSavedSearch::class.java to { graph.insertFeedSavedSearch },
        DeleteFeedSavedSearchById::class.java to { graph.deleteFeedSavedSearchById },
        ReorderFeed::class.java to { graph.reorderFeed },
        GetExhSavedSearch::class.java to { graph.getExhSavedSearch },
        GetVisibleAnimeCategories::class.java to { graph.getVisibleAnimeCategories },
        GetVisibleMangaCategories::class.java to { graph.getVisibleMangaCategories },
        GetTracksPerAnime::class.java to { graph.getTracksPerAnime },
        GetTracksPerManga::class.java to { graph.getTracksPerManga },
        GetNextEpisodes::class.java to { graph.getNextEpisodes },
        GetNextChapters::class.java to { graph.getNextChapters },
        GetChaptersByMangaId::class.java to { graph.getChaptersByMangaId },
        SetSeenStatus::class.java to { graph.setSeenStatus },
        SetReadStatus::class.java to { graph.setReadStatus },
        UpdateAnime::class.java to { graph.updateAnime },
        UpdateManga::class.java to { graph.updateManga },
        SetAnimeCategories::class.java to { graph.setAnimeCategories },
        SetMangaCategories::class.java to { graph.setMangaCategories },
        RemoveAnimeHistory::class.java to { graph.removeAnimeHistory },
        RemoveMangaHistory::class.java to { graph.removeMangaHistory },
        GetDuplicateLibraryAnime::class.java to { graph.getDuplicateLibraryAnime },
        GetDuplicateLibraryManga::class.java to { graph.getDuplicateLibraryManga },
        GetMangaTracks::class.java to { graph.getMangaTracks },
        RefreshAnimeTracks::class.java to { graph.refreshAnimeTracks },
        RefreshMangaTracks::class.java to { graph.refreshMangaTracks },

        CreateAnimeCategoryWithName::class.java to { graph.createAnimeCategoryWithName },
        DeleteAnimeCategory::class.java to { graph.deleteAnimeCategory },
        HideAnimeCategory::class.java to { graph.hideAnimeCategory },
        RenameAnimeCategory::class.java to { graph.renameAnimeCategory },
        ReorderAnimeCategory::class.java to { graph.reorderAnimeCategory },
        SetAnimeDisplayMode::class.java to { graph.setAnimeDisplayMode },
        SetSortModeForAnimeCategory::class.java to { graph.setSortModeForAnimeCategory },

        CreateMangaCategoryWithName::class.java to { graph.createMangaCategoryWithName },
        DeleteMangaCategory::class.java to { graph.deleteMangaCategory },
        HideMangaCategory::class.java to { graph.hideMangaCategory },
        RenameMangaCategory::class.java to { graph.renameMangaCategory },
        ReorderMangaCategory::class.java to { graph.reorderMangaCategory },
        SetMangaDisplayMode::class.java to { graph.setMangaDisplayMode },
        SetSortModeForMangaCategory::class.java to { graph.setSortModeForMangaCategory },

        DeleteAnimeTrack::class.java to { graph.deleteAnimeTrack },
        DeleteMangaTrack::class.java to { graph.deleteMangaTrack },
        TrackEpisode::class.java to { graph.trackEpisode },
        TrackChapter::class.java to { graph.trackChapter },
        SyncEpisodeProgressWithTrack::class.java to { graph.syncEpisodeProgressWithTrack },
        SyncChapterProgressWithTrack::class.java to { graph.syncChapterProgressWithTrack },
        DelayedMangaTrackingStore::class.java to { graph.delayedMangaTrackingStore },

        UpdateAnimeNotes::class.java to { graph.updateAnimeNotes },
        UpdateMangaNotes::class.java to { graph.updateMangaNotes },
        GetAnimeByUrlAndSourceId::class.java to { graph.getAnimeByUrlAndSourceId },
        GetMangaByUrlAndSourceId::class.java to { graph.getMangaByUrlAndSourceId },
        GetEpisodeByUrlAndAnimeId::class.java to { graph.getEpisodeByUrlAndAnimeId },
        GetChapterByUrlAndMangaId::class.java to { graph.getChapterByUrlAndMangaId },
        UpdateAnimeFromRemote::class.java to { graph.updateAnimeFromRemote },
        ImageSaver::class.java to { graph.imageSaver },
        SetAnimeViewerFlags::class.java to { graph.setAnimeViewerFlags },
        SetMangaViewerFlags::class.java to { graph.setMangaViewerFlags },
        SetAnimeEpisodeFlags::class.java to { graph.setAnimeEpisodeFlags },
        SetMangaChapterFlags::class.java to { graph.setMangaChapterFlags },
        SetAnimeSeasonFlags::class.java to { graph.setAnimeSeasonFlags },
        SetCustomAnimeInfo::class.java to { graph.setCustomAnimeInfo },
        SetCustomMangaInfo::class.java to { graph.setCustomMangaInfo },
        GetCustomAnimeInfo::class.java to { graph.getCustomAnimeInfo },
        GetCustomMangaInfo::class.java to { graph.getCustomMangaInfo },
        GetAnimeWithEpisodesAndSeasons::class.java to { graph.getAnimeWithEpisodesAndSeasons },
        GetMangaWithChapters::class.java to { graph.getMangaWithChapters },
        AnimeFetchInterval::class.java to { graph.animeFetchInterval },
        MangaFetchInterval::class.java to { graph.mangaFetchInterval },

        GetExcludedScanlators::class.java to { graph.getExcludedScanlators },
        SetExcludedScanlators::class.java to { graph.setExcludedScanlators },
        GetAvailableScanlators::class.java to { graph.getAvailableScanlators },
        SyncChaptersWithSource::class.java to { graph.syncChaptersWithSource },
        SyncEpisodesWithSource::class.java to { graph.syncEpisodesWithSource },
        SyncSeasonsWithSource::class.java to { graph.syncSeasonsWithSource },
        UpdateChapter::class.java to { graph.updateChapter },
        SetAnimeDefaultEpisodeFlags::class.java to { graph.setAnimeDefaultEpisodeFlags },
        SetMangaDefaultChapterFlags::class.java to { graph.setMangaDefaultChapterFlags },
        SetAnimeDefaultSeasonFlags::class.java to { graph.setAnimeDefaultSeasonFlags },
        GetAnimeSeasonsByParentId::class.java to { graph.getAnimeSeasonsByParentId },
        GetBookmarkedChaptersByMangaId::class.java to { graph.getBookmarkedChaptersByMangaId },

        ToggleAnimeSourcePin::class.java to { graph.toggleAnimeSourcePin },
        ToggleMangaSourcePin::class.java to { graph.toggleMangaSourcePin },
        ToggleAnimeSource::class.java to { graph.toggleAnimeSource },
        ToggleMangaSource::class.java to { graph.toggleMangaSource },
        ToggleAnimeIncognito::class.java to { graph.toggleAnimeIncognito },
        ToggleMangaIncognito::class.java to { graph.toggleMangaIncognito },
        GetAnimeSourcesWithFavoriteCount::class.java to { graph.getAnimeSourcesWithFavoriteCount },
        GetMangaSourcesWithFavoriteCount::class.java to { graph.getMangaSourcesWithFavoriteCount },
        GetAnimeIncognitoState::class.java to { graph.getAnimeIncognitoState },
        GetMangaIncognitoState::class.java to { graph.getMangaIncognitoState },
        GetLanguagesWithAnimeSources::class.java to { graph.getLanguagesWithAnimeSources },
        GetLanguagesWithMangaSources::class.java to { graph.getLanguagesWithMangaSources },
        GetEnabledAnimeSources::class.java to { graph.getEnabledAnimeSources },
        GetEnabledMangaSources::class.java to { graph.getEnabledMangaSources },
        ToggleLanguage::class.java to { graph.toggleLanguage },
        SetMigrateSorting::class.java to { graph.setMigrateSorting },
        InsertSavedSearch::class.java to { graph.insertSavedSearch },
        GetAnimeExtensionSources::class.java to { graph.getAnimeExtensionSources },
        GetMangaExtensionSources::class.java to { graph.getMangaExtensionSources },
        GetAnimeExtensionsByType::class.java to { graph.getAnimeExtensionsByType },
        GetMangaExtensionsByType::class.java to { graph.getMangaExtensionsByType },
        GetAnimeExtensionLanguages::class.java to { graph.getAnimeExtensionLanguages },
        GetMangaExtensionLanguages::class.java to { graph.getMangaExtensionLanguages },
        GetAnimeUpdates::class.java to { graph.getAnimeUpdates },
        GetMangaUpdates::class.java to { graph.getMangaUpdates },
        GetApplicationRelease::class.java to { graph.getApplicationRelease },
        ToggleExcludeFromMangaDataSaver::class.java to { graph.toggleExcludeFromMangaDataSaver },

        GetMangaExtensionStoreCountAsFlow::class.java to { graph.getMangaExtensionStoreCountAsFlow },
        GetAnimeExtensionStoreCountAsFlow::class.java to { graph.getAnimeExtensionStoreCountAsFlow },
        GetMangaExtensionStores::class.java to { graph.getMangaExtensionStores },
        GetAnimeExtensionStores::class.java to { graph.getAnimeExtensionStores },
    )

    override fun <R : Any> getInstance(forType: Type): R = getInstanceOrNull(forType)
        ?: throw InjektionException("$forType is not exposed to Injekt, add it to ${this::class.simpleName}")

    override fun <R : Any> getInstanceOrElse(forType: Type, default: R): R = getInstanceOrNull(forType) ?: default

    override fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R =
        getInstanceOrNull(forType) ?: default()

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstanceOrNull(forType: Type): R? = bindings[forType]?.invoke() as R?

    override fun <R : Any, K : Any> getKeyedInstance(forType: Type, key: K): R = unsupported("Keyed injection")

    override fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: R): R =
        unsupported("Keyed injection")

    override fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: () -> R): R =
        unsupported("Keyed injection")

    @Suppress("RedundantNullableReturnType")
    override fun <R : Any, K : Any> getKeyedInstanceOrNull(forType: Type, key: K): R? = unsupported("Keyed injection")

    override fun <R : Any> getLogger(expectedLoggerType: Type, byName: String): R = unsupported("Logger injection")

    override fun <R : Any, T : Any> getLogger(expectedLoggerType: Type, forClass: Class<T>): R =
        unsupported("Logger injection")

    override fun <T : Any> addSingleton(forType: TypeReference<T>, singleInstance: T) = readOnly()

    override fun <R : Any> addSingletonFactory(forType: TypeReference<R>, factoryCalledOnce: () -> R) = readOnly()

    override fun <R : Any> addFactory(forType: TypeReference<R>, factoryCalledEveryTime: () -> R) = readOnly()

    override fun <R : Any> addPerThreadFactory(forType: TypeReference<R>, factoryCalledOncePerThread: () -> R) =
        readOnly()

    override fun <R : Any, K : Any> addPerKeyFactory(forType: TypeReference<R>, factoryCalledPerKey: (K) -> R) =
        readOnly()

    override fun <R : Any, K : Any> addPerThreadPerKeyFactory(
        forType: TypeReference<R>,
        factoryCalledPerKeyPerThread: (K) -> R,
    ) = readOnly()

    override fun <R : Any> addLoggerFactory(
        forLoggerType: TypeReference<R>,
        factoryByName: (String) -> R,
        factoryByClass: (Class<Any>) -> R,
    ) = readOnly()

    override fun <O : Any, T : O> addAlias(
        existingRegisteredType: TypeReference<T>,
        otherAncestorOrInterface: TypeReference<O>,
    ) = readOnly()

    override fun <T : Any> hasFactory(forType: TypeReference<T>): Boolean = forType.type in bindings

    override fun importModule(submodule: InjektModule) = readOnly()

    private fun readOnly(): Nothing = throw UnsupportedOperationException("Aniyomi's Injekt instance is read-only")

    private fun unsupported(feature: String): Nothing =
        throw UnsupportedOperationException("$feature is not supported by Aniyomi's Injekt instance")
}
