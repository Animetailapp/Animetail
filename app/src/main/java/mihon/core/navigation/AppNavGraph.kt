package mihon.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.kanade.presentation.entries.anime.EpisodeOptionsDialogScreen
import eu.kanade.presentation.more.settings.screen.DiscordAccountsScreen
import eu.kanade.presentation.more.settings.screen.SettingsAdvancedScreen
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen
import eu.kanade.presentation.more.settings.screen.SettingsConnectionsScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsDiscordScreen
import eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen
import eu.kanade.presentation.more.settings.screen.SettingsLibraryScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.more.settings.screen.SettingsReaderScreen
import eu.kanade.presentation.more.settings.screen.SettingsSearchScreen
import eu.kanade.presentation.more.settings.screen.SettingsSecurityScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.more.settings.screen.about.OpenSourceLicensesScreen
import eu.kanade.presentation.more.settings.screen.advanced.ClearAnimeDatabaseScreen
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen
import eu.kanade.presentation.more.settings.screen.appearance.AppCustomThemeColorPickerScreen
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.presentation.more.settings.screen.data.CreateBackupScreen
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.more.settings.screen.data.SyncSettingsSelector
import eu.kanade.presentation.more.settings.screen.data.SyncTriggerOptionsScreen
import eu.kanade.presentation.more.settings.screen.debug.BackupSchemaScreen
import eu.kanade.presentation.more.settings.screen.debug.DebugInfoScreen
import eu.kanade.presentation.more.settings.screen.debug.WorkerInfoScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsAdvancedScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsAudioScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsDecoderScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsGesturesScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsMainScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsPlayerScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsSubtitleScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsTorrentScreen
import eu.kanade.presentation.more.settings.screen.player.custombutton.PlayerSettingsCustomButtonScreen
import eu.kanade.presentation.more.settings.screen.player.editor.PlayerSettingsEditorScreen
import eu.kanade.presentation.more.settings.screen.player.editor.codeeditor.CodeEditScreen
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionFilterScreen
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.MigrateAnimeScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.AnimeSourceSearchScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.AnimeSourcesFilterScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionFilterScreen
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.MangaExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.MangaSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.manga.MigrateMangaScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MangaSourceSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.MangaSourcesFilterScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.deeplink.anime.DeepLinkAnimeScreen
import eu.kanade.tachiyomi.ui.deeplink.manga.DeepLinkMangaScreen
import eu.kanade.tachiyomi.ui.download.DownloadsTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.anime.notes.AnimeNotesScreen
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackInfoDialog
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.manga.notes.MangaNotesScreen
import eu.kanade.tachiyomi.ui.entries.manga.track.MangaTrackInfoDialog
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.home.TopLevelRoute
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.more.NetworkStreamScreen
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.ui.stats.StatsTab
import eu.kanade.tachiyomi.ui.storage.StorageTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.feature.migration.config.AnimeMigrationConfigScreen
import mihon.feature.migration.config.MangaMigrationConfigScreen
import mihon.feature.migration.list.AnimeMigrationListScreen
import mihon.feature.migration.list.MangaMigrationListScreen
import mihon.feature.support.SupportUsScreen
import mihon.feature.upcoming.anime.UpcomingAnimeScreen
import mihon.feature.upcoming.manga.UpcomingMangaScreen

