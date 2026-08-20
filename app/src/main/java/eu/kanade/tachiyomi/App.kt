package eu.kanade.tachiyomi

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import animetail.feature.mpvfiles.MpvConfig
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.util.DebugLogger
import dev.mihon.injekt.patchInjekt
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
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
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.domain.track.manga.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.manga.interactor.TrackChapter
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.crash.CrashActivity
import eu.kanade.tachiyomi.crash.GlobalExceptionHandler
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.coil.AnimeCoverKeyer
import eu.kanade.tachiyomi.data.coil.AnimeImageFetcher
import eu.kanade.tachiyomi.data.coil.AnimeKeyer
import eu.kanade.tachiyomi.data.coil.BufferedSourceFetcher
import eu.kanade.tachiyomi.data.coil.ImageDecoder
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverKeyer
import eu.kanade.tachiyomi.data.coil.MangaKeyer
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.LocalHttpServerHolder
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import mihon.app.di.AppGraph
import mihon.app.di.injekt.MetroInteropModule
import mihon.core.metro.GraphProvider
import mihon.core.migration.Migration
import mihon.core.migration.Migrator
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStoreCountAsFlow
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStores
import mihon.domain.extension.manga.interactor.GetMangaExtensionStoreCountAsFlow
import mihon.domain.extension.manga.interactor.GetMangaExtensionStores
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import org.conscrypt.Conscrypt
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
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
import tachiyomi.i18n.MR
import tachiyomi.presentation.widget.entries.anime.AnimeWidgetManager
import tachiyomi.presentation.widget.entries.manga.MangaWidgetManager
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.security.Security
import eu.kanade.domain.extension.manga.interactor.GetExtensionSources as GetMangaExtensionSources

class App : Application(), DefaultLifecycleObserver, SingletonImageLoader.Factory, GraphProvider<AppGraph> {

