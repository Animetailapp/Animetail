package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class ExtensionStoresViewModel(
    val isManga: Boolean,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : ViewModel() {

    companion object {
        val IS_MANGA_KEY = CreationExtras.Key<Boolean>()

        val Factory = viewModelFactory {
            initializer {
                ExtensionStoresViewModel(
                    isManga = this[IS_MANGA_KEY]!!,
                )
            }
        }
    }

    private val getMangaExtensionStores: GetMangaExtensionStores by lazy { Injekt.get() }
    private val getAnimeExtensionStores: GetAnimeExtensionStores by lazy { Injekt.get() }
    private val addMangaExtensionStore: AddMangaExtensionStore by lazy { Injekt.get() }
    private val addAnimeExtensionStore: AddAnimeExtensionStore by lazy { Injekt.get() }
    private val removeMangaExtensionStore: RemoveMangaExtensionStore by lazy { Injekt.get() }
    private val removeAnimeExtensionStore: RemoveAnimeExtensionStore by lazy { Injekt.get() }
    private val updateMangaExtensionStores: UpdateMangaExtensionStores by lazy { Injekt.get() }
    private val updateAnimeExtensionStores: UpdateAnimeExtensionStores by lazy { Injekt.get() }

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
                    Injekt.get<MangaExtensionManager>().findAvailableExtensions()
                } else {
                    Injekt.get<AnimeExtensionManager>().findAvailableExtensions()
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
                Injekt.get<MangaExtensionManager>().findAvailableExtensions()
            } else {
                removeAnimeExtensionStore(baseUrl)
                Injekt.get<AnimeExtensionManager>().findAvailableExtensions()
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
