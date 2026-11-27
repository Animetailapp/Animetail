package eu.kanade.tachiyomi.ui.browse.anime.migration.anime.season

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.browse.anime.BrowseAnimeSourceContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialog
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialogScreenModel
import mihon.core.navigation.AnimeRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MigrateSeasonSelectRoute
import mihon.core.navigation.WebViewRoute
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.anime.LocalAnimeSource

@Composable
fun MigrateSeasonSelectScreen(
    oldAnimeId: Long,
    animeId: Long,
) {
    if (!ifAnimeSourcesLoaded()) {
        LoadingScreen()
        return
    }

    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.current

    val screenModel =
        assistedMetroViewModel<MigrateSeasonSelectScreenModel, MigrateSeasonSelectScreenModel.Factory> {
            create(oldAnimeId = oldAnimeId, animeId = animeId)
        }
    val state by screenModel.state.collectAsState()

    val oldAnime = state.oldAnime
    val anime = state.anime
    val source = state.source
    if (oldAnime == null || anime == null || source == null) {
        LoadingScreen()
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = anime.title,
                navigateUp = navigator::pop,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        val openMigrateDialog: (Anime) -> Unit = {
            screenModel.setDialog(MigrateSeasonSelectScreenModel.Dialog.Migrate(newAnime = it, oldAnime = oldAnime))
        }
        BrowseAnimeSourceContent(
            source = source,
            animeList = screenModel.seasonPagerFlowFlow.collectAsLazyPagingItems(),
            columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
            displayMode = screenModel.displayMode,
            snackbarHostState = snackbarHostState,
            contentPadding = paddingValues,
            onWebViewClick = {
                val httpSource = source as? AnimeHttpSource ?: return@BrowseAnimeSourceContent
                navigator.push(
                    WebViewRoute(
                        url = httpSource.getHomeUrl(),
                        initialTitle = httpSource.name,
                        sourceId = httpSource.id,
                    ),
                )
            },
            onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
            onLocalAnimeSourceHelpClick = { uriHandler.openUri(LocalAnimeSource.HELP_URL) },
            onAnimeClick = openMigrateDialog,
            onAnimeLongClick = { navigator.push(AnimeRoute(it.id, true)) },
        )
    }

    val onDismissRequest = { screenModel.setDialog(null) }
    when (val dialog = state.dialog) {
        is MigrateSeasonSelectScreenModel.Dialog.Migrate -> {
            MigrateAnimeDialog(
                oldAnime = dialog.oldAnime,
                newAnime = dialog.newAnime,
                screenModel = metroViewModel<MigrateAnimeDialogScreenModel>(),
                onDismissRequest = onDismissRequest,
                onClickTitle = { navigator.push(AnimeRoute(dialog.newAnime.id)) },
                onClickSeasons = { navigator.push(MigrateSeasonSelectRoute(oldAnime.id, dialog.newAnime.id)) },
                onPopScreen = {
                    onDismissRequest()
                },
            )
        }

        else -> {}
    }
}
