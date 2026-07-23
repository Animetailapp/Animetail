package eu.kanade.tachiyomi.ui.storage.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.storage.StorageScreenContent
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun Screen.mangaStorageTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow

    val viewModel = viewModel<MangaStorageViewModel>()
    val state by viewModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_manga,
        content = { contentPadding, _ ->
            StorageScreenContent(
                state = state,
                isManga = true,
                contentPadding = contentPadding,
                onCategorySelected = viewModel::setSelectedCategory,
                onDelete = viewModel::deleteEntry,
            )
        },
        navigateUp = navigator::pop,
    )
}
