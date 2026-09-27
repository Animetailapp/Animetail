package eu.kanade.tachiyomi.extension.anime

import android.content.Context
import android.graphics.drawable.Drawable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.api.AnimeExtensionApi
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionInstaller
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.i18n.MR
import java.util.Locale

/**
 * The manager of anime extensions installed as another apk which extend the available sources. It handles
 * the retrieval of remotely available anime extensions as well as installing, updating and removing them.
 *
 * @param context The application context.
 * @param preferences The application preferences.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences,
    private val trustExtension: TrustAnimeExtension,
    private val installer: AnimeExtensionInstaller,
    private val api: AnimeExtensionApi,
    private val extensionUpdateNotifier: ExtensionUpdateNotifier,
) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initialized = CompletableDeferred<Unit>()

    private val iconMap = mutableMapOf<String, Drawable>()

    private val loadedExtensionsMapFlow = MutableStateFlow(emptyMap<String, AnimeExtension.Loaded>())
    val loadedExtensionsFlow = loadedExtensionsMapFlow.mapExtensionsWhenInitialized()

    val loadedExtensions: List<AnimeExtension.Loaded>
        get() = loadedExtensionsMapFlow.value.values.toList()

    // Every store's listing, since more than one store can list the same extension
    private val availableExtensionListFlow = MutableStateFlow(emptyList<AnimeExtension.Available>())
    val availableExtensionsFlow = availableExtensionListFlow
        .map { extensions -> extensions.associateBy { it.pkgName }.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val notLoadedExtensionsMapFlow = MutableStateFlow(emptyMap<String, AnimeExtension.NotLoaded>())
    val notLoadedExtensionsFlow = notLoadedExtensionsMapFlow.mapExtensionsWhenInitialized()

    private val _installerCancelEvents = MutableSharedFlow<Long>()
    val installerCancelEvents = _installerCancelEvents.asSharedFlow()

    init {
        scope.launch(Dispatchers.IO) {
            loadAnimeExtensions()
            AnimeExtensionInstallReceiver(AnimeInstallationListener()).register(context)

            // Everything the load decision rests on can change while running, so decide again
            merge(
                trustExtension.changes(),
                preferences.enabledContentWarnings.changes().distinctUntilChanged().drop(1).map {},
                preferences.applyContentWarningsToInstalled.changes().distinctUntilChanged().drop(1).map {},
            )
                .collectLatest { loadAnimeExtensions() }
        }
    }

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages.isSet()

    val installedExtensions: List<AnimeExtension.Installed>
        get() = loadedExtensionsMapFlow.value.values.toList() + notLoadedExtensionsMapFlow.value.values.toList()

    suspend fun getLoadedExtensions(): List<AnimeExtension.Loaded> {
        initialized.await()
        return loadedExtensionsMapFlow.value.values.toList()
    }

    suspend fun getNotLoadedExtensions(): List<AnimeExtension.NotLoaded> {
        initialized.await()
        return notLoadedExtensionsMapFlow.value.values.toList()
    }

    suspend fun getExtensionPackage(sourceId: Long): String? {
        return getLoadedExtensions().find { extension ->
            extension.sources.any { it.id == sourceId }
        }?.pkgName
    }

    fun getExtensionPackageAsFlow(sourceId: Long): Flow<String?> {
        return loadedExtensionsFlow.map { extensions ->
            extensions.find { extension ->
                extension.sources.any { it.id == sourceId }
            }?.pkgName
        }
    }

    suspend fun getAppIconForSource(sourceId: Long): Drawable? {
        val pkgName = getExtensionPackage(sourceId) ?: return null

        return iconMap[pkgName] ?: iconMap.getOrPut(pkgName) {
            AnimeExtensionLoader.getAnimeExtensionPackageInfoFromPkgName(context, pkgName)!!
                .applicationInfo!!
                .loadIcon(context.packageManager)
        }
    }

    private var availableAnimeExtensionsSourcesData: Map<Long, StubAnimeSource> = emptyMap()

    private fun setupAvailableAnimeExtensionsSourcesDataMap(
        extensions: List<AnimeExtension.Available>,
    ) {
        if (extensions.isEmpty()) return
        availableAnimeExtensionsSourcesData = extensions
            .flatMap { ext -> ext.sources.map { it.toStubSource() } }
            .associateBy { it.id }
    }

    fun getSourceData(id: Long) = availableAnimeExtensionsSourcesData[id]

    /**
     * Loads and registers the installed animeextensions. Safe to call again: every extension is judged
     * again, so one can move between loaded and not loaded in either direction, while extensions
     * that still pass keep the instances they already had.
     */
    private suspend fun loadAnimeExtensions() {
        try {
            val extensions = AnimeExtensionLoader.loadExtensions(context, loadedExtensionsMapFlow.value)

            loadedExtensionsMapFlow.value = extensions
                .filterIsInstance<AnimeExtension.Loaded>()
                .associateBy { it.pkgName }

            notLoadedExtensionsMapFlow.value = extensions
                .filterIsInstance<AnimeExtension.NotLoaded>()
                .associateBy { it.pkgName }

            // Newly loaded extensions have no status derived from the store index yet
            updatedInstalledAnimeExtensionsStatuses(availableExtensionListFlow.value)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to load extensions" }
        } finally {
            // Release anything waiting on the extensions whether or not the load worked
            initialized.complete(Unit)
        }
    }

    /**
     * Finds the available anime extensions in the [api] and updates [availableExtensionListFlow].
     */
    suspend fun findAvailableExtensions() {
        val extensions: List<AnimeExtension.Available> = try {
            api.findExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            withUIContext { context.toast(MR.strings.extension_api_error) }
            emptyList()
        }

        enableAdditionalSubLanguages(extensions)

        availableExtensionListFlow.value = extensions
        updatedInstalledAnimeExtensionsStatuses(extensions)
        setupAvailableAnimeExtensionsSourcesDataMap(extensions)
    }

    /**
     * Enables the additional sub-languages in the app first run.
     */
    private fun enableAdditionalSubLanguages(extensions: List<AnimeExtension.Available>) {
        if (subLanguagesEnabledOnFirstRun || extensions.isEmpty()) {
            return
        }

        val availableLanguages = extensions
            .flatMap(AnimeExtension.Available::sources)
            .distinctBy(AnimeExtension.Available.AnimeSource::lang)
            .map(AnimeExtension.Available.AnimeSource::lang)

        val deviceLanguage = Locale.getDefault().language
        val defaultLanguages = preferences.enabledLanguages.defaultValue()
        val languagesToEnable = availableLanguages.filter {
            it != deviceLanguage && it.startsWith(deviceLanguage)
        }

        preferences.enabledLanguages.set(defaultLanguages + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    /**
     * Sets the update field of the installed animeextensions with the given [availableExtensions].
     */
    private fun updatedInstalledAnimeExtensionsStatuses(
        availableExtensions: List<AnimeExtension.Available>,
    ) {
        if (availableExtensions.isEmpty()) {
            return
        }

        loadedExtensionsMapFlow.value = loadedExtensionsMapFlow.value.mapValues { (_, extension) ->
            extension.withStatus(availableExtensions)
        }
        updatePendingUpdatesCount()
    }

    fun installExtension(extension: AnimeExtension.Available): Flow<InstallStep> {
        return installer.downloadAndInstall(extension)
    }

    fun updateExtension(extension: AnimeExtension.Loaded): Flow<InstallStep> {
        val update = extension.findUpdate(availableExtensionListFlow.value) ?: return emptyFlow()
        val isUpdateForPrivatelyInstalled = !extension.isShared
        return installer.downloadAndInstall(update, isUpdateForPrivatelyInstalled)
    }

    fun cancelInstallUpdateExtension(extension: AnimeExtension) {
        installer.cancelInstall(extension.pkgName)
    }

    fun cancelInstallerQueue(downloadId: Long) {
        scope.launch { _installerCancelEvents.emit(downloadId) }
    }

    fun setInstalling(downloadId: Long) {
        installer.updateInstallStep(downloadId, InstallStep.Installing)
    }

    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        installer.updateInstallStep(downloadId, step)
    }

    fun uninstallExtension(extension: AnimeExtension.Installed) {
        installer.uninstallApk(extension.pkgName)
    }

    fun trust(extension: AnimeExtension.NotLoaded) {
        val reason = extension.reason as? AnimeExtension.NotLoaded.Reason.Untrusted ?: return
        notLoadedExtensionsMapFlow.value[extension.pkgName] ?: return

        // Loading it again is left to the reload triggered by the trust change
        trustExtension.trust(extension.pkgName, extension.versionCode, reason.signatureHash)
    }

    private fun registerExtension(extension: AnimeExtension.Loaded) {
        loadedExtensionsMapFlow.value += extension
    }

    private fun unregisterAnimeExtension(pkgName: String) {
        loadedExtensionsMapFlow.value -= pkgName
        notLoadedExtensionsMapFlow.value -= pkgName
    }

    private inner class AnimeInstallationListener : AnimeExtensionInstallReceiver.Listener {

        override fun onExtensionLoaded(extension: AnimeExtension.Loaded) {
            registerExtension(extension.withStatus(availableExtensionListFlow.value))
            notLoadedExtensionsMapFlow.value -= extension.pkgName
            updatePendingUpdatesCount()
        }

        override fun onExtensionNotLoaded(extension: AnimeExtension.NotLoaded) {
            loadedExtensionsMapFlow.value -= extension.pkgName
            notLoadedExtensionsMapFlow.value += extension
            updatePendingUpdatesCount()
        }

        override fun onPackageUninstalled(pkgName: String) {
            AnimeExtensionLoader.uninstallPrivateExtension(context, pkgName)
            unregisterAnimeExtension(pkgName)
            updatePendingUpdatesCount()
        }
    }

    /**
     * Derives what the store listings say about an installed extension. Without any listing there's
     * nothing to derive from, so it's left as is rather than being marked obsolete.
     */
    private fun AnimeExtension.Loaded.withStatus(
        availableExtensions: List<AnimeExtension.Available>,
    ): AnimeExtension.Loaded {
        if (availableExtensions.isEmpty()) return this
        val listing = findListing(availableExtensions)
        return copy(
            hasUpdate = findUpdate(availableExtensions) != null,
            isObsolete = listing == null,
            store = listing?.store ?: store,
        )
    }

    private fun updatePendingUpdatesCount() {
        val pendingUpdateCount = loadedExtensionsMapFlow.value.values.count { it.hasUpdate }
        preferences.animeExtensionUpdatesCount.set(pendingUpdateCount)
        if (pendingUpdateCount == 0) {
            extensionUpdateNotifier.dismiss()
        }
    }

    private operator fun <T : AnimeExtension> Map<String, T>.plus(extension: T) = plus(extension.pkgName to extension)

    /**
     * Extensions are loaded in the background, so this flow only starts emitting once that finished.
     */
    private fun <T : AnimeExtension> StateFlow<Map<String, T>>.mapExtensionsWhenInitialized(): Flow<List<T>> {
        return onStart { initialized.await() }.map { it.values.toList() }
    }
}
