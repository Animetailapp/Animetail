package eu.kanade.presentation.more.settings.screen.player.custombutton

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animetail.feature.mpvfiles.MpvConfig
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.custombuttons.interactor.CreateCustomButton
import tachiyomi.domain.custombuttons.interactor.DeleteCustomButton
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.interactor.ReorderCustomButton
import tachiyomi.domain.custombuttons.interactor.ToggleFavoriteCustomButton
import tachiyomi.domain.custombuttons.interactor.UpdateCustomButton
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.custombuttons.model.CustomButtonUpdate
import tachiyomi.i18n.MR

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class PlayerSettingsCustomButtonScreenModel(
    private val getCustomButtons: GetCustomButtons,
    private val createCustomButton: CreateCustomButton,
    private val deleteCustomButton: DeleteCustomButton,
    private val updateCustomButton: UpdateCustomButton,
    private val reorderCustomButton: ReorderCustomButton,
    private val toggleFavoriteCustomButton: ToggleFavoriteCustomButton,
    // AM -->
    private val mpvConfig: MpvConfig,
    // <-- AM
) : ViewModel() {

    private val mutableState = MutableStateFlow<CustomButtonScreenState>(CustomButtonScreenState.Loading)
    val state: StateFlow<CustomButtonScreenState> = mutableState.asStateFlow()

    private val _events: Channel<CustomButtonEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            getCustomButtons.subscribeAll()
                .collectLatest { customButtons ->
                    mutableState.update {
                        CustomButtonScreenState.Success(
                            customButtons = customButtons.toImmutableList(),
                        )
                    }
                }
        }

        // AM -->
        viewModelScope.launchIO {
            getCustomButtons.subscribeAll()
                .distinctUntilChanged()
                .collectLatest { customButtons ->
                    mpvConfig.setupCustomButtons(customButtons)
                }
        }
        // <-- AM
    }

    fun createCustomButton(name: String, content: String, longPressContent: String, onStartup: String) {
        viewModelScope.launch {
            when (createCustomButton.await(name, content, longPressContent, onStartup)) {
                is CreateCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun togglePrimaryButton(customButton: CustomButton) {
        viewModelScope.launch {
            when (toggleFavoriteCustomButton.await(customButton)) {
                is ToggleFavoriteCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun editCustomButton(customButtonUpdate: CustomButtonUpdate) {
        viewModelScope.launch {
            when (updateCustomButton.await(customButtonUpdate)) {
                is UpdateCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun deleteCustomButton(customButton: CustomButton) {
        viewModelScope.launch {
            when (deleteCustomButton.await(customButton.id)) {
                is DeleteCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun changeOrder(customButton: CustomButton, newIndex: Int) {
        viewModelScope.launch {
            when (reorderCustomButton.changeOrder(customButton, newIndex)) {
                is ReorderCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun showDialog(dialog: CustomButtonDialog) {
        mutableState.update {
            when (it) {
                CustomButtonScreenState.Loading -> it
                is CustomButtonScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                CustomButtonScreenState.Loading -> it
                is CustomButtonScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface CustomButtonDialog {
    data object Create : CustomButtonDialog
    data class Edit(val customButton: CustomButton) : CustomButtonDialog
    data class Delete(val customButton: CustomButton) : CustomButtonDialog
}

sealed interface CustomButtonEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : CustomButtonEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface CustomButtonScreenState {
    @Immutable
    data object Loading : CustomButtonScreenState

    @Immutable
    data class Success(
        val customButtons: ImmutableList<CustomButton>,
        val dialog: CustomButtonDialog? = null,
    ) : CustomButtonScreenState {
        val isEmpty: Boolean
            get() = customButtons.isEmpty()
    }
}

sealed interface CustomButtonFetchState {
    @Immutable
    data object Loading : CustomButtonFetchState

    @Immutable
    data class Success(val customButtons: ImmutableList<CustomButton>) : CustomButtonFetchState

    @Immutable
    data class Error(val errorMessage: String) : CustomButtonFetchState
}

fun CustomButtonFetchState.getButtons(): ImmutableList<CustomButton> {
    return when (this) {
        is CustomButtonFetchState.Success -> this.customButtons
        else -> emptyList<CustomButton>().toImmutableList()
    }
}
