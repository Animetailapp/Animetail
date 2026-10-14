package eu.kanade.tachiyomi.extension.manga

import android.content.Context
import android.graphics.drawable.Drawable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionInstaller
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.extension.manga.interactor.UpdateMangaExtensionStores
import mihon.domain.extension.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.i18n.MR
import java.util.Locale

@Inject
@SingleIn(AppScope::class)
class MangaExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences,
    private val trustExtension: TrustMangaExtension,
    private val installer: MangaExtensionInstaller,
    private val extensionStoreRepository: MangaExtensionStoreRepository,
    private val updateExtensionStores: UpdateMangaExtensionStores,
    private val extensionUpdateNotifier: ExtensionUpdateNotifier,
) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initialized = CompletableDeferred<Unit>()

    private val iconMap = mutableMapOf<String, Drawable>()

    private val loadedExtensionsMapFlow = MutableStateFlow(emptyMap<String, MangaExtension.Loaded>())
    val loadedExtensionsFlow = loadedExtensionsMapFlow.mapExtensionsWhenInitialized()

    val loadedExtensions: List<MangaExtension.Loaded>
        get() = loadedExtensionsMapFlow.value.values.toList()

    @Volatile
    private var stores = emptyList<ExtensionStore>()

    // Every store's listing, since more than one store can list the same extension
    private val availableExtensionListFlow = MutableStateFlow(emptyList<MangaExtension.Available>())

    // Stores sharing a signing key serve the same apks, so only the newest of their listings is shown. Stores
    // with different keys offer different apks, each installable, so each keeps its own.
    val availableExtensionsFlow = availableExtensionListFlow
        .map { extensions ->
            extensions
                .groupBy { it.pkgName to it.store.signingKey }
                .values
                .map { listings ->
                    listings.maxWith(compareBy<MangaExtension.Available> { it.versionCode }.thenBy { it.libVersion })
                }
        }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val notLoadedExtensionsMapFlow = MutableStateFlow(emptyMap<String, MangaExtension.NotLoaded>())
    val notLoadedExtensionsFlow = notLoadedExtensionsMapFlow.mapExtensionsWhenInitialized()

    private val _installerCancelEvents = MutableSharedFlow<Long>()
    val installerCancelEvents = _installerCancelEvents.asSharedFlow()

    init {
        scope.launch(Dispatchers.IO) {
            loadMangaExtensions()
            MangaExtensionInstallReceiver(MangaInstallationListener()).register(context)

            // Everything the load decision rests on can change while running, so decide again
            merge(
                trustExtension.changes(),
                preferences.enabledContentWarnings.changes().distinctUntilChanged().drop(1).map {},
                preferences.applyContentWarningsToInstalled.changes().distinctUntilChanged().drop(1).map {},
            )
                .collectLatest { loadMangaExtensions() }
        }

        // Extensions are only reloaded when the set of signing keys changes, which misses a store being
        // removed while another with the same key stays, or a store being renamed
        scope.launch(Dispatchers.IO) {
            initialized.await()
            extensionStoreRepository.getAllAsFlow().collect(::assignStores)
        }
    }

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages.isSet()

    val installedExtensions: List<MangaExtension.Installed>
        get() = loadedExtensionsMapFlow.value.values.toList() + notLoadedExtensionsMapFlow.value.values.toList()

    suspend fun getLoadedExtensions(): List<MangaExtension.Loaded> {
        initialized.await()
        return loadedExtensionsMapFlow.value.values.toList()
    }

    suspend fun getNotLoadedExtensions(): List<MangaExtension.NotLoaded> {
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
            MangaExtensionLoader.getMangaExtensionPackageInfoFromPkgName(context, pkgName)!!
                .applicationInfo!!
                .loadIcon(context.packageManager)
        }
    }

    private var availableMangaExtensionsSourcesData: Map<Long, StubMangaSource> = emptyMap()

    private fun setupAvailableMangaExtensionsSourcesDataMap(
        extensions: List<MangaExtension.Available>,
    ) {
        if (extensions.isEmpty()) return
        availableMangaExtensionsSourcesData = extensions
            .flatMap { ext -> ext.sources.map { it.toStubSource() } }
            .associateBy { it.id }
    }

    fun getSourceData(id: Long) = availableMangaExtensionsSourcesData[id]

    /**
     * Loads and registers the installed extensions. Safe to call again: every extension is judged
     * again, so one can move between loaded and not loaded in either direction, while extensions
     * that still pass keep the instances they already had.
     */
    private suspend fun loadMangaExtensions() {
        try {
            val extensions = MangaExtensionLoader.loadMangaExtensions(context, loadedExtensionsMapFlow.value)

            loadedExtensionsMapFlow.value = extensions
                .filterIsInstance<MangaExtension.Loaded>()
                .associateBy { it.pkgName }

            notLoadedExtensionsMapFlow.value = extensions
                .filterIsInstance<MangaExtension.NotLoaded>()
                .associateBy { it.pkgName }

            // Newly loaded extensions have no status derived from the store index yet
            refreshStatuses()
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to load extensions" }
        } finally {
            // Release anything waiting on the extensions whether or not the load worked
            initialized.complete(Unit)
        }
    }

    /**
     * Finds the available extensions in the stores and updates [availableExtensionListFlow].
     */
    suspend fun findAvailableExtensions() {
        val extensions: List<MangaExtension.Available> = try {
            fetchExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            withUIContext { context.toast(MR.strings.extension_api_error) }
            return
        }

        setAvailableExtensions(extensions)
    }

    /**
     * Refreshes the stores and their listings like [findAvailableExtensions], then notifies about the
     * installed extensions that have an update. Stays silent when the stores can't be reached.
     */
    suspend fun checkForUpdates() {
        val extensions = try {
            updateExtensionStores()
            fetchExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return
        }

        initialized.await()
        setAvailableExtensions(extensions)

        val names = (loadedExtensionsMapFlow.value.values + notLoadedExtensionsMapFlow.value.values)
            .filter { it.hasUpdate }
            .map { it.name }
        if (names.isNotEmpty()) {
            extensionUpdateNotifier.promptUpdates(names)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchExtensions(): List<MangaExtension.Available> {
        return withIOContext { extensionStoreRepository.fetchExtensions() as List<MangaExtension.Available> }
    }

    private fun setAvailableExtensions(extensions: List<MangaExtension.Available>) {
        enableAdditionalSubLanguages(extensions)

        availableExtensionListFlow.value = extensions
        refreshStatuses()
        setupAvailableMangaExtensionsSourcesDataMap(extensions)
    }

    private fun enableAdditionalSubLanguages(extensions: List<MangaExtension.Available>) {
        if (subLanguagesEnabledOnFirstRun || extensions.isEmpty()) {
            return
        }

        val availableLanguages = extensions
            .flatMap(MangaExtension.Available::sources)
            .distinctBy(MangaExtension.Available.MangaSource::lang)
            .map(MangaExtension.Available.MangaSource::lang)

        val deviceLanguage = Locale.getDefault().language
        val defaultLanguages = preferences.enabledLanguages.defaultValue()
        val languagesToEnable = availableLanguages.filter {
            it != deviceLanguage && it.startsWith(deviceLanguage)
        }

        preferences.enabledLanguages.set(defaultLanguages + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    fun installExtension(extension: MangaExtension.Available): Flow<InstallStep> {
        return installer.downloadAndInstall(extension)
    }

    fun updateExtension(extension: MangaExtension.Installed): Flow<InstallStep> {
        val update = extension.findUpdate(availableExtensionListFlow.value) ?: return emptyFlow()
        val isUpdateForPrivatelyInstalled = !extension.isShared
        return installer.downloadAndInstall(update, isUpdateForPrivatelyInstalled)
    }

    fun cancelInstallUpdateExtension(extension: MangaExtension) {
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

    fun uninstallExtension(extension: MangaExtension.Installed) {
        installer.uninstallApk(extension.pkgName)
    }

    fun trust(extension: MangaExtension.NotLoaded) {
        val reason = extension.reason as? MangaExtension.NotLoaded.Reason.Untrusted ?: return
        notLoadedExtensionsMapFlow.value[extension.pkgName] ?: return

        // Loading it again is left to the reload triggered by the trust change
        trustExtension.trust(extension.pkgName, extension.versionCode, reason.signatureHash)
    }

    private fun registerExtension(extension: MangaExtension.Loaded) {
        loadedExtensionsMapFlow.value += extension
    }

    private fun unregisterMangaExtension(pkgName: String) {
        loadedExtensionsMapFlow.value -= pkgName
        notLoadedExtensionsMapFlow.value -= pkgName
    }

    private inner class MangaInstallationListener : MangaExtensionInstallReceiver.Listener {

        override fun onExtensionLoaded(extension: MangaExtension.Loaded) {
            registerExtension(extension)
            notLoadedExtensionsMapFlow.value -= extension.pkgName
            refreshStatuses()
        }

        override fun onExtensionNotLoaded(extension: MangaExtension.NotLoaded) {
            loadedExtensionsMapFlow.value -= extension.pkgName
            notLoadedExtensionsMapFlow.value += extension
            refreshStatuses()
        }

        override fun onPackageUninstalled(pkgName: String) {
            MangaExtensionLoader.uninstallPrivateExtension(context, pkgName)
            unregisterMangaExtension(pkgName)
            updatePendingUpdatesCount()
        }
    }

    /**
     * Derives what the store listings say about every installed extension, loaded or not. Without any
     * listing there's nothing to derive from, so they're left as they are rather than marked obsolete.
     */
    private fun refreshStatuses() {
        val available = availableExtensionListFlow.value
        if (available.isNotEmpty()) {
            loadedExtensionsMapFlow.value = loadedExtensionsMapFlow.value.mapValues { (_, extension) ->
                val listing = extension.findListing(available)
                extension.copy(
                    hasUpdate = extension.findUpdate(available) != null,
                    isObsolete = listing == null,
                    store = if (stores.isEmpty()) extension.store else extension.pickStore(),
                )
            }
            notLoadedExtensionsMapFlow.value = notLoadedExtensionsMapFlow.value.mapValues { (_, extension) ->
                extension.copy(
                    hasUpdate = extension.findUpdate(available) != null,
                    store = if (stores.isEmpty()) extension.store else extension.pickStore(),
                )
            }
        }
        updatePendingUpdatesCount()
    }

    /**
     * Several stores can share a signing key, so one that lists the extension names where it comes from better
     * than whichever of them was added first.
     */
    private fun MangaExtension.Installed.pickStore(): ExtensionStore? {
        val signingStores = stores.filter { it.signingKey in signatures }
        val listedBy = availableExtensionListFlow.value
            .filter { it.pkgName == pkgName }
            .mapTo(HashSet()) { it.store.indexUrl }
        return signingStores.firstOrNull { it.indexUrl in listedBy } ?: signingStores.firstOrNull()
    }

    private fun assignStores(stores: List<ExtensionStore>) {
        this.stores = stores
        loadedExtensionsMapFlow.update { extensions ->
            extensions.mapValues { (_, extension) -> extension.copy(store = extension.pickStore()) }
        }
        notLoadedExtensionsMapFlow.update { extensions ->
            extensions.mapValues { (_, extension) -> extension.copy(store = extension.pickStore()) }
        }
    }

    private fun updatePendingUpdatesCount() {
        val pendingUpdateCount = (loadedExtensionsMapFlow.value.values + notLoadedExtensionsMapFlow.value.values)
            .count { it.hasUpdate }
        preferences.extensionUpdatesCount.set(pendingUpdateCount)
        if (pendingUpdateCount == 0) {
            extensionUpdateNotifier.dismiss()
        }
    }

    private operator fun <T : MangaExtension> Map<String, T>.plus(extension: T) = plus(extension.pkgName to extension)

    /**
     * Extensions are loaded in the background, so this flow only starts emitting once that finished.
     */
    private fun <T : MangaExtension> StateFlow<Map<String, T>>.mapExtensionsWhenInitialized(): Flow<List<T>> {
        return onStart { initialized.await() }.map { it.values.toList() }
    }
}
