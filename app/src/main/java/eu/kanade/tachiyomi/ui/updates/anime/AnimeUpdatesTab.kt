package eu.kanade.tachiyomi.ui.updates.anime

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
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
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.entries.anime.EpisodeOptionsDialogScreen
import eu.kanade.presentation.updates.UpdatesDeleteConfirmationDialog
import eu.kanade.presentation.updates.anime.AnimeUpdateScreen
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.feature.upcoming.anime.UpcomingAnimeScreen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.injectLazy

@Composable
fun Screen.animeUpdatesTab(
    context: Context,
    fromMore: Boolean,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = viewModel<AnimeUpdatesViewModel>()
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

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

    suspend fun openEpisode(updateItem: AnimeUpdatesItem, altPlayer: Boolean = false) {
        val playerPreferences: PlayerPreferences by injectLazy()
        val update = updateItem.update
        val extPlayer = playerPreferences.alwaysUseExternalPlayer().get() != altPlayer
        MainActivity.startPlayerActivity(
            context,
            update.animeId,
            update.episodeId,
            extPlayer,
        )
    }

    return TabContent(
        titleRes = AYMR.strings.label_anime_updates,
        searchEnabled = false,
        content = { contentPadding, _ ->
            AnimeUpdateScreen(
                state = state,
                snackbarHostState = viewModel.snackbarHostState,
                lastUpdated = viewModel.lastUpdated,
                onClickCover = { item -> navigator.push(AnimeScreen(item.update.animeId)) },
                onSelectAll = viewModel::toggleAllSelection,
                onInvertSelection = viewModel::invertSelection,
                onUpdateLibrary = viewModel::updateLibrary,
                onDownloadEpisode = viewModel::downloadEpisodes,
                onMultiBookmarkClicked = viewModel::bookmarkUpdates,
                onMultiFillermarkClicked = viewModel::fillermarkUpdates,
                onMultiMarkAsSeenClicked = viewModel::markUpdatesSeen,
                onMultiDeleteClicked = viewModel::showConfirmDeleteEpisodes,
                onUpdateSelected = viewModel::toggleSelection,
                onOpenEpisode = { updateItem: AnimeUpdatesItem, altPlayer: Boolean ->
                    scope.launchIO {
                        openEpisode(updateItem, altPlayer)
                    }
                },
            )

            val onDismissDialog = { viewModel.setDialog(null) }
            when (val dialog = state.dialog) {
                is AnimeUpdatesViewModel.Dialog.DeleteConfirmation -> {
                    UpdatesDeleteConfirmationDialog(
                        onDismissRequest = onDismissDialog,
                        onConfirm = { viewModel.deleteEpisodes(dialog.toDelete) },
                        isManga = false,
                    )
                }

                is AnimeUpdatesViewModel.Dialog.ShowQualities -> {
                    EpisodeOptionsDialogScreen.onDismissDialog = onDismissDialog
                    NavigatorAdaptiveSheet(
                        screen = EpisodeOptionsDialogScreen(
                            useExternalDownloader = viewModel.useExternalDownloader,
                            episodeTitle = dialog.episodeTitle,
                            episodeId = dialog.episodeId,
                            animeId = dialog.animeId,
                            sourceId = dialog.sourceId,
                        ),
                        onDismissRequest = onDismissDialog,
                    )
                }

                null -> {}
            }

            LaunchedEffect(Unit) {
                // AM (DISCORD) -->
                DiscordRPCService.setScreen(context, DiscordScreen.UPDATES)
                // <-- AM (DISCORD)
                viewModel.events.collectLatest { event ->
                    when (event) {
                        AnimeUpdatesViewModel.Event.InternalError -> viewModel.snackbarHostState.showSnackbar(
                            context.stringResource(
                                MR.strings.internal_error,
                            ),
                        )

                        is AnimeUpdatesViewModel.Event.LibraryUpdateTriggered -> {
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
        if (viewModel.state.collectAsState().value.selected.isNotEmpty()) {
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
                    title = stringResource(MR.strings.action_view_upcoming),
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = { navigator.push(UpcomingAnimeScreen()) },
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
