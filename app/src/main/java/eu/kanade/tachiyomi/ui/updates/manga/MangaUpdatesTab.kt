package eu.kanade.tachiyomi.ui.updates.manga

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.updates.UpdatesDeleteConfirmationDialog
import eu.kanade.presentation.updates.UpdatesFilterDialog
import eu.kanade.presentation.updates.manga.MangaUpdateScreen
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesSettingsViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.feature.upcoming.manga.UpcomingMangaScreen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

@Composable
fun Screen.mangaUpdatesTab(
    context: Context,
    fromMore: Boolean,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = viewModel<MangaUpdatesViewModel>()
    val settingsViewModel = viewModel<UpdatesSettingsViewModel>()
    val state by viewModel.state.collectAsState()

    val scope = rememberCoroutineScope()
    val navigateUp: (() -> Unit)? = if (fromMore) {
        {
            if (navigator.lastItem == HomeScreen) {
                scope.launch { HomeScreen.openTab(HomeScreen.Tab.AnimeLib()) }
            } else {
                navigator.pop()
            }
        }
    } else {
        null
    }

    return TabContent(
        titleRes = AYMR.strings.label_updates,
        searchEnabled = false,
        content = { contentPadding, _ ->
            MangaUpdateScreen(
                state = state,
                snackbarHostState = viewModel.snackbarHostState,
                lastUpdated = viewModel.lastUpdated,
                onClickCover = { item -> navigator.push(MangaScreen(item.update.mangaId)) },
                onSelectAll = viewModel::toggleAllSelection,
                onInvertSelection = viewModel::invertSelection,
                onUpdateLibrary = viewModel::updateLibrary,
                onDownloadChapter = viewModel::downloadChapters,
                onMultiBookmarkClicked = viewModel::bookmarkUpdates,
                onMultiMarkAsReadClicked = viewModel::markUpdatesRead,
                onMultiDeleteClicked = viewModel::showConfirmDeleteChapters,
                onUpdateSelected = viewModel::toggleSelection,
                onOpenChapter = {
                    val intent =
                        ReaderActivity.newIntent(context, it.update.mangaId, it.update.chapterId)
                    context.startActivity(intent)
                },
            )

            val onDismissDialog = { viewModel.setDialog(null) }
            when (val dialog = state.dialog) {
                is MangaUpdatesViewModel.Dialog.DeleteConfirmation -> {
                    UpdatesDeleteConfirmationDialog(
                        onDismissRequest = onDismissDialog,
                        onConfirm = { viewModel.deleteChapters(dialog.toDelete) },
                        isManga = true,
                    )
                }

                is MangaUpdatesViewModel.Dialog.FilterSheet -> {
                    UpdatesFilterDialog(
                        onDismissRequest = onDismissDialog,
                        viewModel = settingsViewModel,
                    )
                }

                null -> {}
            }

            LaunchedEffect(Unit) {
                // AM (DISCORD) -->
                DiscordRPCService.setMangaScreen(context, DiscordScreen.UPDATES)
                // <-- AM (DISCORD)
                viewModel.events.collectLatest { event ->
                    when (event) {
                        MangaUpdatesViewModel.Event.InternalError -> viewModel.snackbarHostState.showSnackbar(
                            context.stringResource(
                                MR.strings.internal_error,
                            ),
                        )

                        is MangaUpdatesViewModel.Event.LibraryUpdateTriggered -> {
                            val msg = if (event.started) {
                                MR.strings.updating_library
                            } else {
                                MR.strings.update_already_running
                            }
                            viewModel.snackbarHostState.showSnackbar(context.stringResource(msg))
                        }
                    }
                }
            }

            LaunchedEffect(state.selectionMode) {
                HomeScreen.showBottomNav(!state.selectionMode)
            }

            LaunchedEffect(state.isLoading) {
                if (!state.isLoading) {
                    (context as? MainActivity)?.ready = true
                }
            }
            DisposableEffect(Unit) {
                viewModel.resetNewUpdatesCount()

                onDispose {
                    viewModel.resetNewUpdatesCount()
                }
            }
        },
        actions =
        if (state.selected.isNotEmpty()) {
            persistentListOf(
                AppBar.Action(
                    title = stringResource(MR.strings.action_select_all),
                    icon = Icons.Outlined.SelectAll,
                    onClick = { viewModel.toggleAllSelection(true) },
                ),
                AppBar.Action(
                    title = stringResource(MR.strings.action_select_inverse),
                    icon = Icons.Outlined.FlipToBack,
                    onClick = { viewModel.invertSelection() },
                ),
            )
        } else {
            persistentListOf(
                AppBar.Action(
                    title = stringResource(MR.strings.action_filter),
                    icon = Icons.Outlined.FilterList,
                    iconTint = if (state.hasActiveFilters) {
                        MaterialTheme.colorScheme.active
                    } else {
                        LocalContentColor.current
                    },
                    onClick = { viewModel.showFilterDialog() },
                ),
                AppBar.Action(
                    title = stringResource(MR.strings.action_view_upcoming),
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = { navigator.push(UpcomingMangaScreen()) },
                ),
                AppBar.Action(
                    title = stringResource(MR.strings.action_update_library),
                    icon = Icons.Outlined.Refresh,
                    onClick = { viewModel.updateLibrary() },
                ),
            )
        },
        navigateUp = navigateUp,
    )
}
