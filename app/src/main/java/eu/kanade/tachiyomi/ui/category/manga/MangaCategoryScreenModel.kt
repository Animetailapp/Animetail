package eu.kanade.tachiyomi.ui.category.manga

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
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
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class MangaCategoryScreenModel(
    private val getAllCategories: GetMangaCategories,
    private val getVisibleCategories: GetVisibleMangaCategories,
    private val createCategoryWithName: CreateMangaCategoryWithName,
    private val hideCategory: HideMangaCategory,
    private val deleteCategory: DeleteMangaCategory,
    private val reorderCategory: ReorderMangaCategory,
    private val renameCategory: RenameMangaCategory,
    private val libraryPreferences: LibraryPreferences,
) : ViewModel() {

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), MangaCategoryScreenState.Loading)

    fun createCategory(name: String) {
        viewModelScope.launch {
            when (createCategoryWithName.await(name)) {
                is CreateMangaCategoryWithName.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun hideCategory(category: Category) {
        viewModelScope.launch {
            when (hideCategory.await(category)) {
                is HideMangaCategory.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteMangaCategory.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        viewModelScope.launch {
            when (reorderCategory.await(category, newIndex)) {
                is ReorderMangaCategory.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        viewModelScope.launch {
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
