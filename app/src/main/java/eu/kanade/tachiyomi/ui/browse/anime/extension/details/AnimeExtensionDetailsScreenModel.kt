package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import android.content.Context
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class AnimeExtensionDetailsScreenModel(
    pkgName: String,
    private val context: Context,
    private val network: NetworkHelper = Injekt.get(),
    private val extensionManager: AnimeExtensionManager = Injekt.get(),
    private val getExtensionSources: GetAnimeExtensionSources = Injekt.get(),
    private val toggleSource: ToggleAnimeSource = Injekt.get(),
    private val toggleIncognito: ToggleAnimeIncognito = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : ScreenModel {

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
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5.seconds), State.Loading)

    private val successState: State.Success?
        get() = state.value as? State.Success

    private fun subscribeToSources(extension: AnimeExtension.Installed): Flow<ImmutableList<AnimeExtensionSourceItem>> {
        return getExtensionSources.subscribe(extension)
            .map {
                it.sortedWith(
                    compareBy(
                        { !it.enabled },
                        { item ->
                            item.source.name.takeIf { item.labelAsName }
                                ?: LocaleHelper.getSourceDisplayName(item.source.lang, context).lowercase()
                        },
                    ),
                )
                    .toImmutableList()
            }
            .catch { throwable ->
                logcat(LogPriority.ERROR, throwable)
                emit(persistentListOf())
            }
    }

    fun clearCookies() {
        val extension = successState?.extension ?: return

        val urls = extension.sources
            .filterIsInstance<AnimeHttpSource>()
            .flatMap { listOf(it.baseUrl, it.getHomeUrl()) }
            .filter { it.isNotEmpty() }
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
        val extension = successState?.extension ?: return
        extensionManager.uninstallExtension(extension)
    }

    fun toggleSource(sourceId: Long) {
        toggleSource.await(sourceId)
    }

    fun toggleSources(enable: Boolean) {
        successState?.extension?.sources
            ?.map { it.id }
            ?.let { toggleSource.await(it, enable) }
    }

    fun toggleIncognito(enable: Boolean) {
        successState?.extension?.pkgName?.let { packageName ->
            toggleIncognito.await(packageName, enable)
        }
    }

    sealed interface State {

        data object Loading : State

        data object Uninstalled : State

        @Immutable
        data class Success(
            val extension: AnimeExtension.Installed,
            val isIncognito: Boolean,
            val sources: ImmutableList<AnimeExtensionSourceItem>,
        ) : State
    }
}