    override val graph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(context = this, isDebugBuild = isDebugBuildType)
    }

    @Inject lateinit var preferenceStore: PreferenceStore

    @Inject lateinit var basePreferences: BasePreferences

    @Inject lateinit var privacyPreferences: PrivacyPreferences

    @Inject lateinit var networkPreferences: NetworkPreferences

    @Inject lateinit var uiPreferences: UiPreferences

    @Inject lateinit var syncPreferences: SyncPreferences

    @Inject lateinit var coverCache: MangaCoverCache

    @Inject lateinit var animeCoverCache: AnimeCoverCache

    @Inject lateinit var animeBackgroundCache: AnimeBackgroundCache

    @Inject lateinit var networkHelper: NetworkHelper

    @Inject lateinit var sourceManager: MangaSourceManager

    @Inject lateinit var animeSourceManager: AnimeSourceManager

    @Inject lateinit var mangaWidgetManager: MangaWidgetManager

    @Inject lateinit var animeWidgetManager: AnimeWidgetManager

    @Inject lateinit var injektMetroInteropModule: MetroInteropModule

    @Inject lateinit var migrations: Set<Migration>

    private val disableIncognitoReceiver = DisableIncognitoReceiver()

    @SuppressLint("LaunchActivityFromNotification")
    @Suppress("LongMethod")
    override fun onCreate() {
        super<Application>.onCreate()

        // Must run before the graph is built, since injecting dependencies initializes WebView and the
        // suffix can't be set once a provider exists in the process. Secondary processes die otherwise.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val process = getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        }

        setupInjekt(graph)
        graph.inject(this)
        Injekt.importModule(injektMetroInteropModule)

        GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)

        // TLS 1.3 support for Android < 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        setupNotificationChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val scope = ProcessLifecycleOwner.get().lifecycleScope

        // Show notification to disable Incognito Mode when it's enabled
        basePreferences.incognitoMode.changes()
            .onEach { enabled ->
                if (enabled) {
                    disableIncognitoReceiver.register()
                    notify(
                        Notifications.ID_INCOGNITO_MODE,
                        Notifications.CHANNEL_INCOGNITO_MODE,
                    ) {
                        setContentTitle(stringResource(MR.strings.pref_incognito_mode))
                        setContentText(stringResource(MR.strings.notification_incognito_text))
                        setSmallIcon(R.drawable.ic_glasses_24dp)
                        setOngoing(true)

                        val pendingIntent = PendingIntent.getBroadcast(
                            this@App,
                            0,
                            Intent(ACTION_DISABLE_INCOGNITO_MODE),
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        setContentIntent(pendingIntent)
                    }
                } else {
                    disableIncognitoReceiver.unregister()
                    cancelNotification(Notifications.ID_INCOGNITO_MODE)
                }
            }
            .launchIn(ProcessLifecycleOwner.get().lifecycleScope)

        setAppCompatDelegateThemeMode(uiPreferences.themeMode.get())

        // Updates widget update
        with(mangaWidgetManager) {
            init(scope)
        }
        with(animeWidgetManager) {
            init(scope)
        }

        if (!LogcatLogger.isInstalled) {
            val minLogPriority = when {
                networkPreferences.verboseLogging().get() -> LogPriority.VERBOSE
                BuildConfig.DEBUG -> LogPriority.DEBUG
                else -> LogPriority.INFO
            }
            AndroidLogcatLogger.installOnDebuggableApp(this, minLogPriority)
        }

        initializeMigrator()

        val syncTriggerOpt = syncPreferences.getSyncTriggerOptions()
        if (syncPreferences.isSyncEnabled() && syncTriggerOpt.syncOnAppStart) {
            SyncDataJob.startNow(this@App)
        }
    }

    private fun setupInjekt(graph: AppGraph) {
        patchInjekt()
        Injekt.addSingleton<Application>(this)
        Injekt.addSingleton<Context>(this)
        Injekt.addSingleton<Json>(graph.json)

        Injekt.addSingleton<PreferenceStore>(graph.preferenceStore)
        Injekt.addSingleton<BasePreferences>(graph.basePreferences)
        Injekt.addSingleton<UiPreferences>(graph.uiPreferences)
        Injekt.addSingleton<LibraryPreferences>(graph.libraryPreferences)
        Injekt.addSingleton<SyncPreferences>(graph.syncPreferences)
        Injekt.addSingleton<TrackPreferences>(graph.trackPreferences)
        Injekt.addSingleton<SourcePreferences>(graph.sourcePreferences)
        Injekt.addSingleton<BackupPreferences>(graph.backupPreferences)
        Injekt.addSingleton<StoragePreferences>(graph.storagePreferences)
        Injekt.addSingleton<SecurityPreferences>(graph.securityPreferences)
        Injekt.addSingleton<PrivacyPreferences>(graph.privacyPreferences)
        Injekt.addSingleton<DownloadPreferences>(graph.downloadPreferences)
        Injekt.addSingleton<ConnectionsPreferences>(graph.connectionsPreferences)
        Injekt.addSingleton<PlayerPreferences>(graph.playerPreferences)
        Injekt.addSingleton<GesturePreferences>(graph.gesturePreferences)
        Injekt.addSingleton<AdvancedPlayerPreferences>(graph.advancedPlayerPreferences)
        Injekt.addSingleton<DecoderPreferences>(graph.decoderPreferences)
        Injekt.addSingleton<AudioPreferences>(graph.audioPreferences)
        Injekt.addSingleton<SubtitlePreferences>(graph.subtitlePreferences)
        Injekt.addSingleton<TorrentPreferences>(graph.torrentPreferences)
        Injekt.addSingleton<ReaderPreferences>(graph.readerPreferences)
        Injekt.addSingleton<NetworkPreferences>(graph.networkPreferences)
        Injekt.addSingleton<StorageManager>(graph.storageManager)
        Injekt.addSingleton<LocalHttpServerHolder>(graph.localHttpServerHolder)
        Injekt.addSingleton<TorrentServerApi>(graph.torrentServerApi)
        Injekt.addSingleton<TorrentServerUtils>(graph.torrentServerUtils)
        Injekt.addSingleton<AnimeDownloadManager>(graph.animeDownloadManager)
        Injekt.addSingleton<MangaDownloadManager>(graph.mangaDownloadManager)
        Injekt.addSingleton<AnimeDownloadProvider>(graph.animeDownloadProvider)
        Injekt.addSingleton<MangaDownloadProvider>(graph.mangaDownloadProvider)
        Injekt.addSingleton<AnimeDownloadCache>(graph.animeDownloadCache)
        Injekt.addSingleton<MangaDownloadCache>(graph.mangaDownloadCache)
        Injekt.addSingleton<AnimeCoverCache>(graph.animeCoverCache)
        Injekt.addSingleton<MangaCoverCache>(graph.mangaCoverCache)
        Injekt.addSingleton<AnimeBackgroundCache>(graph.animeBackgroundCache)
        Injekt.addSingleton<ChapterCache>(graph.chapterCache)
        Injekt.addSingleton<AnimeSourceManager>(graph.animeSourceManager)
        Injekt.addSingleton<MangaSourceManager>(graph.mangaSourceManager)
        Injekt.addSingleton<TrackerManager>(graph.trackerManager)
        Injekt.addSingleton<ConnectionsManager>(graph.connectionsManager)
        Injekt.addSingleton<SyncManager>(graph.syncManager)
        Injekt.addSingleton<AppUpdateChecker>(graph.updateChecker)
        Injekt.addSingleton<MangaExtensionManager>(graph.mangaExtensionManager)
        Injekt.addSingleton<AnimeExtensionManager>(graph.animeExtensionManager)
        Injekt.addSingleton<NetworkHelper>(graph.networkHelper)
        Injekt.addSingleton<GetEpisode>(graph.getEpisode)
        Injekt.addSingleton<GetChapter>(graph.getChapter)
        Injekt.addSingleton<GetAnime>(graph.getAnime)
        Injekt.addSingleton<GetManga>(graph.getManga)
        Injekt.addSingleton<GetCustomButtons>(graph.getCustomButtons)
        Injekt.addSingleton<CreateCustomButton>(graph.createCustomButton)
        Injekt.addSingleton<DeleteCustomButton>(graph.deleteCustomButton)
        Injekt.addSingleton<UpdateCustomButton>(graph.updateCustomButton)
        Injekt.addSingleton<ReorderCustomButton>(graph.reorderCustomButton)
        Injekt.addSingleton<ToggleFavoriteCustomButton>(graph.toggleFavoriteCustomButton)
        Injekt.addSingleton<MpvConfig>(graph.mpvConfig)
        Injekt.addSingleton<GetAnimeHistory>(graph.getAnimeHistory)
        Injekt.addSingleton<GetMangaHistory>(graph.getMangaHistory)
        Injekt.addSingleton<GetLibraryAnime>(graph.getLibraryAnime)
        Injekt.addSingleton<GetLibraryManga>(graph.getLibraryManga)
        Injekt.addSingleton<NetworkToLocalAnime>(graph.networkToLocalAnime)
        Injekt.addSingleton<NetworkToLocalManga>(graph.networkToLocalManga)
        Injekt.addSingleton<GetFeedSavedSearchGlobal>(graph.getFeedSavedSearchGlobal)
        Injekt.addSingleton<GetFeedSavedSearchBySourceId>(graph.getFeedSavedSearchBySourceId)
        Injekt.addSingleton<GetSavedSearchGlobalFeed>(graph.getSavedSearchGlobalFeed)
        Injekt.addSingleton<GetSavedSearchBySourceIdFeed>(graph.getSavedSearchBySourceIdFeed)
        Injekt.addSingleton<CountFeedSavedSearchGlobal>(graph.countFeedSavedSearchGlobal)
        Injekt.addSingleton<CountFeedSavedSearchBySourceId>(graph.countFeedSavedSearchBySourceId)
        Injekt.addSingleton<GetSavedSearchBySourceId>(graph.getSavedSearchBySourceId)
        Injekt.addSingleton<InsertFeedSavedSearch>(graph.insertFeedSavedSearch)
        Injekt.addSingleton<DeleteFeedSavedSearchById>(graph.deleteFeedSavedSearchById)
        Injekt.addSingleton<ReorderFeed>(graph.reorderFeed)
        Injekt.addSingleton<GetExhSavedSearch>(graph.getExhSavedSearch)
        Injekt.addSingleton<GetVisibleAnimeCategories>(graph.getVisibleAnimeCategories)
        Injekt.addSingleton<GetVisibleMangaCategories>(graph.getVisibleMangaCategories)
        Injekt.addSingleton<GetTracksPerAnime>(graph.getTracksPerAnime)
        Injekt.addSingleton<GetTracksPerManga>(graph.getTracksPerManga)
        Injekt.addSingleton<GetNextEpisodes>(graph.getNextEpisodes)
        Injekt.addSingleton<GetNextChapters>(graph.getNextChapters)
        Injekt.addSingleton<GetChaptersByMangaId>(graph.getChaptersByMangaId)
        Injekt.addSingleton<SetSeenStatus>(graph.setSeenStatus)
        Injekt.addSingleton<SetReadStatus>(graph.setReadStatus)
        Injekt.addSingleton<UpdateAnime>(graph.updateAnime)
        Injekt.addSingleton<UpdateManga>(graph.updateManga)
        Injekt.addSingleton<SetAnimeCategories>(graph.setAnimeCategories)
        Injekt.addSingleton<SetMangaCategories>(graph.setMangaCategories)
        Injekt.addSingleton<RemoveAnimeHistory>(graph.removeAnimeHistory)
        Injekt.addSingleton<RemoveMangaHistory>(graph.removeMangaHistory)
        Injekt.addSingleton<GetDuplicateLibraryAnime>(graph.getDuplicateLibraryAnime)
        Injekt.addSingleton<GetDuplicateLibraryManga>(graph.getDuplicateLibraryManga)
        Injekt.addSingleton<GetMangaTracks>(graph.getMangaTracks)
        Injekt.addSingleton<GetAnimeTracks>(graph.getAnimeTracks)
        Injekt.addSingleton<RefreshAnimeTracks>(graph.refreshAnimeTracks)
        Injekt.addSingleton<RefreshMangaTracks>(graph.refreshMangaTracks)

        Injekt.addSingleton<CreateAnimeCategoryWithName>(graph.createAnimeCategoryWithName)
        Injekt.addSingleton<DeleteAnimeCategory>(graph.deleteAnimeCategory)
        Injekt.addSingleton<HideAnimeCategory>(graph.hideAnimeCategory)
        Injekt.addSingleton<RenameAnimeCategory>(graph.renameAnimeCategory)
        Injekt.addSingleton<ReorderAnimeCategory>(graph.reorderAnimeCategory)
        Injekt.addSingleton<SetAnimeDisplayMode>(graph.setAnimeDisplayMode)
        Injekt.addSingleton<SetSortModeForAnimeCategory>(graph.setSortModeForAnimeCategory)

        Injekt.addSingleton<CreateMangaCategoryWithName>(graph.createMangaCategoryWithName)
        Injekt.addSingleton<DeleteMangaCategory>(graph.deleteMangaCategory)
        Injekt.addSingleton<HideMangaCategory>(graph.hideMangaCategory)
        Injekt.addSingleton<RenameMangaCategory>(graph.renameMangaCategory)
        Injekt.addSingleton<ReorderMangaCategory>(graph.reorderMangaCategory)
        Injekt.addSingleton<SetMangaDisplayMode>(graph.setMangaDisplayMode)
        Injekt.addSingleton<SetSortModeForMangaCategory>(graph.setSortModeForMangaCategory)

        Injekt.addSingleton<TrustMangaExtension>(graph.trustMangaExtension)
        Injekt.addSingleton<TrustAnimeExtension>(graph.trustAnimeExtension)
        Injekt.addSingleton<GetMangaFavorites>(graph.getMangaFavorites)
        Injekt.addSingleton<GetAnimeFavorites>(graph.getAnimeFavorites)
        Injekt.addSingleton<ResetMangaViewerFlags>(graph.resetMangaViewerFlags)
        Injekt.addSingleton<ResetAnimeViewerFlags>(graph.resetAnimeViewerFlags)
        Injekt.addSingleton<ResetMangaCategoryFlags>(graph.resetMangaCategoryFlags)
        Injekt.addSingleton<ResetAnimeCategoryFlags>(graph.resetAnimeCategoryFlags)
        Injekt.addSingleton<AddMangaTracks>(graph.addMangaTracks)
        Injekt.addSingleton<AddAnimeTracks>(graph.addAnimeTracks)
        Injekt.addSingleton<InsertMangaTrack>(graph.insertMangaTrack)
        Injekt.addSingleton<InsertAnimeTrack>(graph.insertAnimeTrack)
        Injekt.addSingleton<DeleteAnimeTrack>(graph.deleteAnimeTrack)
        Injekt.addSingleton<DeleteMangaTrack>(graph.deleteMangaTrack)
        Injekt.addSingleton<TrackEpisode>(graph.trackEpisode)
        Injekt.addSingleton<TrackChapter>(graph.trackChapter)
        Injekt.addSingleton<SyncEpisodeProgressWithTrack>(graph.syncEpisodeProgressWithTrack)
        Injekt.addSingleton<SyncChapterProgressWithTrack>(graph.syncChapterProgressWithTrack)
        Injekt.addSingleton<DelayedMangaTrackingStore>(graph.delayedMangaTrackingStore)
        Injekt.addSingleton<DelayedAnimeTrackingStore>(graph.delayedAnimeTrackingStore)
        Injekt.addSingleton<UpsertAnimeHistory>(graph.upsertAnimeHistory)
        Injekt.addSingleton<UpdateEpisode>(graph.updateEpisode)
        Injekt.addSingleton<GetEpisodesByAnimeId>(graph.getEpisodesByAnimeId)

        Injekt.addSingleton<UpdateAnimeNotes>(graph.updateAnimeNotes)
        Injekt.addSingleton<UpdateMangaNotes>(graph.updateMangaNotes)
        Injekt.addSingleton<GetAnimeByUrlAndSourceId>(graph.getAnimeByUrlAndSourceId)
        Injekt.addSingleton<GetMangaByUrlAndSourceId>(graph.getMangaByUrlAndSourceId)
        Injekt.addSingleton<GetEpisodeByUrlAndAnimeId>(graph.getEpisodeByUrlAndAnimeId)
        Injekt.addSingleton<GetChapterByUrlAndMangaId>(graph.getChapterByUrlAndMangaId)
        Injekt.addSingleton<UpdateAnimeFromRemote>(graph.updateAnimeFromRemote)
        Injekt.addSingleton<ImageSaver>(graph.imageSaver)
        Injekt.addSingleton<SetAnimeViewerFlags>(graph.setAnimeViewerFlags)
        Injekt.addSingleton<SetMangaViewerFlags>(graph.setMangaViewerFlags)
        Injekt.addSingleton<SetAnimeEpisodeFlags>(graph.setAnimeEpisodeFlags)
        Injekt.addSingleton<SetMangaChapterFlags>(graph.setMangaChapterFlags)
        Injekt.addSingleton<SetAnimeSeasonFlags>(graph.setAnimeSeasonFlags)
        Injekt.addSingleton<SetCustomAnimeInfo>(graph.setCustomAnimeInfo)
        Injekt.addSingleton<SetCustomMangaInfo>(graph.setCustomMangaInfo)
        Injekt.addSingleton<GetCustomAnimeInfo>(graph.getCustomAnimeInfo)
        Injekt.addSingleton<GetCustomMangaInfo>(graph.getCustomMangaInfo)
        Injekt.addSingleton<GetAnimeWithEpisodesAndSeasons>(graph.getAnimeWithEpisodesAndSeasons)
        Injekt.addSingleton<GetMangaWithChapters>(graph.getMangaWithChapters)
        Injekt.addSingleton<AnimeFetchInterval>(graph.animeFetchInterval)
        Injekt.addSingleton<MangaFetchInterval>(graph.mangaFetchInterval)

        Injekt.addSingleton<GetExcludedScanlators>(graph.getExcludedScanlators)
        Injekt.addSingleton<SetExcludedScanlators>(graph.setExcludedScanlators)
        Injekt.addSingleton<GetAvailableScanlators>(graph.getAvailableScanlators)
        Injekt.addSingleton<SyncChaptersWithSource>(graph.syncChaptersWithSource)
        Injekt.addSingleton<SyncEpisodesWithSource>(graph.syncEpisodesWithSource)
        Injekt.addSingleton<SyncSeasonsWithSource>(graph.syncSeasonsWithSource)
        Injekt.addSingleton<UpdateChapter>(graph.updateChapter)
        Injekt.addSingleton<SetAnimeDefaultEpisodeFlags>(graph.setAnimeDefaultEpisodeFlags)
        Injekt.addSingleton<SetMangaDefaultChapterFlags>(graph.setMangaDefaultChapterFlags)
        Injekt.addSingleton<SetAnimeDefaultSeasonFlags>(graph.setAnimeDefaultSeasonFlags)
        Injekt.addSingleton<GetAnimeSeasonsByParentId>(graph.getAnimeSeasonsByParentId)
        Injekt.addSingleton<GetBookmarkedChaptersByMangaId>(graph.getBookmarkedChaptersByMangaId)

        Injekt.addSingleton<ToggleAnimeSourcePin>(graph.toggleAnimeSourcePin)
        Injekt.addSingleton<ToggleMangaSourcePin>(graph.toggleMangaSourcePin)
        Injekt.addSingleton<ToggleAnimeSource>(graph.toggleAnimeSource)
        Injekt.addSingleton<ToggleMangaSource>(graph.toggleMangaSource)
        Injekt.addSingleton<ToggleAnimeIncognito>(graph.toggleAnimeIncognito)
        Injekt.addSingleton<ToggleMangaIncognito>(graph.toggleMangaIncognito)
        Injekt.addSingleton<GetAnimeSourcesWithFavoriteCount>(graph.getAnimeSourcesWithFavoriteCount)
        Injekt.addSingleton<GetMangaSourcesWithFavoriteCount>(graph.getMangaSourcesWithFavoriteCount)
        Injekt.addSingleton<GetAnimeIncognitoState>(graph.getAnimeIncognitoState)
        Injekt.addSingleton<GetMangaIncognitoState>(graph.getMangaIncognitoState)
        Injekt.addSingleton<GetLanguagesWithAnimeSources>(graph.getLanguagesWithAnimeSources)
        Injekt.addSingleton<GetLanguagesWithMangaSources>(graph.getLanguagesWithMangaSources)
        Injekt.addSingleton<GetEnabledAnimeSources>(graph.getEnabledAnimeSources)
        Injekt.addSingleton<GetEnabledMangaSources>(graph.getEnabledMangaSources)
        Injekt.addSingleton<ToggleLanguage>(graph.toggleLanguage)
        Injekt.addSingleton<SetMigrateSorting>(graph.setMigrateSorting)
        Injekt.addSingleton<InsertSavedSearch>(graph.insertSavedSearch)
        Injekt.addSingleton<GetAnimeExtensionSources>(graph.getAnimeExtensionSources)
        Injekt.addSingleton<GetMangaExtensionSources>(graph.getMangaExtensionSources)
        Injekt.addSingleton<GetAnimeExtensionsByType>(graph.getAnimeExtensionsByType)
        Injekt.addSingleton<GetMangaExtensionsByType>(graph.getMangaExtensionsByType)
        Injekt.addSingleton<GetAnimeExtensionLanguages>(graph.getAnimeExtensionLanguages)
        Injekt.addSingleton<GetMangaExtensionLanguages>(graph.getMangaExtensionLanguages)
        Injekt.addSingleton<GetAnimeUpdates>(graph.getAnimeUpdates)
        Injekt.addSingleton<GetMangaUpdates>(graph.getMangaUpdates)
        Injekt.addSingleton<GetApplicationRelease>(graph.getApplicationRelease)
        Injekt.addSingleton<ToggleExcludeFromMangaDataSaver>(graph.toggleExcludeFromMangaDataSaver)
        Injekt.addSingleton<GetMangaExtensionStoreCountAsFlow>(graph.getMangaExtensionStoreCountAsFlow)
        Injekt.addSingleton<GetAnimeExtensionStoreCountAsFlow>(graph.getAnimeExtensionStoreCountAsFlow)
        Injekt.addSingleton<GetMangaExtensionStores>(graph.getMangaExtensionStores)
        Injekt.addSingleton<GetAnimeExtensionStores>(graph.getAnimeExtensionStores)
        Injekt.addSingleton<LocalMangaCoverManager>(graph.localMangaCoverManager)
        Injekt.addSingleton<LocalAnimeCoverManager>(graph.localAnimeCoverManager)
        Injekt.addSingleton<LocalAnimeBackgroundManager>(graph.localAnimeBackgroundManager)
        Injekt.addSingleton<LocalEpisodeThumbnailManager>(graph.localEpisodeThumbnailManager)
    }

    private fun initializeMigrator() {
        val preference = preferenceStore.getInt(Preference.appStateKey("last_version_code"), 0)
        logcat {
            "Migration from ${preference.get()} to ${BuildConfig.VERSION_CODE} with ${migrations.size} migration(s)"
        }
        Migrator.initialize(
            old = preference.get(),
            new = BuildConfig.VERSION_CODE,
            migrations = migrations.toList(),
            onMigrationComplete = {
                logcat { "Updating last version to ${BuildConfig.VERSION_CODE}" }
                preference.set(BuildConfig.VERSION_CODE)
            },
        )
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(this).apply {
            val callFactoryLazy = lazy { networkHelper.client }
            components {
                // NetworkFetcher.Factory
                add(OkHttpNetworkFetcherFactory(callFactoryLazy::value))
                // Decoder.Factory
                add(ImageDecoder.Factory())
                // Fetcher.Factory
                add(BufferedSourceFetcher.Factory())
                add(MangaCoverFetcher.MangaCoverFactory(callFactoryLazy, coverCache, sourceManager))
                add(MangaCoverFetcher.MangaFactory(callFactoryLazy, coverCache, sourceManager))
                add(AnimeImageFetcher.AnimeCoverFactory(callFactoryLazy, animeCoverCache, animeSourceManager))
                add(
                    AnimeImageFetcher.AnimeFactory(
                        callFactoryLazy,
                        animeCoverCache,
                        animeBackgroundCache,
                        animeSourceManager,
                    ),
                )
                // Keyer
                add(AnimeKeyer())
                add(MangaKeyer())
                add(AnimeCoverKeyer(animeCoverCache))
                add(MangaCoverKeyer(coverCache))
            }

            crossfade((300 * this@App.animatorDurationScale).toInt())
            allowRgb565(DeviceUtil.isLowRamDevice(this@App))
            if (networkPreferences.verboseLogging().get()) logger(DebugLogger())

            // Coil spawns a new thread for every image load by default
            fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
        }
            .build()
    }

    override fun onStart(owner: LifecycleOwner) {
        SecureActivityDelegate.onApplicationStart(this)

        val syncTriggerOpt = syncPreferences.getSyncTriggerOptions()
        if (syncPreferences.isSyncEnabled() && syncTriggerOpt.syncOnAppResume) {
            SyncDataJob.startNow(this@App)
        }

        // AM (DISCORD) -->
        DiscordRPCService.start(applicationContext)
        // <-- AM (DISCORD)
    }

    override fun onStop(owner: LifecycleOwner) {
        SecureActivityDelegate.onApplicationStopped(this)

        val syncTriggerOpt = syncPreferences.getSyncTriggerOptions()
        if (syncPreferences.isSyncEnabled() && syncTriggerOpt.syncOnAppStart) {
            SyncDataJob.startNow(this@App)
        }

        // AM (DISCORD) -->
        DiscordRPCService.stop(applicationContext)
        // <-- AM (DISCORD)
    }

    override fun getPackageName(): String {
        try {
            // Override the value passed as X-Requested-With in WebView requests
            val stackTrace = Thread.currentThread().stackTrace
            val isChromiumCall = stackTrace.any { trace ->
                trace.className.equals("org.chromium.base.BuildInfo", ignoreCase = true) &&
                    setOf("getAll", "getPackageName", "<init>").any { trace.methodName.equals(it, ignoreCase = true) }
            }

            if (isChromiumCall) return WebViewUtil.spoofedPackageName(applicationContext)
        } catch (_: Exception) {
        }

        return super.getPackageName()
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to modify notification channels" }
        }
    }

    private inner class DisableIncognitoReceiver : BroadcastReceiver() {
        private var registered = false

        override fun onReceive(context: Context, intent: Intent) {
            basePreferences.incognitoMode.set(false)
        }

        fun register() {
            if (!registered) {
                ContextCompat.registerReceiver(
                    this@App,
                    this,
                    IntentFilter(ACTION_DISABLE_INCOGNITO_MODE),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                unregisterReceiver(this)
                registered = false
            }
        }
    }

    private fun isMainProcess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageName == getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val processName = am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
            processName == null || packageName == processName
        }
    }
}

private const val ACTION_DISABLE_INCOGNITO_MODE = "tachi.action.DISABLE_INCOGNITO_MODE"
