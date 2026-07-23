package eu.kanade.tachiyomi.ui.browse.manga.extension.details

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.kanade.domain.extension.manga.interactor.GetExtensionSources
import eu.kanade.domain.extension.manga.interactor.MangaExtensionSourceItem
import eu.kanade.domain.source.manga.interactor.ToggleMangaIncognito
import eu.kanade.domain.source.manga.interactor.ToggleMangaSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.viewmodel.StateViewModel
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaExtensionDetailsViewModel(
    pkgName: String,
    context: Context,
    private val network: NetworkHelper = Injekt.get(),
    private val extensionManager: MangaExtensionManager = Injekt.get(),
    private val getExtensionSources: GetExtensionSources = Injekt.get(),
    private val toggleSource: ToggleMangaSource = Injekt.get(),
    private val toggleIncognito: ToggleMangaIncognito = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : StateViewModel<MangaExtensionDetailsViewModel.State>(State()) {

    companion object {
        val PKG_NAME_KEY = CreationExtras.Key<String>()

        val Factory = viewModelFactory {
            initializer {
                MangaExtensionDetailsViewModel(
                    pkgName = get(PKG_NAME_KEY)!!,
                    context = Injekt.get<Application>(),
                )
            }
        }
    }

    private val _events: Channel<MangaExtensionDetailsEvent> = Channel()
    val events: Flow<MangaExtensionDetailsEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            launch {
                extensionManager.installedExtensionsFlow
                    .map { it.firstOrNull { extension -> extension.pkgName == pkgName } }
                    .collectLatest { extension ->
                        if (extension == null) {
                            _events.send(MangaExtensionDetailsEvent.Uninstalled)
                            return@collectLatest
                        }
                        mutableState.update { state ->
                            state.copy(extension = extension)
                        }
                    }
            }
            launch {
                state.collectLatest { state ->
                    if (state.extension == null) return@collectLatest
                    getExtensionSources.subscribe(state.extension)
                        .map {
                            it.sortedWith(
                                compareBy(
                                    { !it.enabled },
                                    { item ->
                                        item.source.name.takeIf { item.labelAsName }
                                            ?: LocaleHelper.getSourceDisplayName(
                                                item.source.lang,
                                                context,
                                            ).lowercase()
                                    },
                                ),
                            )
                        }
                        .catch { throwable ->
                            logcat(LogPriority.ERROR, throwable)
                            mutableState.update { it.copy(_sources = listOf()) }
                        }
                        .collectLatest { sources ->
                            mutableState.update { it.copy(_sources = sources.toList()) }
                        }
                }
            }
            launch {
                preferences.incognitoMangaExtensions
                    .changes()
                    .map { pkgName in it }
                    .distinctUntilChanged()
                    .collectLatest { isIncognito ->
                        mutableState.update { it.copy(isIncognito = isIncognito) }
                    }
            }
        }
    }

    fun clearCookies() {
        val extension = state.value.extension ?: return

        val urls = extension.sources
            .filterIsInstance<HttpSource>()
            .mapNotNull { it.baseUrl.takeUnless { url -> url.isEmpty() } }
            .distinct()

        val cleared = urls.sumOf {
            try {
                network.cookieJar.remove(it.toHttpUrl())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to clear cookies for $it" }
                0
            }
        }

        logcat { "Cleared $cleared cookies for: ${urls.joinToString()}" }
    }

    fun uninstallExtension() {
        val extension = state.value.extension ?: return
        extensionManager.uninstallExtension(extension)
    }

    fun toggleSource(sourceId: Long) {
        toggleSource.await(sourceId)
    }

    fun toggleSources(enable: Boolean) {
        state.value.extension?.sources
            ?.map { it.id }
            ?.let { toggleSource.await(it, enable) }
    }

    fun toggleIncognito(enable: Boolean) {
        state.value.extension?.pkgName?.let { packageName ->
            toggleIncognito.await(packageName, enable)
        }
    }

    @Immutable
    data class State(
        val extension: MangaExtension.Installed? = null,
        val isIncognito: Boolean = false,
        private val _sources: List<MangaExtensionSourceItem>? = null,
    ) {

        val sources: List<MangaExtensionSourceItem>
            get() = _sources ?: listOf()

        val isLoading: Boolean
            get() = extension == null || _sources == null
    }
}

sealed interface MangaExtensionDetailsEvent {
    data object Uninstalled : MangaExtensionDetailsEvent
}
