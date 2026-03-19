package eu.kanade.tachiyomi.ui.browse.manga.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class MangaExtensionsScreenModel(
    preferences: SourcePreferences = Injekt.get(),
    basePreferences: BasePreferences = Injekt.get(),
    private val extensionManager: MangaExtensionManager = Injekt.get(),
    private val getExtensions: GetMangaExtensionsByType = Injekt.get(),
) : StateScreenModel<MangaExtensionsScreenModel.State>(State()) {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())

    init {
        val context = Injekt.get<Application>()
        val extensionMapper: (Map<String, InstallStep>) -> ((MangaExtension) -> MangaExtensionUiModel.Item) = { map ->
            {
                MangaExtensionUiModel.Item(it, map[it.pkgName] ?: InstallStep.Idle)
            }
        }

        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }
                    .distinctUntilChanged()
                    .debounce(SEARCH_DEBOUNCE_MILLIS)
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
                .collectLatest { items ->
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            items = items,
                        )
                    }
                }
        }

        screenModelScope.launchIO { findAvailableExtensions() }

        preferences.mangaExtensionUpdatesCount().changes()
            .onEach { mutableState.update { state -> state.copy(updates = it) } }
            .launchIn(screenModelScope)

        basePreferences.extensionInstaller().changes()
            .onEach { mutableState.update { state -> state.copy(installer = it) } }
            .launchIn(screenModelScope)
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
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            state.value.items.values.flatten()
                .map { it.extension }
                .filterIsInstance<MangaExtension.Installed>()
                .filter { it.hasUpdate }
                .forEach(::updateExtension)
        }
    }

    fun installExtension(extension: MangaExtension.Available) {
        screenModelScope.launchIO {
            extensionManager.installExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun updateExtension(extension: MangaExtension.Installed) {
        screenModelScope.launchIO {
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
            .onCompletion { removeDownloadState(extension) }
            .collect()

    fun uninstallExtension(extension: MangaExtension) {
        extensionManager.uninstallExtension(extension)
    }

    fun findAvailableExtensions() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }

            extensionManager.findAvailableExtensions()

            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.seconds)

            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun trustExtension(extension: MangaExtension.Untrusted) {
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
