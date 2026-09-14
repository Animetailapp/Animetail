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
import eu.kanade.tachiyomi.extension.manga.api.MangaExtensionApi
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.LogPriority
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
    private val api: MangaExtensionApi,
    private val extensionUpdateNotifier: ExtensionUpdateNotifier,
) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initialized = CompletableDeferred<Unit>()

    private val iconMap = mutableMapOf<String, Drawable>()

    private val loadedExtensionsMapFlow = MutableStateFlow(emptyMap<String, MangaExtension.Loaded>())
    val loadedExtensionsFlow = loadedExtensionsMapFlow.mapExtensionsWhenInitialized()

    val loadedExtensions: List<MangaExtension.Loaded>
        get() = loadedExtensionsMapFlow.value.values.toList()

    private val availableExtensionsMapFlow = MutableStateFlow(emptyMap<String, MangaExtension.Available>())
    val availableExtensionsFlow = availableExtensionsMapFlow.mapExtensions(scope)

    private val notLoadedExtensionsMapFlow = MutableStateFlow(emptyMap<String, MangaExtension.NotLoaded>())
    val notLoadedExtensionsFlow = notLoadedExtensionsMapFlow.mapExtensionsWhenInitialized()

    private val _installerCancelEvents = MutableSharedFlow<Long>()
    val installerCancelEvents = _installerCancelEvents.asSharedFlow()

    init {
        scope.launch(Dispatchers.IO) {
            initMangaExtensions()
            MangaExtensionInstallReceiver(MangaInstallationListener()).register(context)
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

    private fun initMangaExtensions() {
        try {
            val extensions = MangaExtensionLoader.loadMangaExtensions(context)

            loadedExtensionsMapFlow.value = extensions
                .filterIsInstance<MangaExtension.Loaded>()
                .associateBy { it.pkgName }

            notLoadedExtensionsMapFlow.value = extensions
                .filterIsInstance<MangaExtension.NotLoaded>()
                .associateBy { it.pkgName }

            initialized.complete(Unit)
        } catch (e: Throwable) {
            initialized.complete(Unit)
            throw e
        }
    }

    suspend fun findAvailableExtensions() {
        val extensions: List<MangaExtension.Available> = try {
            api.findExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            withUIContext { context.toast(MR.strings.extension_api_error) }
            emptyList()
        }

        enableAdditionalSubLanguages(extensions)

        availableExtensionsMapFlow.value = extensions.associateBy { it.pkgName }
        updatedInstalledMangaExtensionsStatuses(extensions)
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

    private fun updatedInstalledMangaExtensionsStatuses(
        availableExtensions: List<MangaExtension.Available>,
    ) {
        val noExtAvailable = availableExtensions.isEmpty()

        val loadedExtensionsMap = loadedExtensionsMapFlow.value.toMutableMap()
        var changed = false

        for ((pkgName, extension) in loadedExtensionsMap) {
            val availableExt = availableExtensions.find { it.pkgName == pkgName }

            if (availableExt == null) {
                val isObsolete = !noExtAvailable && !extension.isObsolete
                loadedExtensionsMap[pkgName] = extension.copy(
                    isObsolete = isObsolete,
                    hasUpdate = false,
                )
                changed = changed || isObsolete || (noExtAvailable && (extension.isObsolete || extension.hasUpdate))
            } else {
                val hasUpdate = extension.updateExists(availableExt)
                loadedExtensionsMap[pkgName] = if (extension.hasUpdate != hasUpdate) {
                    extension.copy(
                        hasUpdate = hasUpdate,
                        store = availableExt.store,
                    )
                } else {
                    extension.copy(
                        store = availableExt.store,
                    )
                }
                changed = true
            }
        }

        if (changed) {
            loadedExtensionsMapFlow.value = loadedExtensionsMap
        }
        updatePendingUpdatesCount()
    }

    fun installExtension(extension: MangaExtension.Available): Flow<InstallStep> {
        return installer.downloadAndInstall(extension.apkUrl, extension)
    }

    fun updateExtension(extension: MangaExtension.Loaded): Flow<InstallStep> {
        val availableExt = availableExtensionsMapFlow.value[extension.pkgName] ?: return emptyFlow()
        val isUpdateForPrivatelyInstalled = !extension.isShared
        return installer.downloadAndInstall(availableExt.apkUrl, availableExt, isUpdateForPrivatelyInstalled)
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

    suspend fun trust(extension: MangaExtension.NotLoaded) {
        val reason = extension.reason as? MangaExtension.NotLoaded.Reason.Untrusted ?: return
        notLoadedExtensionsMapFlow.value[extension.pkgName] ?: return

        trustExtension.trust(extension.pkgName, extension.versionCode, reason.signatureHash)

        notLoadedExtensionsMapFlow.value -= extension.pkgName

        when (val reloaded = MangaExtensionLoader.loadMangaExtensionFromPkgName(context, extension.pkgName)) {
            is MangaExtension.Loaded -> registerExtension(reloaded)
            is MangaExtension.NotLoaded -> notLoadedExtensionsMapFlow.value += reloaded
            null -> {}
        }
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
            registerExtension(extension.withUpdateCheck())
            notLoadedExtensionsMapFlow.value -= extension.pkgName
            updatePendingUpdatesCount()
        }

        override fun onExtensionNotLoaded(extension: MangaExtension.NotLoaded) {
            loadedExtensionsMapFlow.value -= extension.pkgName
            notLoadedExtensionsMapFlow.value += extension
            updatePendingUpdatesCount()
        }

        override fun onPackageUninstalled(pkgName: String) {
            MangaExtensionLoader.uninstallPrivateExtension(context, pkgName)
            unregisterMangaExtension(pkgName)
            updatePendingUpdatesCount()
        }
    }

    private fun MangaExtension.Loaded.withUpdateCheck(): MangaExtension.Loaded {
        return if (updateExists()) {
            copy(hasUpdate = true)
        } else {
            this
        }
    }

    private fun MangaExtension.Loaded.updateExists(
        availableExtension: MangaExtension.Available? = null,
    ): Boolean {
        val availableExt = availableExtension
            ?: availableExtensionsMapFlow.value[pkgName]
            ?: return false

        return availableExt.versionCode > versionCode || availableExt.libVersion > libVersion
    }

    private fun updatePendingUpdatesCount() {
        val pendingUpdateCount = loadedExtensionsMapFlow.value.values.count { it.hasUpdate }
        preferences.extensionUpdatesCount.set(pendingUpdateCount)
        if (pendingUpdateCount == 0) {
            extensionUpdateNotifier.dismiss()
        }
    }

    private operator fun <T : MangaExtension> Map<String, T>.plus(extension: T) = plus(extension.pkgName to extension)

    private fun <T : MangaExtension> StateFlow<Map<String, T>>.mapExtensions(
        scope: CoroutineScope,
    ): StateFlow<List<T>> {
        return map { it.values.toList() }.stateIn(scope, SharingStarted.Lazily, value.values.toList())
    }

    /**
     * Extensions are loaded in the background, so this flow only starts emitting once that finished.
     */
    private fun <T : MangaExtension> StateFlow<Map<String, T>>.mapExtensionsWhenInitialized(): Flow<List<T>> {
        return onStart { initialized.await() }.map { it.values.toList() }
    }
}
