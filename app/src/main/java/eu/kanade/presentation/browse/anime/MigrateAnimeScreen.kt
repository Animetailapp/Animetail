package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.entries.anime.components.BaseAnimeListItem
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.MigrateAnimeScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MigrateAnimeScreen(
    navigateUp: () -> Unit,
    title: String?,
    state: MigrateAnimeScreenModel.State,
    onClickItem: (Anime) -> Unit,
    onClickCover: (Anime) -> Unit,
    onLongClickItem: (Anime) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onClickMigrate: () -> Unit,
) {
    val isSelectionMode = state.selectedAnimeIds.isNotEmpty()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = if (isSelectionMode) "${state.selectedAnimeIds.size}" else title,
                navigateUp = navigateUp,
                actions = {
                    if (isSelectionMode) {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_select_all),
                                    icon = Icons.Outlined.SelectAll,
                                    onClick = onSelectAll,
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectNoneLabel),
                                    icon = Icons.Outlined.Deselect,
                                    onClick = onClearSelection,
                                ),
                            ),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (isSelectionMode) {
                SmallExtendedFloatingActionButton(
                    text = { Text(text = stringResource(MR.strings.migrationConfigScreen_continueButtonText)) },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                        )
                    },
                    onClick = onClickMigrate,
                )
            }
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        MigrateAnimeContent(
            contentPadding = contentPadding,
            state = state,
            onClickItem = onClickItem,
            onClickCover = onClickCover,
            onLongClickItem = onLongClickItem,
        )
    }
}

@Composable
private fun MigrateAnimeContent(
    contentPadding: PaddingValues,
    state: MigrateAnimeScreenModel.State,
    onClickItem: (Anime) -> Unit,
    onClickCover: (Anime) -> Unit,
    onLongClickItem: (Anime) -> Unit,
) {
    val selectionMode = state.selectedAnimeIds.isNotEmpty()
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(state.titles) { anime ->
            MigrateAnimeItem(
                anime = anime,
                onClickItem = onClickItem,
                onClickCover = onClickCover,
                onLongClickItem = onLongClickItem,
                isSelected = anime.id in state.selectedAnimeIds,
                selectionMode = selectionMode,
            )
        }
    }
}

@Composable
private fun MigrateAnimeItem(
    anime: Anime,
    onClickItem: (Anime) -> Unit,
    onClickCover: (Anime) -> Unit,
    onLongClickItem: (Anime) -> Unit,
    isSelected: Boolean,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    BaseAnimeListItem(
        modifier = modifier,
        anime = anime,
        onClickItem = { onClickItem(anime) },
        onClickCover = { onClickCover(anime) },
        onLongClickItem = { onLongClickItem(anime) },
        cover = {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                )
                Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))
            }
            ItemCover.Book(
                modifier = Modifier.fillMaxHeight(),
                data = anime,
                onClick = { onClickCover(anime) },
            )
        },
    )
}
