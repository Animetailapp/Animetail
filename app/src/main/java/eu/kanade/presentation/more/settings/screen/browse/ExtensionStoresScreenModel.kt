package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

class ExtensionStoresScreenModel(
    val isManga: Boolean,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<ExtensionStoreScreenState>(ExtensionStoreScreenState.Loading) {

    private val getMangaExtensionStores: GetMangaExtensionStores by lazy { Injekt.get() }
    private val getAnimeExtensionStores: GetAnimeExtensionStores by lazy { Injekt.get() }
    private val addMangaExtensionStore: AddMangaExtensionStore by lazy { Injekt.get() }
    private val addAnimeExtensionStore: AddAnimeExtensionStore by lazy { Injekt.get() }
    private val removeMangaExtensionStore: RemoveMangaExtensionStore by lazy { Injekt.get() }
    private val removeAnimeExtensionStore: RemoveAnimeExtensionStore by lazy { Injekt.get() }
    private val updateMangaExtensionStores: UpdateMangaExtensionStores by lazy { Injekt.get() }
    private val updateAnimeExtensionStores: UpdateAnimeExtensionStores by lazy { Injekt.get() }

    private inline fun updateSuccessState(
        func: (ExtensionStoreScreenState.Success) -> ExtensionStoreScreenState.Success,
    ) {
        mutableState.update {
            when (it) {
                ExtensionStoreScreenState.Loading -> it
                is ExtensionStoreScreenState.Success -> func(it)
            }
        }
    }

    init {
        val storesFlow = if (isManga) getMangaExtensionStores.subscribe() else getAnimeExtensionStores.subscribe()
        screenModelScope.launchIO {
            storesFlow.collectLatest { stores ->
                mutableState.update {
                    when (it) {
                        ExtensionStoreScreenState.Loading -> ExtensionStoreScreenState.Success(
                            stores = stores,
                            disabledRepos = sourcePreferences.disabledRepos.get(),
                        )

                        is ExtensionStoreScreenState.Success -> it.copy(stores = stores)
                    }
                }
            }
        }

        sourcePreferences.disabledRepos.changes()
            .onEach { disabledRepos ->
                mutableState.update {
                    when (it) {
                        is ExtensionStoreScreenState.Success -> it.copy(disabledRepos = disabledRepos)
                        else -> it
                    }
                }
            }
            .launchIn(screenModelScope)
    }

    /**
     * Creates and adds a new repo to the database.
     *
     * @param baseUrl The baseUrl of the repo to create.
     */
    fun createRepo(baseUrl: String) {
        screenModelScope.launch {
            updateSuccessState {
                it.copy(
                    dialog = when (it.dialog) {
                        is ExtensionStoreDialog.Create -> it.dialog.copy(processing = true)
                        is ExtensionStoreDialog.Confirm -> it.dialog.copy(processing = true)
                        else -> it.dialog
                    },
                )
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
                    updateSuccessState {
                        it.copy(
                            dialog = when (it.dialog) {
                                is ExtensionStoreDialog.Create -> it.dialog.copy(
                                    processing = false,
                                    errorMessage = throwable.message ?: "unknown error",
                                )

                                is ExtensionStoreDialog.Confirm -> it.dialog.copy(
                                    processing = false,
                                    errorMessage = throwable.message ?: "unknown error",
                                )

                                else -> it.dialog
                            },
                        )
                    }
                }
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        val status = state.value

        if (status is ExtensionStoreScreenState.Success) {
            screenModelScope.launchIO {
                if (isManga) {
                    updateMangaExtensionStores()
                } else {
                    updateAnimeExtensionStores()
                }
            }
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(baseUrl: String) {
        enableRepo(baseUrl)
        screenModelScope.launchIO {
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
        updateSuccessState { state ->
            state.copy(
                dialog = ExtensionStoreDialog.Confirm(
                    url = storeIndexUrl,
                    alreadyExists = state.stores.any { it.indexUrl == storeIndexUrl },
                ),
            )
        }
    }

    fun showDialog(dialog: ExtensionStoreDialog) {
        updateSuccessState { state ->
            state.copy(dialog = dialog)
        }
    }

    fun dismissDialog() {
        updateSuccessState {
            it.copy(dialog = null)
        }
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
