package eu.kanade.presentation.browse.manga

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
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.entries.manga.components.BaseMangaListItem
import eu.kanade.tachiyomi.ui.browse.manga.migration.manga.MigrateMangaViewModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MigrateMangaScreen(
    navigateUp: () -> Unit,
    title: String?,
    state: MigrateMangaViewModel.State,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onClickMigrate: () -> Unit,
) {
    val isSelectionMode = state.selectedMangaIds.isNotEmpty()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = if (isSelectionMode) "${state.selectedMangaIds.size}" else title,
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

        MigrateMangaContent(
            contentPadding = contentPadding,
            state = state,
            onClickItem = onClickItem,
            onClickCover = onClickCover,
            onLongClickItem = onLongClickItem,
        )
    }
}

@Composable
private fun MigrateMangaContent(
    contentPadding: PaddingValues,
    state: MigrateMangaViewModel.State,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
) {
    val selectionMode = state.selectedMangaIds.isNotEmpty()
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(state.titles) { manga ->
            MigrateMangaItem(
                manga = manga,
                onClickItem = onClickItem,
                onClickCover = onClickCover,
                onLongClickItem = onLongClickItem,
                isSelected = manga.id in state.selectedMangaIds,
                selectionMode = selectionMode,
            )
        }
    }
}

@Composable
private fun MigrateMangaItem(
    manga: Manga,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    isSelected: Boolean,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    BaseMangaListItem(
        modifier = modifier,
        manga = manga,
        onClickItem = { onClickItem(manga) },
        onClickCover = { onClickCover(manga) },
        onLongClickItem = { onLongClickItem(manga) },
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
                data = manga,
                onClick = { onClickCover(manga) },
            )
        },
    )
}
