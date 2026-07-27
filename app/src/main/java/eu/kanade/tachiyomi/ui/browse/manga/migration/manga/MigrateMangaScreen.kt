package eu.kanade.tachiyomi.ui.browse.manga.migration.manga

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.manga.MigrateMangaScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

data class MigrateMangaScreen(
    private val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateMangaScreenModel(sourceId) }

        val state by screenModel.state.collectAsState()

        val isSelectionMode = state.selectedMangaIds.isNotEmpty()
        BackHandler(enabled = isSelectionMode) {
            screenModel.clearSelection()
        }

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        MigrateMangaScreen(
            navigateUp = {
                if (isSelectionMode) {
                    screenModel.clearSelection()
                } else {
                    navigator.pop()
                }
            },
            title = state.source!!.name,
            state = state,
            onClickItem = { manga ->
                if (isSelectionMode) {
                    screenModel.toggleSelection(manga)
                } else {
                    navigator.push(MigrateMangaSearchScreen(manga.id))
                }
            },
            onClickCover = { manga ->
                if (isSelectionMode) {
                    screenModel.toggleSelection(manga)
                } else {
                    navigator.push(MangaScreen(manga.id))
                }
            },
            onLongClickItem = screenModel::toggleSelection,
            onSelectAll = screenModel::selectAll,
            onClearSelection = screenModel::clearSelection,
            onClickMigrate = {
                navigator.push(mihon.feature.migration.config.MangaMigrationConfigScreen(state.selectedMangaIds))
            },
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    MigrationMangaEvent.FailedFetchingFavorites -> {
                        context.toast(MR.strings.internal_error)
                    }
                }
            }
        }
    }
}
