package eu.kanade.presentation.library.manga

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEach
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.manga.MangaLibrarySettingsScreenModel
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.manga.model.MangaLibraryGroup
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.manga.model.sort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.BaseSortItem
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.IconItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
@Suppress("MagicNumber")
fun MangaLibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    screenModel: MangaLibrarySettingsScreenModel,
    category: Category?,
    // SY -->
    hasCategories: Boolean,
    // SY <--
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
            // SY -->
            stringResource(TLMR.strings.group),
            // SY <--
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterPage(
                    screenModel = screenModel,
                )
                1 -> SortPage(
                    category = category,
                    screenModel = screenModel,
                )
                2 -> DisplayPage(
                    screenModel = screenModel,
                )
                // SY -->
                3 -> GroupPage(
                    screenModel = screenModel,
                    hasCategories = hasCategories,
                )
                // SY <--
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    screenModel: MangaLibrarySettingsScreenModel,
) {
    val filterDownloaded by screenModel.libraryPreferences.filterDownloadedManga().collectAsState()
    val downloadedOnly by screenModel.preferences.downloadedOnly().collectAsState()
    val autoUpdateMangaRestrictions by screenModel.libraryPreferences.autoUpdateItemRestrictions().collectAsState()

    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = if (downloadedOnly) {
            TriState.ENABLED_IS
        } else {
            filterDownloaded
        },
        enabled = !downloadedOnly,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterDownloadedManga) },
    )
    val filterUnread by screenModel.libraryPreferences.filterUnread().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = filterUnread,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterUnread) },
    )
    val filterStarted by screenModel.libraryPreferences.filterStartedManga().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.label_started),
        state = filterStarted,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterStartedManga) },
    )
    val filterBookmarked by screenModel.libraryPreferences.filterBookmarkedManga().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = filterBookmarked,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterBookmarkedManga) },
    )
    val filterCompleted by screenModel.libraryPreferences.filterCompletedManga().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.completed),
        state = filterCompleted,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterCompletedManga) },
    )

    // TODO: re-enable when custom intervals are ready for stable
    if ((!isReleaseBuildType) && LibraryPreferences.ENTRY_OUTSIDE_RELEASE_PERIOD in autoUpdateMangaRestrictions) {
        val filterIntervalCustom by screenModel.libraryPreferences.filterIntervalCustom().collectAsState()
        TriStateItem(
            label = stringResource(MR.strings.action_filter_interval_custom),
            state = filterIntervalCustom,
            onClick = { screenModel.toggleFilter(LibraryPreferences::filterIntervalCustom) },
        )
    }

    val trackers by screenModel.trackersFlow.collectAsState()
    when (trackers.size) {
        0 -> {
            // No trackers
        }
        1 -> {
            val service = trackers[0]
            val filterTracker by screenModel.libraryPreferences.filterTrackedManga(
                service.id.toInt(),
            ).collectAsState()
            TriStateItem(
                label = stringResource(MR.strings.action_filter_tracked),
                state = filterTracker,
                onClick = { screenModel.toggleTracker(service.id.toInt()) },
            )
        }
        else -> {
            HeadingItem(MR.strings.action_filter_tracked)
            trackers.map { service ->
                val filterTracker by screenModel.libraryPreferences.filterTrackedManga(
                    service.id.toInt(),
                ).collectAsState()
                TriStateItem(
                    label = service.name,
                    state = filterTracker,
                    onClick = { screenModel.toggleTracker(service.id.toInt()) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SortPage(
    category: Category?,
    screenModel: MangaLibrarySettingsScreenModel,
) {
    val trackers by screenModel.trackersFlow.collectAsState()
    // SY -->
    val globalSortMode by screenModel.libraryPreferences.mangaSortingMode().collectAsState()
    val sortingMode = if (screenModel.grouping == MangaLibraryGroup.BY_DEFAULT) {
        category.sort.type
    } else {
        globalSortMode.type
    }
    val sortDescending = if (screenModel.grouping == MangaLibraryGroup.BY_DEFAULT) {
        category.sort.isAscending
    } else {
        globalSortMode.isAscending
    }.not()
    // SY <--

    val options = remember(trackers.isEmpty()) {
        val trackerMeanPair = if (trackers.isNotEmpty()) {
            MR.strings.action_sort_tracker_score to MangaLibrarySort.Type.TrackerMean
        } else {
            null
        }
        listOfNotNull(
            MR.strings.action_sort_alpha to MangaLibrarySort.Type.Alphabetical,
            MR.strings.action_sort_total to MangaLibrarySort.Type.TotalChapters,
            MR.strings.action_sort_last_read to MangaLibrarySort.Type.LastRead,
            AYMR.strings.action_sort_last_manga_update to MangaLibrarySort.Type.LastUpdate,
            MR.strings.action_sort_unread_count to MangaLibrarySort.Type.UnreadCount,
            MR.strings.action_sort_latest_chapter to MangaLibrarySort.Type.LatestChapter,
            MR.strings.action_sort_chapter_fetch_date to MangaLibrarySort.Type.ChapterFetchDate,
            MR.strings.action_sort_date_added to MangaLibrarySort.Type.DateAdded,
            trackerMeanPair,
            MR.strings.action_sort_random to MangaLibrarySort.Type.Random,
        )
    }

    options.map { (titleRes, mode) ->
        if (mode == MangaLibrarySort.Type.Random) {
            BaseSortItem(
                label = stringResource(titleRes),
                icon = Icons.Default.Refresh
                    .takeIf { sortingMode == MangaLibrarySort.Type.Random },
                onClick = {
                    screenModel.setSort(category, mode, MangaLibrarySort.Direction.Ascending)
                },
            )
            return@map
        }
        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = {
                val isTogglingDirection = sortingMode == mode
                val direction = when {
                    isTogglingDirection -> if (sortDescending) {
                        MangaLibrarySort.Direction.Ascending
                    } else {
                        MangaLibrarySort.Direction.Descending
                    }
                    else -> if (sortDescending) {
                        MangaLibrarySort.Direction.Descending
                    } else {
                        MangaLibrarySort.Direction.Ascending
                    }
                }
                screenModel.setSort(category, mode, direction)
            },
        )
    }
}

private val displayModes = listOf(
    MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
    MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
    MR.strings.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
    MR.strings.action_display_list to LibraryDisplayMode.List,
)

@Composable
private fun ColumnScope.DisplayPage(
    screenModel: MangaLibrarySettingsScreenModel,
) {
    val displayMode by screenModel.libraryPreferences.displayMode().collectAsState()
    SettingsChipRow(MR.strings.action_display_mode) {
        displayModes.map { (titleRes, mode) ->
            FilterChip(
                selected = displayMode == mode,
                onClick = { screenModel.setDisplayMode(mode) },
                label = { Text(stringResource(titleRes)) },
            )
        }
    }

    val configuration = LocalConfiguration.current
    val columnPreference = remember {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            screenModel.libraryPreferences.mangaLandscapeColumns()
        } else {
            screenModel.libraryPreferences.mangaPortraitColumns()
        }
    }

    val columns by columnPreference.collectAsState()
    if (displayMode == LibraryDisplayMode.List) {
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(AYMR.strings.pref_library_rows),
            valueText = if (columns > 0) {
                columns.toString()
            } else {
                stringResource(MR.strings.label_auto)
            },
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    } else {
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(MR.strings.pref_library_columns),
            valueText = if (columns > 0) {
                columns.toString()
            } else {
                stringResource(MR.strings.label_auto)
            },
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    HeadingItem(MR.strings.overlay_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_download_badge),
        pref = screenModel.libraryPreferences.downloadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_unread_badge),
        pref = screenModel.libraryPreferences.unreadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_local_badge),
        pref = screenModel.libraryPreferences.localBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_language_badge),
        pref = screenModel.libraryPreferences.languageBadge(),
    )
    CheckboxItem(
        label = stringResource(AYMR.strings.action_display_show_continue_reading_button),
        pref = screenModel.libraryPreferences.showContinueViewingButton(),
    )

    HeadingItem(MR.strings.tabs_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_tabs),
        pref = screenModel.libraryPreferences.categoryTabs(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_number_of_items),
        pref = screenModel.libraryPreferences.categoryNumberOfItems(),
    )
}

