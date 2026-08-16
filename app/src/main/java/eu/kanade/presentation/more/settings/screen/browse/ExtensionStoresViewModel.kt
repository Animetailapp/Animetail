package eu.kanade.presentation.more.settings.screen.browse

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
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.extension.anime.interactor.AddAnimeExtensionStore
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStores
import mihon.domain.extension.anime.interactor.RemoveAnimeExtensionStore
import mihon.domain.extension.anime.interactor.UpdateAnimeExtensionStores
import mihon.domain.extension.manga.interactor.AddMangaExtensionStore
import mihon.domain.extension.manga.interactor.GetMangaExtensionStores
import mihon.domain.extension.manga.interactor.RemoveMangaExtensionStore
import mihon.domain.extension.manga.interactor.UpdateMangaExtensionStores
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.util.lang.launchIO
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class ExtensionStoresViewModel(
    @Assisted val isManga: Boolean,
    private val sourcePreferences: SourcePreferences,
    private val getMangaExtensionStores: GetMangaExtensionStores,
    private val getAnimeExtensionStores: GetAnimeExtensionStores,
    private val addMangaExtensionStore: AddMangaExtensionStore,
    private val addAnimeExtensionStore: AddAnimeExtensionStore,
    private val removeMangaExtensionStore: RemoveMangaExtensionStore,
    private val removeAnimeExtensionStore: RemoveAnimeExtensionStore,
    private val updateMangaExtensionStores: UpdateMangaExtensionStores,
    private val updateAnimeExtensionStores: UpdateAnimeExtensionStores,
    private val mangaExtensionManager: MangaExtensionManager,
    private val animeExtensionManager: AnimeExtensionManager,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(isManga: Boolean): ExtensionStoresViewModel
    }

    private val dialog = MutableStateFlow<ExtensionStoreDialog?>(null)

    val state: StateFlow<ExtensionStoreScreenState> = combine(
        if (isManga) getMangaExtensionStores.subscribe() else getAnimeExtensionStores.subscribe(),
        sourcePreferences.disabledRepos.changes(),
        dialog,
    ) { stores, disabledRepos, dialog ->
        ExtensionStoreScreenState.Success(
            stores = stores,
            disabledRepos = disabledRepos,
            dialog = dialog,
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), ExtensionStoreScreenState.Loading)

    /**
     * Creates and adds a new repo to the database.
     *
     * @param baseUrl The baseUrl of the repo to create.
     */
    fun createRepo(baseUrl: String) {
        viewModelScope.launch {
            dialog.update {
                when (it) {
                    is ExtensionStoreDialog.Create -> it.copy(processing = true)
                    is ExtensionStoreDialog.Confirm -> it.copy(processing = true)
                    else -> it
                }
            }
            val result = if (isManga) addMangaExtensionStore(baseUrl) else addAnimeExtensionStore(baseUrl)
            result.onSuccess {
                if (isManga) {
                    mangaExtensionManager.findAvailableExtensions()
                } else {
                    animeExtensionManager.findAvailableExtensions()
                }
                dismissDialog()
            }
                .onFailure { throwable ->
                    dialog.update {
                        when (it) {
                            is ExtensionStoreDialog.Create -> it.copy(
                                processing = false,
                                errorMessage = throwable.message ?: "unknown error",
                            )

                            is ExtensionStoreDialog.Confirm -> it.copy(
                                processing = false,
                                errorMessage = throwable.message ?: "unknown error",
                            )

                            else -> it
                        }
                    }
                }
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        viewModelScope.launchIO {
            if (isManga) {
                updateMangaExtensionStores()
            } else {
                updateAnimeExtensionStores()
            }
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(baseUrl: String) {
        enableRepo(baseUrl)
        viewModelScope.launchIO {
            if (isManga) {
                removeMangaExtensionStore(baseUrl)
                mangaExtensionManager.findAvailableExtensions()
            } else {
                removeAnimeExtensionStore(baseUrl)
                animeExtensionManager.findAvailableExtensions()
            }
        }
    }

    fun enableRepo(baseUrl: String) {
        val disabledRepos = sourcePreferences.disabledRepos.get()
        if (baseUrl in disabledRepos) {
            sourcePreferences.disabledRepos.set(
                disabledRepos.filterNot { it == baseUrl }.toSet(),
            )
        }
    }

    fun disableRepo(baseUrl: String) {
        val disabledRepos = sourcePreferences.disabledRepos.get()
        if (baseUrl !in disabledRepos) {
            sourcePreferences.disabledRepos.set(
                disabledRepos + baseUrl,
            )
        }
    }

    fun addFromDeeplink(storeIndexUrl: String) {
        viewModelScope.launchIO {
            val stores = if (isManga) getMangaExtensionStores.await() else getAnimeExtensionStores.await()
            val alreadyExists = stores.any {
                it.indexUrl ==
                    storeIndexUrl
            }
            dialog.update { ExtensionStoreDialog.Confirm(url = storeIndexUrl, alreadyExists = alreadyExists) }
        }
    }

    fun showDialog(dialog: ExtensionStoreDialog) {
        this.dialog.update { dialog }
    }

    fun dismissDialog() {
        dialog.update { null }
    }
}

sealed class ExtensionStoreDialog {
    data class Create(val processing: Boolean = false, val errorMessage: String? = null) : ExtensionStoreDialog()
    data class Delete(val store: ExtensionStore) : ExtensionStoreDialog()
    data class Confirm(
        val url: String,
        val alreadyExists: Boolean = false,
        val processing: Boolean = false,
        val errorMessage: String? = null,
    ) : ExtensionStoreDialog()
}

sealed class ExtensionStoreScreenState {

    @Immutable
    data object Loading : ExtensionStoreScreenState()

    @Immutable
    data class Success(
        val stores: List<ExtensionStore>,
        val dialog: ExtensionStoreDialog? = null,
        val disabledRepos: Set<String> = emptySet(),
    ) : ExtensionStoreScreenState() {

        val isEmpty: Boolean
            get() = stores.isEmpty()
    }
}
