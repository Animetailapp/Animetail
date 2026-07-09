package eu.kanade.tachiyomi.ui.browse.anime.migration.anime

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.MigrateAnimeScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeSearchScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

data class MigrateAnimeScreen(
    private val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateAnimeScreenModel(sourceId) }

        val state by screenModel.state.collectAsState()

        val isSelectionMode = state.selectedAnimeIds.isNotEmpty()
        BackHandler(enabled = isSelectionMode) {
            screenModel.clearSelection()
        }

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        MigrateAnimeScreen(
            navigateUp = {
                if (isSelectionMode) {
                    screenModel.clearSelection()
                } else {
                    navigator.pop()
                }
            },
            title = state.source!!.name,
            state = state,
            onClickItem = { anime ->
                if (isSelectionMode) {
                    screenModel.toggleSelection(anime)
                } else {
                    navigator.push(MigrateAnimeSearchScreen(anime.id))
                }
            },
            onClickCover = { anime ->
                if (isSelectionMode) {
                    screenModel.toggleSelection(anime)
                } else {
                    navigator.push(AnimeScreen(anime.id))
                }
            },
            onLongClickItem = screenModel::toggleSelection,
            onSelectAll = screenModel::selectAll,
            onClearSelection = screenModel::clearSelection,
            onClickMigrate = {
                navigator.push(mihon.feature.migration.config.AnimeMigrationConfigScreen(state.selectedAnimeIds))
            },
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    MigrationAnimeEvent.FailedFetchingFavorites -> {
                        context.toast(MR.strings.internal_error)
                    }
                }
            }
        }
    }
}
