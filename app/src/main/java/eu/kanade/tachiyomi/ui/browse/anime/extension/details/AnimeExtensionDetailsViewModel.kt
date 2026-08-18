package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.extension.anime.interactor.AnimeExtensionSourceItem
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionSources
import eu.kanade.domain.source.anime.interactor.ToggleAnimeIncognito
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class AnimeExtensionDetailsViewModel(
    @Assisted private val pkgName: String,
    private val context: Context,
    private val network: NetworkHelper,
    private val extensionManager: AnimeExtensionManager,
    private val getExtensionSources: GetAnimeExtensionSources,
    private val toggleSource: ToggleAnimeSource,
    private val toggleIncognito: ToggleAnimeIncognito,
    private val preferences: SourcePreferences,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(pkgName: String): AnimeExtensionDetailsViewModel
    }

    val state: StateFlow<State> = extensionManager.installedExtensionsFlow
        .map { it.firstOrNull { extension -> extension.pkgName == pkgName } }
        .distinctUntilChanged()
        .flatMapLatest { extension ->
            if (extension == null) return@flatMapLatest flowOf(State.Uninstalled)
            combine(
                subscribeToSources(extension),
                preferences.incognitoAnimeExtensions.changes().map { pkgName in it }.distinctUntilChanged(),
            ) { sources, isIncognito ->
                State.Success(extension = extension, isIncognito = isIncognito, sources = sources)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State.Loading)

    fun clearCookies() {
        val extension = (state.value as? State.Success)?.extension ?: return

        val urls = extension.sources
            .filterIsInstance<AnimeHttpSource>()
            .mapNotNull { it.baseUrl.takeUnless { url -> url.isEmpty() } }
            .distinct()

        val cookieJar = network.cookieJar
        urls.forEach {
            try {
                cookieJar.remove(it.toHttpUrl())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to clear cookies for $it" }
            }
        }
    }

    fun uninstallExtension() {
        val extension = (state.value as? State.Success)?.extension ?: return
        extensionManager.uninstallExtension(extension)
    }

    fun toggleSource(sourceId: Long) {
        toggleSource.await(sourceId)
    }

    fun toggleSources(enable: Boolean) {
        (state.value as? State.Success)?.extension?.sources
            ?.map { it.id }
            ?.let { toggleSource.await(it, enable) }
    }

    fun toggleIncognito(isIncognito: Boolean) {
        toggleIncognito.await(pkgName, isIncognito)
    }

    private fun subscribeToSources(extension: AnimeExtension.Installed): Flow<ImmutableList<AnimeExtensionSourceItem>> {
        return getExtensionSources.subscribe(extension)
            .catch { throwable ->
                logcat(LogPriority.ERROR, throwable)
                emit(emptyList())
            }
            .map { sources ->
                sources
                    .sortedWith(
                        compareBy(
                            { !it.enabled },
                            { item ->
                                item.source.name.takeIf { item.labelAsName }
                                    ?: LocaleHelper.getSourceDisplayName(item.source.lang, context)
                            },
                        ),
                    )
                    .toImmutableList()
            }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data object Uninstalled : State

        @Immutable
        data class Success(
            val extension: AnimeExtension.Installed,
            val isIncognito: Boolean,
            val sources: ImmutableList<AnimeExtensionSourceItem> = persistentListOf(),
        ) : State
    }
}
