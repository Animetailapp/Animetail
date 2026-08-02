package eu.kanade.tachiyomi.ui.browse.anime.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.AnimeSourceOptionsDialog
import eu.kanade.presentation.browse.anime.AnimeSourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.anime.source.SourcesScreen.SmartSearchConfig
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceViewModel
import eu.kanade.tachiyomi.ui.browse.anime.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.animeSourcesTab(
    smartSearchConfig: SmartSearchConfig? = null,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = viewModel<AnimeSourcesViewModel>(
        factory = AnimeSourcesViewModel.Factory,
        extras = CreationExtras {
            set(AnimeSourcesViewModel.SMART_SEARCH_CONFIG_KEY, smartSearchConfig)
        },
    )
    val state by viewModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_anime_sources,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navigator.push(GlobalAnimeSearchScreen()) },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { navigator.push(AnimeSourcesFilterScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            AnimeSourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    // SY -->
                    val screen = when {
                        listing == BrowseAnimeSourceViewModel.Listing.Popular &&
                            viewModel.useNewSourceNavigation -> SourceFeedScreen(source.id)

                        else -> BrowseAnimeSourceScreen(source.id, listing.query)
                    }
                    navigator.push(screen)
                    // SY <--
                },
                onClickPin = viewModel::togglePin,
                onLongClickItem = viewModel::showSourceDialog,
            )

            state.dialog?.let { dialog ->
                val source = dialog.source
                AnimeSourceOptionsDialog(
                    source = source,
                    onClickPin = {
                        viewModel.togglePin(source)
                        viewModel.closeDialog()
                    },
                    onClickDisable = {
                        viewModel.toggleSource(source)
                        viewModel.closeDialog()
                    },
                    onDismiss = viewModel::closeDialog,
                )
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        AnimeSourcesViewModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
