package eu.kanade.tachiyomi.ui.browse.manga.extension

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class MangaExtensionsViewModel(
    private val context: Context,
    private val preferences: SourcePreferences,
    basePreferences: BasePreferences,
    private val extensionManager: MangaExtensionManager,
    private val getExtensions: GetMangaExtensionsByType,
) : ViewModel() {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())

    // Public so BrowseTab's search bar can observe it without subscribing to the whole state.
    val searchQuery: StateFlow<String?>
        field = MutableStateFlow(null)

    // Public so the tab badge can observe it without subscribing to the whole state.
    val updatesCount = preferences.extensionUpdatesCount.changes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), 0)

    private val isRefreshing = MutableStateFlow(false)

    private fun extensionMapper(map: Map<String, InstallStep>): (MangaExtension) -> MangaExtensionUiModel.Item = {
        MangaExtensionUiModel.Item(it, map[it.pkgName] ?: InstallStep.Idle)
    }

    @Suppress("LocalVariableName")
    private val items = combine(
        searchQuery
            .debounce(0.25.seconds)
            .map { searchQueryPredicate(it ?: "") },
        currentDownloads,
        getExtensions.subscribe(),
    ) { predicate, downloads, (_updates, _installed, _available, _untrusted) ->
        buildMap {
            val updates = _updates.filter(predicate).map(extensionMapper(downloads))
            if (updates.isNotEmpty()) {
                put(MangaExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending), updates)
            }

            val installed = _installed.filter(predicate).map(extensionMapper(downloads))
            val untrusted = _untrusted.filter(predicate).map(extensionMapper(downloads))
            if (installed.isNotEmpty() || untrusted.isNotEmpty()) {
                put(MangaExtensionUiModel.Header.Resource(MR.strings.ext_installed), installed + untrusted)
            }

            val languagesWithExtensions = _available
                .filter(predicate)
                .groupBy { it.lang }
                .toSortedMap(LocaleHelper.comparator)
                .map { (lang, exts) ->
                    MangaExtensionUiModel.Header.Text(LocaleHelper.getSourceDisplayName(lang, context)) to
                        exts.map(extensionMapper(downloads))
                }
            if (languagesWithExtensions.isNotEmpty()) {
                putAll(languagesWithExtensions)
            }
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    val state: StateFlow<State> = combine(
        items,
        searchQuery,
        isRefreshing,
        preferences.extensionUpdatesCount.changes(),
        basePreferences.extensionInstaller.changes(),
    ) { items, searchQuery, isRefreshing, updates, installer ->
        State(
            isLoading = items == null,
            isRefreshing = isRefreshing,
            items = items.orEmpty(),
            updates = updates,
            installer = installer,
            searchQuery = searchQuery,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    init {

        viewModelScope.launchIO { findAvailableExtensions() }
    }

    fun searchQueryPredicate(query: String): (MangaExtension) -> Boolean {
        val subqueries = query.split(",")
            .map { it.trim() }
            .filterNot { it.isBlank() }

        if (subqueries.isEmpty()) return { true }

        return { extension ->
            subqueries.any { subquery ->
                if (extension.name.contains(subquery, ignoreCase = true)) return@any true

                when (extension) {
                    is MangaExtension.Installed -> extension.sources.any { source ->
                        source.name.contains(subquery, ignoreCase = true) ||
                            (source as? HttpSource)?.baseUrl?.contains(subquery, ignoreCase = true) == true ||
                            source.id == subquery.toLongOrNull()
                    }

                    is MangaExtension.Available -> extension.sources.any {
                        it.name.contains(subquery, ignoreCase = true) ||
                            it.baseUrl.contains(subquery, ignoreCase = true) ||
                            it.id == subquery.toLongOrNull()
                    }

                    is MangaExtension.Untrusted -> extension.name.contains(subquery, ignoreCase = true)
                }
            }
        }
    }

    fun search(query: String?) {
        searchQuery.update { query }
    }

    fun updateAllExtensions() {
        viewModelScope.launchIO {
            state.value.items.values.flatten()
                .map { it.extension }
                .filterIsInstance<MangaExtension.Installed>()
                .filter { it.hasUpdate }
                .forEach(::updateExtension)
        }
    }

    fun installExtension(extension: MangaExtension.Available) {
        viewModelScope.launchIO {
            extensionManager.installExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun updateExtension(extension: MangaExtension.Installed) {
        viewModelScope.launchIO {
            extensionManager.updateExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun cancelInstallUpdateExtension(extension: MangaExtension) {
        extensionManager.cancelInstallUpdateExtension(extension)
    }

    private fun addDownloadState(extension: MangaExtension, installStep: InstallStep) {
        currentDownloads.update { it + Pair(extension.pkgName, installStep) }
    }

    private fun removeDownloadState(extension: MangaExtension) {
        currentDownloads.update { it - extension.pkgName }
    }

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: MangaExtension) =
        this
            .onEach { installStep -> addDownloadState(extension, installStep) }
            .takeWhile { installStep -> installStep != InstallStep.Installed }
            .onCompletion { removeDownloadState(extension) }
            .collect()

    fun uninstallExtension(extension: MangaExtension) {
        extensionManager.uninstallExtension(extension)
    }

    fun findAvailableExtensions() {
        viewModelScope.launchIO {
            isRefreshing.update { true }

            extensionManager.findAvailableExtensions()

            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.seconds)

            isRefreshing.update { false }
        }
    }

    fun trustExtension(extension: MangaExtension.Untrusted) {
        viewModelScope.launch {
            extensionManager.trust(extension)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ItemGroups = mutableMapOf(),
        val updates: Int = 0,
        val installer: BasePreferences.ExtensionInstaller? = null,
        val searchQuery: String? = null,
    ) {
        val isEmpty = items.isEmpty()
    }
}

typealias ItemGroups = Map<MangaExtensionUiModel.Header, List<MangaExtensionUiModel.Item>>

object MangaExtensionUiModel {
    sealed interface Header {
        data class Resource(val textRes: StringResource) : Header
        data class Text(val text: String) : Header
    }

    data class Item(
        val extension: MangaExtension,
        val installStep: InstallStep,
    )
}