fun EntryProviderScope<NavKey>.appEntries() {
    entry<HomeRoute> {
        HomeScreen()
    }
    entry<TopLevelRoute.MangaLibrary> {
        MangaLibraryTab()
    }
    entry<TopLevelRoute.Updates> {
        UpdatesTab()
    }
    entry<TopLevelRoute.History> {
        HistoriesTab()
    }
    entry<TopLevelRoute.Browse> {
        BrowseTab()
    }
    entry<WebViewRoute> { route ->
        LocalAssistContentManager.current.Provide(route) {
            WebViewScreen(
                url = route.url,
                initialTitle = route.initialTitle,
                sourceId = route.sourceId,
            )
        }
    }

    // Manga routes

    entry<DeepLinkMangaRoute> { route ->
        DeepLinkMangaScreen(route.query)
    }
    entry<MangaNotesRoute> { route ->
        MangaNotesScreen(route.mangaId)
    }
    entry<MangaRoute> { route ->
        LocalAssistContentManager.current.Provide(route) {
            MangaScreen(route.mangaId, route.fromSource)
        }
    }
    entry<MangaTrackInfoDialogRoute>(metadata = OverlaySheetScene.overlaySheet()) { route ->
        val navigator = LocalNavigator.current
        MangaTrackInfoDialog(
            mangaId = route.mangaId,
            mangaTitle = route.mangaTitle,
            sourceId = route.sourceId,
            onDismissRequest = { navigator.pop() },
        )
    }
    entry<GlobalMangaSearchRoute> { route ->
        GlobalMangaSearchScreen(route.searchQuery, route.extensionFilter)
    }
    entry<UpcomingMangaRoute> {
        UpcomingMangaScreen()
    }
    entry<BrowseMangaSourceRoute> { route ->
        LocalAssistContentManager.current.Provide(route) {
            BrowseMangaSourceScreen(route.sourceId, route.listingQuery)
        }
    }
    entry<MangaExtensionDetailsRoute> { route ->
        MangaExtensionDetailsScreen(route.pkgName)
    }
    entry<MangaExtensionFilterRoute> {
        MangaExtensionFilterScreen()
    }
    entry<MigrateMangaRoute> { route ->
        MigrateMangaScreen(route.sourceId)
    }
    entry<MigrateMangaSearchRoute> { route ->
        MigrateMangaSearchScreen(route.mangaId)
    }
    entry<MangaSourceSearchRoute> { route ->
        MangaSourceSearchScreen(route.currentMangaId, route.sourceId, route.query)
    }
    entry<MangaMigrationConfigRoute> { route ->
        MangaMigrationConfigScreen(route.mangaIds)
    }
    entry<MangaMigrationListRoute> { route ->
        MangaMigrationListScreen(route.mangaIds, route.extraSearchQuery)
    }
    entry<MangaSourcesFilterRoute> {
        MangaSourcesFilterScreen()
    }
    entry<MangaSourcePreferencesRoute> { route ->
        MangaSourcePreferencesScreen(route.sourceId)
    }

    // Anime routes

    entry<DeepLinkAnimeRoute> { route ->
        DeepLinkAnimeScreen(route.query)
    }
    entry<AnimeNotesRoute> { route ->
        AnimeNotesScreen(route.animeId)
    }
    entry<AnimeRoute> { route ->
        LocalAssistContentManager.current.Provide(route) {
            AnimeScreen(route.animeId, route.fromSource)
        }
    }
    entry<AnimeTrackInfoDialogRoute>(metadata = OverlaySheetScene.overlaySheet()) { route ->
        val navigator = LocalNavigator.current
        AnimeTrackInfoDialog(
            animeId = route.animeId,
            animeTitle = route.animeTitle,
            sourceId = route.sourceId,
            isSeason = route.isSeason,
            onDismissRequest = { navigator.pop() },
        )
    }
    entry<EpisodeOptionsDialogRoute>(metadata = OverlaySheetScene.overlaySheet()) { route ->
        val navigator = LocalNavigator.current
        EpisodeOptionsDialogScreen(
            useExternalDownloader = route.useExternalDownloader,
            episodeTitle = route.episodeTitle,
            episodeId = route.episodeId,
            animeId = route.animeId,
            sourceId = route.sourceId,
            onDismissRequest = { navigator.pop() },
        )
    }
    entry<GlobalAnimeSearchRoute> { route ->
        GlobalAnimeSearchScreen(route.searchQuery, route.extensionFilter)
    }
    entry<UpcomingAnimeRoute> {
        UpcomingAnimeScreen()
    }
    entry<BrowseAnimeSourceRoute> { route ->
        LocalAssistContentManager.current.Provide(route) {
            BrowseAnimeSourceScreen(
                sourceId = route.sourceId,
                listingQuery = route.listingQuery,
                filtersJson = route.filtersJson,
                savedSearch = route.savedSearch,
            )
        }
    }
    entry<SourceFeedRoute> { route ->
        SourceFeedScreen(route.sourceId)
    }
    entry<AnimeExtensionDetailsRoute> { route ->
        AnimeExtensionDetailsScreen(route.pkgName)
    }
    entry<AnimeExtensionFilterRoute> {
        AnimeExtensionFilterScreen()
    }
    entry<MigrateAnimeRoute> { route ->
        MigrateAnimeScreen(route.sourceId)
    }
    entry<MigrateSeasonSelectRoute> { route ->
        MigrateSeasonSelectScreen(route.oldAnimeId, route.animeId)
    }
    entry<MigrateAnimeSearchRoute> { route ->
        MigrateAnimeSearchScreen(route.animeId)
    }
    entry<AnimeSourceSearchRoute> { route ->
        AnimeSourceSearchScreen(route.currentAnimeId, route.sourceId, route.query)
    }
    entry<AnimeMigrationConfigRoute> { route ->
        AnimeMigrationConfigScreen(route.animeIds)
    }
    entry<AnimeMigrationListRoute> { route ->
        AnimeMigrationListScreen(route.animeIds, route.extraSearchQuery)
    }
    entry<AnimeSourcesFilterRoute> {
        AnimeSourcesFilterScreen()
    }
    entry<AnimeSourcePreferencesRoute> { route ->
        AnimeSourcePreferencesScreen(route.sourceId)
    }

    // Shared More / Tool routes

    entry<CategoryRoute> {
        CategoriesTab()
    }
    entry<DownloadQueueRoute> {
        DownloadsTab()
    }
    entry<StatsRoute> {
        StatsTab()
    }
    entry<StorageRoute> {
        StorageTab()
    }
    entry<NetworkStreamRoute> {
        NetworkStreamScreen()
    }
    entry<NewUpdateRoute>(metadata = TwoPaneSettingsScene.fullScreen()) { route ->
        NewUpdateScreen(
            versionName = route.versionName,
            changelogInfo = route.changelogInfo,
            releaseLink = route.releaseLink,
            downloadLink = route.downloadLink,
        )
    }
    entry<OnboardingRoute> {
        OnboardingScreen()
    }
    entry<SupportUsRoute>(metadata = TwoPaneSettingsScene.fullScreen()) {
        SupportUsScreen()
    }

    // Settings routes

    entry<SettingsMainScreen>(metadata = TwoPaneSettingsScene.listPane()) {
        SettingsMainScreen.Content()
    }
    entry<PlayerSettingsMainScreen>(metadata = TwoPaneSettingsScene.listPane()) { route ->
        route.Content()
    }
    entry<AboutRoute> {
        AboutScreen()
    }
    entry<AppLanguageRoute> {
        AppLanguageScreen()
    }
    entry<AppCustomThemeColorPickerRoute> {
        AppCustomThemeColorPickerScreen()
    }
    entry<BackupSchemaRoute> {
        BackupSchemaScreen()
    }
    entry<ClearDatabaseRoute> {
        ClearDatabaseScreen()
    }
    entry<ClearAnimeDatabaseRoute> {
        ClearAnimeDatabaseScreen()
    }
    entry<CreateBackupRoute> {
        CreateBackupScreen()
    }
    entry<DebugInfoRoute> {
        DebugInfoScreen()
    }
    entry<ExtensionStoresRoute> { route ->
        ExtensionStoresScreen(route.isManga)
    }
    entry<OpenSourceLicensesRoute> {
        OpenSourceLicensesScreen()
    }
    entry<RestoreBackupRoute> { route ->
        RestoreBackupScreen(route.uri)
    }
    entry<SettingsSearchRoute> { route ->
        SettingsSearchScreen(route.isPlayer)
    }
    entry<WorkerInfoRoute> {
        WorkerInfoScreen()
    }
    entry<SyncSettingsSelectorRoute> {
        SyncSettingsSelector()
    }
    entry<SyncTriggerOptionsRoute> {
        SyncTriggerOptionsScreen()
    }
    entry<SettingsDiscordAccountsRoute> {
        DiscordAccountsScreen()
    }
    entry<PlayerSettingsCustomButtonRoute> {
        PlayerSettingsCustomButtonScreen()
    }
    entry<PlayerSettingsEditorRoute> {
        PlayerSettingsEditorScreen()
    }
    entry<CodeEditRoute> { route ->
        CodeEditScreen(route.filePath)
    }

    entry<SettingsAdvancedScreen> {
        SettingsAdvancedScreen.Content()
    }
    entry<SettingsAppearanceScreen> {
        SettingsAppearanceScreen.Content()
    }
    entry<SettingsBrowseScreen> {
        SettingsBrowseScreen.Content()
    }
    entry<SettingsConnectionsScreen> {
        SettingsConnectionsScreen.Content()
    }
    entry<SettingsDiscordScreen> {
        SettingsDiscordScreen.Content()
    }
    entry<SettingsDataScreen> {
        SettingsDataScreen.Content()
    }
    entry<SettingsDownloadScreen> {
        SettingsDownloadScreen.Content()
    }
    entry<SettingsLibraryScreen> {
        SettingsLibraryScreen.Content()
    }
    entry<SettingsReaderScreen> {
        SettingsReaderScreen.Content()
    }
    entry<SettingsSecurityScreen> {
        SettingsSecurityScreen.Content()
    }
    entry<SettingsTrackingScreen> {
        SettingsTrackingScreen.Content()
    }
    entry<PlayerSettingsPlayerScreen> {
        PlayerSettingsPlayerScreen.Content()
    }
    entry<PlayerSettingsGesturesScreen> {
        PlayerSettingsGesturesScreen.Content()
    }
    entry<PlayerSettingsDecoderScreen> {
        PlayerSettingsDecoderScreen.Content()
    }
    entry<PlayerSettingsSubtitleScreen> {
        PlayerSettingsSubtitleScreen.Content()
    }
    entry<PlayerSettingsAudioScreen> {
        PlayerSettingsAudioScreen.Content()
    }
    entry<PlayerSettingsTorrentScreen> {
        PlayerSettingsTorrentScreen.Content()
    }
    entry<PlayerSettingsAdvancedScreen> {
        PlayerSettingsAdvancedScreen.Content()
    }
}
