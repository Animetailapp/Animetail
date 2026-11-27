package eu.kanade.tachiyomi.ui.storage.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.storage.StorageScreenContent
import mihon.core.navigation.LocalNavigator
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun mangaStorageTab(): TabContent {
    val navigator = LocalNavigator.current

    val viewModel = metroViewModel<MangaStorageViewModel>()
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
