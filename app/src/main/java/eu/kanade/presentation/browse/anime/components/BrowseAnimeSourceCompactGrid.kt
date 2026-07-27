package eu.kanade.presentation.browse.anime.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryCompactGridItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseAnimeSourceCompactGrid(
    animeList: LazyPagingItems<StateFlow<Anime>>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: (Anime) -> Unit,
) {
    // Detectar si estamos en Android TV
    val context = LocalContext.current
    val isAndroidTV = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }

    // Crear un focus requester para la cuadrícula
    val gridFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    // Enfocar automáticamente el primer item al cargar en Android TVAdd commentMore actions
    LaunchedEffect(isAndroidTV, animeList.itemCount) {
        if (isAndroidTV && animeList.itemCount > 0) {
            delay(300) // Esperar a que la UI esté lista
            gridFocusRequester.requestFocus()
        }
    }

    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(
            CommonEntryItemDefaults.GridVerticalSpacer + if (isAndroidTV) 4.dp else 0.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(
            CommonEntryItemDefaults.GridHorizontalSpacer + if (isAndroidTV) 4.dp else 0.dp,
        ),
        state = gridState,
        modifier = Modifier.focusRequester(gridFocusRequester),
    ) {
        if (animeList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(count = animeList.itemCount) { index ->
            val anime by animeList[index]?.collectAsState() ?: return@items
            BrowseAnimeSourceCompactGridItem(
                anime = anime,
                onClick = { onAnimeClick(anime) },
                onLongClick = { onAnimeLongClick(anime) },
                // Asignar el índice para marcar el primer elementoAdd commentMore actions
                isFirstItem = index == 0 && isAndroidTV,
            )
        }

        if (animeList.loadState.refresh is LoadState.Loading || animeList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
fun BrowseAnimeSourceCompactGridItem(
    anime: Anime,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
    isSelected: Boolean = false,
    isFirstItem: Boolean = false,
) {
    EntryCompactGridItem(
        title = anime.title,
        coverData = AnimeCover(
            animeId = anime.id,
            sourceId = anime.source,
            isAnimeFavorite = anime.favorite,
            url = anime.thumbnailUrl,
            lastModified = anime.coverLastModified,
        ),
        // KMK -->
        isSelected = isSelected,
        coverAlpha = if (anime.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        coverBadgeStart = {
            InLibraryBadge(enabled = anime.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
        isFirstItem = isFirstItem,
    )
}
