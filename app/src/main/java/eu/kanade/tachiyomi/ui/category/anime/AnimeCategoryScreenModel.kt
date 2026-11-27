package eu.kanade.tachiyomi.ui.category.anime

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.app.di.AppGraph
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.HideAnimeCategory
import tachiyomi.domain.category.anime.interactor.RenameAnimeCategory
import tachiyomi.domain.category.anime.interactor.ReorderAnimeCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.seconds

class AnimeCategoryScreenModel(
    private val getAllCategories: GetAnimeCategories,
    private val getVisibleCategories: GetVisibleAnimeCategories,
    private val createCategoryWithName: CreateAnimeCategoryWithName,
    private val hideCategory: HideAnimeCategory,
    private val deleteCategory: DeleteAnimeCategory,
    private val reorderCategory: ReorderAnimeCategory,
    private val renameCategory: RenameAnimeCategory,
    private val libraryPreferences: LibraryPreferences,
) : ScreenModel {
    constructor(appGraph: AppGraph) : this(
        getAllCategories = appGraph.getAnimeCategories,
        getVisibleCategories = appGraph.getVisibleAnimeCategories,
        createCategoryWithName = appGraph.createAnimeCategoryWithName,
        hideCategory = appGraph.hideAnimeCategory,
        deleteCategory = appGraph.deleteAnimeCategory,
        reorderCategory = appGraph.reorderAnimeCategory,
        renameCategory = appGraph.renameAnimeCategory,
        libraryPreferences = appGraph.libraryPreferences,
    )

    private val _events: Channel<AnimeCategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    private val dialog = MutableStateFlow<AnimeCategoryDialog?>(null)

    val state: StateFlow<AnimeCategoryScreenState> = combine(
        if (libraryPreferences.hideHiddenCategoriesSettings.get()) {
            getVisibleCategories.subscribe()
        } else {
            getAllCategories.subscribe()
        },
        dialog,
    ) { categories, dialog ->
        AnimeCategoryScreenState.Success(
            categories = categories
                .filterNot(Category::isSystemCategory)
                .toImmutableList(),
            dialog = dialog,
        )
    }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5.seconds), AnimeCategoryScreenState.Loading)

    fun createCategory(name: String) {
        screenModelScope.launch {
            when (createCategoryWithName.await(name)) {
                is CreateAnimeCategoryWithName.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun hideCategory(category: Category) {
        screenModelScope.launch {
            when (hideCategory.await(category)) {
                is HideAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        screenModelScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        screenModelScope.launch {
            when (reorderCategory.await(category, newIndex)) {
                is ReorderAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        screenModelScope.launch {
            when (renameCategory.await(category, name)) {
                is RenameAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun showDialog(dialog: AnimeCategoryDialog) {
        this.dialog.update { dialog }
    }

    fun dismissDialog() {
        dialog.update { null }
    }
}

sealed interface AnimeCategoryDialog {
    data object Create : AnimeCategoryDialog
    data class Rename(val category: Category) : AnimeCategoryDialog
    data class Delete(val category: Category) : AnimeCategoryDialog
}

sealed interface AnimeCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : AnimeCategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface AnimeCategoryScreenState {

    @Immutable
    data object Loading : AnimeCategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<Category>,
        val dialog: AnimeCategoryDialog? = null,
    ) : AnimeCategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
