package eu.kanade.presentation.more.settings.screen.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.data.sync.models.SyncTriggerOptions
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SyncTriggerOptionsScreen() {
    val navigator = LocalNavigator.current
    val model = metroViewModel<SyncOptionsScreenModel>()
    val state by model.state.collectAsState()

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(TLMR.strings.pref_sync_options),
                navigateUp = navigator::pop,
                scrollBehavior = it,
            )
        },
    ) { contentPadding ->
        LazyColumnWithAction(
            contentPadding = contentPadding,
            actionLabel = stringResource(MR.strings.action_save),
            actionEnabled = true,
            onClickAction = {
                navigator.pop()
            },
        ) {
            item {
                SectionCard(TLMR.strings.label_triggers) {
                    Options(SyncTriggerOptions.mainOptions, state, model)
                }
            }
        }
    }
}

@Composable
private fun Options(
    options: ImmutableList<SyncTriggerOptions.Entry>,
    state: SyncOptionsScreenModel.State,
    model: SyncOptionsScreenModel,
) {
    options.forEach { option ->
        LabeledCheckbox(
            label = stringResource(option.label),
            checked = option.getter(state.options),
            onCheckedChange = {
                model.toggle(option.setter, it)
            },
            enabled = option.enabled(state.options),
        )
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SyncOptionsScreenModel(
    val syncPreferences: SyncPreferences,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        State(
            syncPreferences.getSyncTriggerOptions(),
        ),
    )
    val state: StateFlow<State> = mutableState.asStateFlow()

    fun toggle(setter: (SyncTriggerOptions, Boolean) -> SyncTriggerOptions, enabled: Boolean) {
        mutableState.update {
            val updatedTriggerOptions = setter(it.options, enabled)
            syncPreferences.setSyncTriggerOptions(updatedTriggerOptions)
            it.copy(
                options = updatedTriggerOptions,
            )
        }
    }

    @Immutable
    data class State(
        val options: SyncTriggerOptions = SyncTriggerOptions(),
    )
}
