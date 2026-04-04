package eu.kanade.tachiyomi.extension.manga

import android.content.Context
import android.graphics.drawable.Drawable
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.api.MangaExtensionApi
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionInstaller
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

class MangaExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences = Injekt.get(),
    private val trustExtension: TrustMangaExtension = Injekt.get(),
) {

    val scope = CoroutineScope(SupervisorJob())

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val api = MangaExtensionApi()

    private val installer by lazy { MangaExtensionInstaller(context) }

    private val iconMap = mutableMapOf<String, Drawable>()

    private val installedExtensionsMapFlow = MutableStateFlow(emptyMap<String, MangaExtension.Installed>())
    val installedExtensionsFlow = installedExtensionsMapFlow.mapExtensions(scope)

    private val availableExtensionsMapFlow = MutableStateFlow(emptyMap<String, MangaExtension.Available>())
    val availableExtensionsFlow = availableExtensionsMapFlow.mapExtensions(scope)

    private val untrustedExtensionsMapFlow = MutableStateFlow(emptyMap<String, MangaExtension.Untrusted>())
    val untrustedExtensionsFlow = untrustedExtensionsMapFlow.mapExtensions(scope)

    init {
        initMangaExtensions()
        MangaExtensionInstallReceiver(MangaInstallationListener()).register(context)
    }

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages.isSet()

    fun getExtensionPackage(sourceId: Long): String? {
        return installedExtensionsFlow.value.find { extension ->
            extension.sources.any { it.id == sourceId }
        }?.pkgName
    }

    fun getExtensionPackageAsFlow(sourceId: Long): Flow<String?> {
        return installedExtensionsFlow.map { extensions ->
            extensions.find { extension ->
                extension.sources.any { it.id == sourceId }
            }?.pkgName
        }
    }

    fun getAppIconForSource(sourceId: Long): Drawable? {
        val pkgName = installedExtensionsMapFlow.value.values
            .find { ext -> ext.sources.any { it.id == sourceId } }
            ?.pkgName
            ?: return null

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
        val extensions = MangaExtensionLoader.loadMangaExtensions(context)

        installedExtensionsMapFlow.value = extensions
            .filterIsInstance<MangaLoadResult.Success>()
            .associate { it.extension.pkgName to it.extension }

        untrustedExtensionsMapFlow.value = extensions
            .filterIsInstance<MangaLoadResult.Untrusted>()
            .associate { it.extension.pkgName to it.extension }

        _isInitialized.value = true
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

        val installedExtensionsMap = installedExtensionsMapFlow.value.toMutableMap()
        var changed = false

        for ((pkgName, extension) in installedExtensionsMap) {
            val availableExt = availableExtensions.find { it.pkgName == pkgName }

            if (availableExt == null) {
                val isObsolete = !noExtAvailable && !extension.isObsolete
                installedExtensionsMap[pkgName] = extension.copy(
                    isObsolete = isObsolete,
                    hasUpdate = false,
                )
                changed = changed || isObsolete || (noExtAvailable && (extension.isObsolete || extension.hasUpdate))
            } else {
                val hasUpdate = extension.updateExists(availableExt)
                installedExtensionsMap[pkgName] = if (extension.hasUpdate != hasUpdate) {
                    extension.copy(
                        hasUpdate = hasUpdate,
                        repoUrl = availableExt.repoUrl,
                    )
                } else {
                    extension.copy(
                        repoUrl = availableExt.repoUrl,
                    )
                }
                changed = true
            }
        }

        if (changed) {
            installedExtensionsMapFlow.value = installedExtensionsMap
        }
        updatePendingUpdatesCount()
    }

    fun installExtension(extension: MangaExtension.Available): Flow<InstallStep> {
        return installer.downloadAndInstall(api.getApkUrl(extension), extension)
    }

    fun updateExtension(extension: MangaExtension.Installed): Flow<InstallStep> {
        val availableExt = availableExtensionsMapFlow.value[extension.pkgName] ?: return emptyFlow()
        return installExtension(availableExt)
    }

    fun cancelInstallUpdateExtension(extension: MangaExtension) {
        installer.cancelInstall(extension.pkgName)
    }

    fun setInstalling(downloadId: Long) {
        installer.updateInstallStep(downloadId, InstallStep.Installing)
    }

    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        installer.updateInstallStep(downloadId, step)
    }

    fun uninstallExtension(extension: MangaExtension) {
        installer.uninstallApk(extension.pkgName)
    }

    suspend fun trust(extension: MangaExtension.Untrusted) {
        untrustedExtensionsMapFlow.value[extension.pkgName] ?: return

        trustExtension.trust(extension.pkgName, extension.versionCode, extension.signatureHash)

        untrustedExtensionsMapFlow.value -= extension.pkgName

        MangaExtensionLoader.loadMangaExtensionFromPkgName(context, extension.pkgName)
            .let { it as? MangaLoadResult.Success }
            ?.let { registerNewExtension(it.extension) }
    }

    private fun registerNewExtension(extension: MangaExtension.Installed) {
        installedExtensionsMapFlow.value += extension
    }

    private fun registerUpdatedExtension(extension: MangaExtension.Installed) {
        installedExtensionsMapFlow.value += extension
    }

    private fun unregisterMangaExtension(pkgName: String) {
        installedExtensionsMapFlow.value -= pkgName
        untrustedExtensionsMapFlow.value -= pkgName
    }

    private inner class MangaInstallationListener : MangaExtensionInstallReceiver.Listener {

        override fun onExtensionInstalled(extension: MangaExtension.Installed) {
            registerNewExtension(extension.withUpdateCheck())
            updatePendingUpdatesCount()
        }

        override fun onExtensionUpdated(extension: MangaExtension.Installed) {
            registerUpdatedExtension(extension.withUpdateCheck())
            updatePendingUpdatesCount()
        }

        override fun onExtensionUntrusted(extension: MangaExtension.Untrusted) {
            installedExtensionsMapFlow.value -= extension.pkgName
            untrustedExtensionsMapFlow.value += extension
            updatePendingUpdatesCount()
        }

        override fun onPackageUninstalled(pkgName: String) {
            MangaExtensionLoader.uninstallPrivateExtension(context, pkgName)
            unregisterMangaExtension(pkgName)
            updatePendingUpdatesCount()
        }
    }

    private fun MangaExtension.Installed.withUpdateCheck(): MangaExtension.Installed {
        return if (updateExists()) {
            copy(hasUpdate = true)
        } else {
            this
        }
    }

    private fun MangaExtension.Installed.updateExists(
        availableExtension: MangaExtension.Available? = null,
    ): Boolean {
        val availableExt = availableExtension
            ?: availableExtensionsMapFlow.value[pkgName]
            ?: return false

        return availableExt.versionCode > versionCode || availableExt.libVersion > libVersion
    }

    private fun updatePendingUpdatesCount() {
        val pendingUpdateCount = installedExtensionsMapFlow.value.values.count { it.hasUpdate }
        preferences.extensionUpdatesCount.set(pendingUpdateCount)
        if (pendingUpdateCount == 0) {
            ExtensionUpdateNotifier(context).dismiss()
        }
    }

    private operator fun <T : MangaExtension> Map<String, T>.plus(extension: T) = plus(extension.pkgName to extension)

    private fun <T : MangaExtension> StateFlow<Map<String, T>>.mapExtensions(
        scope: CoroutineScope,
    ): StateFlow<List<T>> {
        return map { it.values.toList() }.stateIn(scope, SharingStarted.Lazily, value.values.toList())
    }
}
