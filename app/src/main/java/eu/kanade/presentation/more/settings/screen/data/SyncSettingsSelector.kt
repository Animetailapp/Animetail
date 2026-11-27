package eu.kanade.presentation.more.settings.screen.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.sync.models.SyncSettings
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.util.system.toast
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
fun SyncSettingsSelector() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val model = metroViewModel<SyncSettingsSelectorModel>()
    val state by model.state.collectAsState()

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(TLMR.strings.pref_choose_what_to_sync),
                navigateUp = navigator::pop,
                scrollBehavior = it,
            )
        },
    ) { contentPadding ->
        LazyColumnWithAction(
            contentPadding = contentPadding,
            actionLabel = stringResource(TLMR.strings.label_sync),
            actionEnabled = state.options.canCreate(),
            onClickAction = {
                if (!SyncDataJob.isRunning(context)) {
                    model.syncNow(context)
                    navigator.pop()
                } else {
                    context.toast(TLMR.strings.sync_in_progress)
                }
            },
        ) {
            item {
                SectionCard(MR.strings.label_library) {
                    Options(BackupOptions.libraryOptions, state, model)
                }
            }

            item {
                SectionCard(MR.strings.label_settings) {
                    Options(BackupOptions.settingsOptions, state, model)
                }
            }
        }
    }
}

@Composable
private fun Options(
    options: List<BackupOptions.Entry>,
    state: SyncSettingsSelectorModel.State,
    model: SyncSettingsSelectorModel,
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
class SyncSettingsSelectorModel(
    val syncPreferences: SyncPreferences,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        State(syncOptionsToBackupOptions(syncPreferences.getSyncSettings())),
    )
    val state: StateFlow<State> = mutableState.asStateFlow()
    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        mutableState.update {
            val updatedOptions = setter(it.options, enabled)
            syncPreferences.setSyncSettings(backupOptionsToSyncOptions(updatedOptions))
            it.copy(options = updatedOptions)
        }
    }

    fun syncNow(context: Context) {
        SyncDataJob.startNow(context)
    }

    @Immutable
    data class State(
        val options: BackupOptions = BackupOptions(),
    ) companion object {
        private fun syncOptionsToBackupOptions(syncSettings: SyncSettings): BackupOptions {
            return BackupOptions(
                libraryEntries = syncSettings.libraryEntries,
                categories = syncSettings.categories,
                chapters = syncSettings.chapters,
                tracking = syncSettings.tracking,
                history = syncSettings.history,
                appSettings = syncSettings.appSettings,
                sourceSettings = syncSettings.sourceSettings,
                privateSettings = syncSettings.privateSettings,
            )
        }

        private fun backupOptionsToSyncOptions(backupOptions: BackupOptions): SyncSettings {
            return SyncSettings(
                libraryEntries = backupOptions.libraryEntries,
                categories = backupOptions.categories,
                chapters = backupOptions.chapters,
                tracking = backupOptions.tracking,
                history = backupOptions.history,
                appSettings = backupOptions.appSettings,
                sourceSettings = backupOptions.sourceSettings,
                privateSettings = backupOptions.privateSettings,
            )
        }
    }
}
