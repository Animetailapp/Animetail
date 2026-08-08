package eu.kanade.tachiyomi.ui.browse.anime.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class AnimeExtensionsScreenModel(
    private val preferences: SourcePreferences = Injekt.get(),
    basePreferences: BasePreferences = Injekt.get(),
    private val extensionManager: AnimeExtensionManager = Injekt.get(),
    private val getExtensions: GetAnimeExtensionsByType = Injekt.get(),
) : ScreenModel {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())

    private val context = Injekt.get<Application>()

    // Public so BrowseTab's search bar can observe it without subscribing to the whole state.
    val searchQuery: StateFlow<String?>
        field = MutableStateFlow(null)

    // Public so the tab badge can observe it without subscribing to the whole state.
    val updatesCount = preferences.animeExtensionUpdatesCount.changes()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5.seconds), 0)

    private val isRefreshing = MutableStateFlow(false)

    private fun extensionMapper(map: Map<String, InstallStep>): (AnimeExtension) -> AnimeExtensionUiModel.Item = {
        AnimeExtensionUiModel.Item(it, map[it.pkgName] ?: InstallStep.Idle)
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
                put(AnimeExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending), updates)
            }

            val installed = _installed.filter(predicate).map(extensionMapper(downloads))
            val untrusted = _untrusted.filter(predicate).map(extensionMapper(downloads))
            if (installed.isNotEmpty() || untrusted.isNotEmpty()) {
                put(AnimeExtensionUiModel.Header.Resource(MR.strings.ext_installed), installed + untrusted)
            }

            val languagesWithExtensions = _available
                .filter(predicate)
                .groupBy { it.lang }
                .toSortedMap(LocaleHelper.comparator)
                .map { (lang, exts) ->
                    AnimeExtensionUiModel.Header.Text(LocaleHelper.getSourceDisplayName(lang, context)) to
                        exts.map(extensionMapper(downloads))
                }
            if (languagesWithExtensions.isNotEmpty()) {
                putAll(languagesWithExtensions)
            }
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    val state: StateFlow<State> = combine(
        items,
        searchQuery,
        isRefreshing,
        preferences.animeExtensionUpdatesCount.changes(),
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
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    init {
        screenModelScope.launchIO { findAvailableExtensions() }
    }

    fun searchQueryPredicate(query: String): (AnimeExtension) -> Boolean {
        val subqueries = query.split(",")
            .map { it.trim() }
            .filterNot { it.isBlank() }

        if (subqueries.isEmpty()) return { true }

        return { extension ->
            subqueries.any { subquery ->
                if (extension.name.contains(subquery, ignoreCase = true)) return@any true

                when (extension) {
                    is AnimeExtension.Installed -> extension.sources.any { source ->
                        source.name.contains(subquery, ignoreCase = true) ||
                            (source as? AnimeHttpSource)?.getHomeUrl()?.contains(subquery, ignoreCase = true) == true ||
                            source.id == subquery.toLongOrNull()
                    }

                    is AnimeExtension.Available -> extension.sources.any {
                        it.name.contains(subquery, ignoreCase = true) ||
                            it.baseUrl.contains(subquery, ignoreCase = true) ||
                            it.id == subquery.toLongOrNull()
                    }

                    is AnimeExtension.Untrusted -> extension.name.contains(subquery, ignoreCase = true)
                }
            }
        }
    }

    fun search(query: String?) {
        searchQuery.update { query }
    }

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            state.value.items.values.flatten()
                .map { it.extension }
                .filterIsInstance<AnimeExtension.Installed>()
                .filter { it.hasUpdate }
                .forEach(::updateExtension)
        }
    }

    fun installExtension(extension: AnimeExtension.Available) {
        screenModelScope.launchIO {
            extensionManager.installExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun updateExtension(extension: AnimeExtension.Installed) {
        screenModelScope.launchIO {
            extensionManager.updateExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun cancelInstallUpdateExtension(extension: AnimeExtension) {
        extensionManager.cancelInstallUpdateExtension(extension)
    }

    private fun addDownloadState(extension: AnimeExtension, installStep: InstallStep) {
        currentDownloads.update { it + Pair(extension.pkgName, installStep) }
    }

    private fun removeDownloadState(extension: AnimeExtension) {
        currentDownloads.update { it - extension.pkgName }
    }

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: AnimeExtension) =
        this
            .onEach { installStep -> addDownloadState(extension, installStep) }
            .onCompletion { removeDownloadState(extension) }
            .collect()

    fun uninstallExtension(extension: AnimeExtension) {
        extensionManager.uninstallExtension(extension)
    }

    fun findAvailableExtensions() {
        screenModelScope.launchIO {
            isRefreshing.update { true }

            extensionManager.findAvailableExtensions()

            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.seconds)

            isRefreshing.update { false }
        }
    }

    fun trustExtension(extension: AnimeExtension.Untrusted) {
        screenModelScope.launch {
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

typealias ItemGroups = MutableMap<AnimeExtensionUiModel.Header, List<AnimeExtensionUiModel.Item>>

object AnimeExtensionUiModel {
    sealed interface Header {
        data class Resource(val textRes: StringResource) : Header
        data class Text(val text: String) : Header
    }
    data class Item(
        val extension: AnimeExtension,
        val installStep: InstallStep,
    )
}
