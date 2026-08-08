package eu.kanade.tachiyomi.ui.category.manga

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
import tachiyomi.domain.category.manga.interactor.CreateMangaCategoryWithName
import tachiyomi.domain.category.manga.interactor.DeleteMangaCategory
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.manga.interactor.HideMangaCategory
import tachiyomi.domain.category.manga.interactor.RenameMangaCategory
import tachiyomi.domain.category.manga.interactor.ReorderMangaCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class MangaCategoryScreenModel(
    private val getAllCategories: GetMangaCategories = Injekt.get(),
    private val getVisibleCategories: GetVisibleMangaCategories = Injekt.get(),
    private val createCategoryWithName: CreateMangaCategoryWithName = Injekt.get(),
    private val hideCategory: HideMangaCategory = Injekt.get(),
    private val deleteCategory: DeleteMangaCategory = Injekt.get(),
    private val reorderCategory: ReorderMangaCategory = Injekt.get(),
    private val renameCategory: RenameMangaCategory = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : ScreenModel {

    private val _events: Channel<MangaCategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    private val dialog = MutableStateFlow<MangaCategoryDialog?>(null)

    val state: StateFlow<MangaCategoryScreenState> = combine(
        if (libraryPreferences.hideHiddenCategoriesSettings.get()) {
            getVisibleCategories.subscribe()
        } else {
            getAllCategories.subscribe()
        },
        dialog,
    ) { categories, dialog ->
        MangaCategoryScreenState.Success(
            categories = categories
                .filterNot(Category::isSystemCategory)
                .toImmutableList(),
            dialog = dialog,
        )
    }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5.seconds), MangaCategoryScreenState.Loading)

    fun createCategory(name: String) {
        screenModelScope.launch {
            when (createCategoryWithName.await(name)) {
                is CreateMangaCategoryWithName.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun hideCategory(category: Category) {
        screenModelScope.launch {
            when (hideCategory.await(category)) {
                is HideMangaCategory.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        screenModelScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteMangaCategory.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        screenModelScope.launch {
            when (reorderCategory.await(category, newIndex)) {
                is ReorderMangaCategory.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        screenModelScope.launch {
            when (renameCategory.await(category, name)) {
                is RenameMangaCategory.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun showDialog(dialog: MangaCategoryDialog) {
        this.dialog.update { dialog }
    }

    fun dismissDialog() {
        dialog.update { null }
    }
}

sealed interface MangaCategoryDialog {
    data object Create : MangaCategoryDialog
    data class Rename(val category: Category) : MangaCategoryDialog
    data class Delete(val category: Category) : MangaCategoryDialog
}

sealed interface MangaCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : MangaCategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface MangaCategoryScreenState {

    @Immutable
    data object Loading : MangaCategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<Category>,
        val dialog: MangaCategoryDialog? = null,
    ) : MangaCategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
