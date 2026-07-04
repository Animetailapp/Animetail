package mihon.feature.migration.config

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.manga.components.MangaSourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.update
import mihon.feature.migration.list.MangaMigrationListScreen
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.manga.model.Source
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.shouldExpandFAB
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaMigrationConfigScreen(private val mangaIds: Collection<Long>) : Screen() {

    constructor(mangaId: Long) : this(listOf(mangaId))

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { ScreenModel() }
        val state by screenModel.state.collectAsState()

        var migrationSheetOpen by rememberSaveable { mutableStateOf(false) }

        fun continueMigration(openSheet: Boolean, extraSearchQuery: String?) {
            val mangaId = mangaIds.singleOrNull()
            if (mangaId == null && openSheet) {
                migrationSheetOpen = true
                return
            }
            val screen = if (mangaId == null) {
                MangaMigrationListScreen(mangaIds, extraSearchQuery)
            } else {
                MigrateMangaSearchScreen(mangaId)
            }
            navigator.replace(screen)
        }

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.migrationConfigScreen_selectedHeader),
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectEnabledLabel),
                                    icon = Icons.Outlined.SelectAll,
                                    onClick = { screenModel.selectAllEnabled() },
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectPinnedLabel),
                                    icon = Icons.Outlined.SelectAll,
                                    onClick = { screenModel.selectAllPinned() },
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.migrationConfigScreen_selectNoneLabel),
                                    icon = Icons.Outlined.Deselect,
                                    onClick = { screenModel.unselectAll() },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                val lazyListState = rememberLazyListState()
                SmallExtendedFloatingActionButton(
                    text = { Text(text = stringResource(MR.strings.migrationConfigScreen_continueButtonText)) },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                        )
                    },
                    onClick = { continueMigration(true, null) },
                    expanded = lazyListState.shouldExpandFAB(),
                )
            },
        ) { contentPadding ->
            val lazyListState = rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                screenModel.reorderSources(from.index, to.index)
            }

            FastScrollLazyColumn(
                state = lazyListState,
                contentPadding = contentPadding,
            ) {
                itemsIndexed(
                    items = state.sources,
                    key = { _, source -> source.id },
                ) { index, source ->
                    ReorderableItem(reorderableState, key = source.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                        ElevatedCard(
                            shape = RoundedCornerShape(0.dp),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation),
                        ) {
                            MigrationSourceItem(
                                source = source,
                                isSelected = source.id in state.includedSourceIds,
                                onSelect = { screenModel.toggleSource(source.id) },
                            )
                        }
                    }
                }
            }
        }

        if (migrationSheetOpen) {
            MangaMigrationConfigScreenSheet(
                onDismissRequest = { migrationSheetOpen = false },
                onStartMigration = { extraSearchQuery ->
                    migrationSheetOpen = false
                    continueMigration(false, extraSearchQuery)
                },
            )
        }
    }

    private class ScreenModel(
        private val sourceManager: MangaSourceManager = Injekt.get(),
        private val sourcePreferences: SourcePreferences = Injekt.get(),
    ) : StateScreenModel<ScreenModel.State>(State()) {

        init {
            initSources()
        }

        private fun initSources() {
            val languages = sourcePreferences.enabledLanguages.get()
            val pinnedSources = sourcePreferences.pinnedMangaSources.get().mapNotNull { it.toLongOrNull() }
            val includedSources = sourcePreferences.migrationMangaSources.get()
            val sources = sourceManager.getCatalogueSources()
                .filterIsInstance<HttpSource>()
                .filter { it.lang in languages }
                .sortedWith(
                    compareByDescending<HttpSource> { it.id in includedSources }
                        .thenByDescending { it.id in pinnedSources }
                        .thenBy { it.name }
                        .thenBy { it.lang },
                )
                .map { it.toDomainSource() }

            mutableState.update {
                it.copy(
                    sources = sources,
                    includedSourceIds = includedSources,
                )
            }
        }

        fun toggleSource(sourceId: Long) {
            mutableState.update { state ->
                val includedSourceIds = if (sourceId in state.includedSourceIds) {
                    state.includedSourceIds - sourceId
                } else {
                    state.includedSourceIds + sourceId
                }
                sourcePreferences.migrationMangaSources.set(includedSourceIds)
                state.copy(includedSourceIds = includedSourceIds)
            }
        }

        fun selectAllEnabled() {
            val languages = sourcePreferences.enabledLanguages.get()
            val disabledSources = sourcePreferences.disabledMangaSources.get()
                .mapNotNull { it.toLongOrNull() }
            val enabledSourceIds = sourceManager.getCatalogueSources()
                .filterIsInstance<HttpSource>()
                .filter { it.lang in languages && it.id !in disabledSources }
                .map { it.id }

            mutableState.update { state ->
                val includedSourceIds = (state.includedSourceIds + enabledSourceIds).distinct()
                sourcePreferences.migrationMangaSources.set(includedSourceIds)
                state.copy(includedSourceIds = includedSourceIds)
            }
        }

        fun selectAllPinned() {
            val pinnedSources = sourcePreferences.pinnedMangaSources.get().mapNotNull { it.toLongOrNull() }
            mutableState.update { state ->
                val includedSourceIds = (state.includedSourceIds + pinnedSources).distinct()
                sourcePreferences.migrationMangaSources.set(includedSourceIds)
                state.copy(includedSourceIds = includedSourceIds)
            }
        }

        fun unselectAll() {
            mutableState.update { state ->
                sourcePreferences.migrationMangaSources.set(emptyList())
                state.copy(includedSourceIds = emptyList())
            }
        }

        fun reorderSources(fromIndex: Int, toIndex: Int) {
            mutableState.update { state ->
                val sources = state.sources.toMutableList()
                val source = sources.removeAt(fromIndex)
                sources.add(toIndex, source)

                val includedSourceIds = sources.map { it.id }.filter { it in state.includedSourceIds }
                sourcePreferences.migrationMangaSources.set(includedSourceIds)

                state.copy(
                    sources = sources,
                    includedSourceIds = includedSourceIds,
                )
            }
        }

        data class State(
            val isLoading: Boolean = false,
            val sources: List<Source> = emptyList(),
            val includedSourceIds: List<Long> = emptyList(),
        )
    }
}

@Composable
private fun ReorderableCollectionItemScope.MigrationSourceItem(
    source: Source,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = source.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                Text(
                    text = LocaleHelper.getDisplayName(source.lang),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (source.isStub) {
                    Pill(
                        text = stringResource(MR.strings.not_installed),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        leadingContent = {
            MangaSourceIcon(source = source)
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                val icon = if (isSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    modifier = Modifier.draggableHandle(),
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        ),
        modifier = Modifier.clickable(onClick = onSelect),
    )
}

private fun HttpSource.toDomainSource(): Source {
    return Source(
        id = id,
        lang = lang,
        name = name,
        supportsLatest = supportsLatest,
        isStub = false,
    )
}
