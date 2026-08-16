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
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.crash.CrashActivity
import eu.kanade.tachiyomi.crash.GlobalExceptionHandler
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.coil.AnimeCoverKeyer
import eu.kanade.tachiyomi.data.coil.AnimeImageFetcher
import eu.kanade.tachiyomi.data.coil.AnimeKeyer
import eu.kanade.tachiyomi.data.coil.BufferedSourceFetcher
import eu.kanade.tachiyomi.data.coil.ImageDecoder
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverKeyer
import eu.kanade.tachiyomi.data.coil.MangaKeyer
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import mihon.app.di.AppGraph
import mihon.app.di.injekt.MetroInteropModule
import mihon.core.metro.GraphProvider
import mihon.core.migration.Migration
import mihon.core.migration.Migrator
import org.conscrypt.Conscrypt
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.widget.entries.anime.AnimeWidgetManager
import tachiyomi.presentation.widget.entries.manga.MangaWidgetManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.security.Security

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

        graph.inject(this)
        setupInjekt()

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

    private fun setupInjekt() {
        patchInjekt()
        Injekt.addSingleton<Application>(this)
        Injekt.addSingleton<Context>(this)
        Injekt.importModule(injektMetroInteropModule)
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
