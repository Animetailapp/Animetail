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
import androidx.compose.material3.Checkbox
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
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.anime.components.AnimeSourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mihon.core.navigation.AnimeMigrationListRoute
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MigrateAnimeSearchRoute
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.shouldExpandFAB

@Composable
fun AnimeMigrationConfigScreen(animeIds: Collection<Long>) {
    val navigator = LocalNavigator.current

    val screenModel = metroViewModel<AnimeMigrationConfigViewModel>()
    val state by screenModel.state.collectAsState()

    var migrationSheetOpen by rememberSaveable { mutableStateOf(false) }

    fun continueMigration(openSheet: Boolean, extraSearchQuery: String?) {
        val animeId = animeIds.singleOrNull()
        if (animeId == null && openSheet) {
            migrationSheetOpen = true
            return
        }
        val route = if (animeId == null) {
            AnimeMigrationListRoute(animeIds, extraSearchQuery)
        } else {
            MigrateAnimeSearchRoute(animeId)
        }
        navigator.replace(route)
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
        AnimeMigrationConfigScreenSheet(
            onDismissRequest = { migrationSheetOpen = false },
            onStartMigration = { extraSearchQuery ->
                migrationSheetOpen = false
                continueMigration(false, extraSearchQuery)
            },
        )
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeMigrationConfigViewModel(
    private val sourceManager: AnimeSourceManager,
    private val sourcePreferences: SourcePreferences,
) : ViewModel() {

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    init {
        initSources()
    }

    private fun initSources() {
        val languages = sourcePreferences.enabledLanguages.get()
        val pinnedSources = sourcePreferences.pinnedAnimeSources.get().mapNotNull { it.toLongOrNull() }
        val includedSources = sourcePreferences.migrationAnimeSources.get()
        val sources = sourceManager.getCatalogueSources()
            .filterIsInstance<AnimeHttpSource>()
            .filter { it.lang in languages }
            .sortedWith(
                compareByDescending<AnimeHttpSource> { it.id in includedSources }
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
            sourcePreferences.migrationAnimeSources.set(includedSourceIds)
            state.copy(includedSourceIds = includedSourceIds)
        }
    }

    fun selectAllEnabled() {
        val languages = sourcePreferences.enabledLanguages.get()
        val disabledSources = sourcePreferences.disabledAnimeSources.get()
            .mapNotNull { it.toLongOrNull() }
        val enabledSourceIds = sourceManager.getCatalogueSources()
            .filterIsInstance<AnimeHttpSource>()
            .filter { it.lang in languages && it.id !in disabledSources }
            .map { it.id }

        mutableState.update { state ->
            val includedSourceIds = (state.includedSourceIds + enabledSourceIds).distinct()
            sourcePreferences.migrationAnimeSources.set(includedSourceIds)
            state.copy(includedSourceIds = includedSourceIds)
        }
    }

    fun selectAllPinned() {
        val pinnedSources = sourcePreferences.pinnedAnimeSources.get().mapNotNull { it.toLongOrNull() }
        mutableState.update { state ->
            val includedSourceIds = (state.includedSourceIds + pinnedSources).distinct()
            sourcePreferences.migrationAnimeSources.set(includedSourceIds)
            state.copy(includedSourceIds = includedSourceIds)
        }
    }

    fun unselectAll() {
        mutableState.update { state ->
            sourcePreferences.migrationAnimeSources.set(emptyList())
            state.copy(includedSourceIds = emptyList())
        }
    }

    fun reorderSources(fromIndex: Int, toIndex: Int) {
        mutableState.update { state ->
            val sources = state.sources.toMutableList()
            val source = sources.removeAt(fromIndex)
            sources.add(toIndex, source)

            val includedSourceIds = sources.map { it.id }.filter { it in state.includedSourceIds }
            sourcePreferences.migrationAnimeSources.set(includedSourceIds)

            state.copy(
                sources = sources,
                includedSourceIds = includedSourceIds,
            )
        }
    }

    data class State(
        val isLoading: Boolean = false,
        val sources: List<AnimeSource> = emptyList(),
        val includedSourceIds: List<Long> = emptyList(),
    )
}

@Composable
private fun ReorderableCollectionItemScope.MigrationSourceItem(
    source: AnimeSource,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onSelect),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                )
                AnimeSourceIcon(source = source)
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
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
        content = {
            Text(
                text = source.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

private fun AnimeHttpSource.toDomainSource(): AnimeSource {
    return AnimeSource(
        id = id,
        lang = lang,
        name = name,
        supportsLatest = supportsLatest,
        isStub = false,
    )
}
