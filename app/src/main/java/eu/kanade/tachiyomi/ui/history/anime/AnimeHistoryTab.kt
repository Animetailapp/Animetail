package eu.kanade.tachiyomi.ui.history.anime

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.entries.anime.DuplicateAnimeDialog
import eu.kanade.presentation.history.HistoryDeleteAllDialog
import eu.kanade.presentation.history.HistoryDeleteDialog
import eu.kanade.presentation.history.anime.AnimeHistoryScreen
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialog
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialogScreenModel
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.app.di.appGraph
import mihon.core.navigation.AnimeRoute
import mihon.core.navigation.CategoryRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MigrateSeasonSelectRoute
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

val resumeLastEpisodeSeenEvent = Channel<Unit>()

@Composable
fun animeHistoryTab(
    context: Context,
    fromMore: Boolean,
): TabContent {
    val snackbarHostState = SnackbarHostState()

    val navigator = LocalNavigator.current
    val viewModel = metroViewModel<AnimeHistoryViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchQuery = state.searchQuery ?: ""

    suspend fun openEpisode(context: Context, episode: Episode?) {
        val playerPreferences = context.appGraph.playerPreferences
        val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
        if (episode != null) {
            MainActivity.startPlayerActivity(
                context,
                episode.animeId,
                episode.id,
                extPlayer,
            )
        } else {
            snackbarHostState.showSnackbar(context.stringResource(AYMR.strings.no_next_episode))
        }
    }

    val navigateUp: (() -> Unit)? = if (fromMore) {
        { navigator.pop() }
    } else {
        null
    }

    return TabContent(
        titleRes = AYMR.strings.label_anime_history,
        searchEnabled = true,
        content = { contentPadding, _ ->
            AnimeHistoryScreen(
                state = state,
                searchQuery = searchQuery,
                snackbarHostState = snackbarHostState,
                onClickCover = { navigator.push(AnimeRoute(it)) },
                onClickResume = viewModel::getNextEpisodeForAnime,
                onDialogChange = viewModel::setDialog,
                onClickFavorite = viewModel::addFavorite,
            )

            val onDismissRequest = { viewModel.setDialog(null) }
            when (val dialog = state.dialog) {
                is AnimeHistoryViewModel.Dialog.Delete -> {
                    HistoryDeleteDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = { all ->
                            if (all) {
                                viewModel.removeAllFromHistory(dialog.history.animeId)
                            } else {
                                viewModel.removeFromHistory(dialog.history)
                            }
                        },
                        isManga = false,
                    )
                }

                is AnimeHistoryViewModel.Dialog.DeleteAll -> {
                    HistoryDeleteAllDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = viewModel::removeAllHistory,
                    )
                }

                is AnimeHistoryViewModel.Dialog.DuplicateAnime -> {
                    DuplicateAnimeDialog(
                        onDismissRequest = onDismissRequest,
                        onConfirm = {
                            viewModel.addFavorite(dialog.anime)
                        },
                        onOpenAnime = { navigator.push(AnimeRoute(dialog.duplicate.id)) },
                        onMigrate = {
                            viewModel.showMigrateDialog(dialog.anime, dialog.duplicate)
                        },
                    )
                }

                is AnimeHistoryViewModel.Dialog.ChangeCategory -> {
                    ChangeCategoryDialog(
                        initialSelection = dialog.initialSelection,
                        onDismissRequest = onDismissRequest,
                        onEditCategories = { navigator.push(CategoryRoute) },
                        onConfirm = { include, _ ->
                            viewModel.moveAnimeToCategoriesAndAddToLibrary(dialog.anime, include)
                        },
                    )
                }

                is AnimeHistoryViewModel.Dialog.Migrate -> {
                    MigrateAnimeDialog(
                        oldAnime = dialog.oldAnime,
                        newAnime = dialog.newAnime,
                        screenModel = metroViewModel<MigrateAnimeDialogScreenModel>(),
                        onDismissRequest = onDismissRequest,
                        onClickTitle = { navigator.push(AnimeRoute(dialog.oldAnime.id)) },
                        onClickSeasons = {
                            navigator.push(MigrateSeasonSelectRoute(dialog.oldAnime.id, dialog.newAnime.id))
                        },
                        onPopScreen = { navigator.replace(AnimeRoute(dialog.oldAnime.id)) },
                    )
                }

                null -> {}
            }

            LaunchedEffect(state.list) {
                if (state.list != null) {
                    (context as? MainActivity)?.ready = true
                }
            }

            LaunchedEffect(Unit) {
                // AM (DISCORD) -->
                DiscordRPCService.setScreen(context, DiscordScreen.HISTORY)
                // <-- AM (DISCORD)
                viewModel.events.collectLatest { e ->
                    when (e) {
                        AnimeHistoryViewModel.Event.InternalError ->
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))

                        AnimeHistoryViewModel.Event.HistoryCleared ->
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))

                        is AnimeHistoryViewModel.Event.OpenEpisode -> openEpisode(context, e.episode)
                    }
                }
            }

            LaunchedEffect(Unit) {
                resumeLastEpisodeSeenEvent.receiveAsFlow().collectLatest {
                    openEpisode(context, viewModel.getNextEpisode())
                }
            }
        },
        actions =
        persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.pref_clear_history),
                icon = Icons.Outlined.DeleteSweep,
                onClick = { viewModel.setDialog(AnimeHistoryViewModel.Dialog.DeleteAll) },
            ),
        ),
        navigateUp = navigateUp,
    )
}