data class GroupMode(
    val int: Int,
    val nameRes: Int,
    val drawableRes: Int,
)

private fun groupTypeDrawableRes(type: Int): Int {
    return when (type) {
        MangaLibraryGroup.BY_STATUS -> R.drawable.ic_progress_clock_24dp
        MangaLibraryGroup.BY_TRACK_STATUS -> R.drawable.ic_sync_24dp
        MangaLibraryGroup.BY_SOURCE -> R.drawable.ic_browse_filled_24dp
        MangaLibraryGroup.BY_TAG -> R.drawable.ic_tag_24dp
        MangaLibraryGroup.UNGROUPED -> R.drawable.ic_ungroup_24dp
        else -> R.drawable.ic_label_24dp
    }
}

@Composable
private fun ColumnScope.GroupPage(
    screenModel: MangaLibrarySettingsScreenModel,
    hasCategories: Boolean,
) {
    val trackers by screenModel.trackersFlow.collectAsState()

    val groups = remember(hasCategories, trackers) {
        buildList {
            add(MangaLibraryGroup.BY_DEFAULT)
            add(MangaLibraryGroup.BY_SOURCE)
            add(MangaLibraryGroup.BY_TAG)
            add(MangaLibraryGroup.BY_STATUS)
            if (trackers.isNotEmpty()) {
                add(MangaLibraryGroup.BY_TRACK_STATUS)
            }
            if (hasCategories) {
                add(MangaLibraryGroup.UNGROUPED)
            }
        }.map {
            GroupMode(
                it,
                MangaLibraryGroup.groupTypeStringRes(it, hasCategories),
                groupTypeDrawableRes(it),
            )
        }
    }

    groups.fastForEach {
        IconItem(
            label = stringResource(it.nameRes),
            icon = painterResource(it.drawableRes),
            selected = it.int == screenModel.grouping,
            onClick = {
                screenModel.setGrouping(it.int)
            },
        )
    }
}
