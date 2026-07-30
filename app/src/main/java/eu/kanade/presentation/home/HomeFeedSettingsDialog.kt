package eu.kanade.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.home.HeroSource
import eu.kanade.tachiyomi.ui.home.HomeFeedScreenModel
import eu.kanade.tachiyomi.ui.home.HomeMediaFilter
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Control segmentado compacto reutilizable.
 */
@Composable
private fun <T> CompactSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                val surfaceColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
                val textColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    color = surfaceColor,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(value) },
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Diálogo modal para personalizar las opciones y secciones del Feed de Inicio.
 */
@Composable
fun HomeFeedSettingsDialog(
    state: HomeFeedScreenModel.State,
    onToggleSection: (String) -> Unit,
    onSetMediaFilter: (HomeMediaFilter) -> Unit,
    onToggleAutoScrollHero: () -> Unit,
    onSetHeroSource: (HeroSource) -> Unit,
    onSetItemsPerSection: (Int) -> Unit,
    onToggleHideCompleted: () -> Unit,
    onToggleEnableTmdb: () -> Unit,
    onToggleEnableAnilist: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(MR.strings.content_filter_title),
            stringResource(MR.strings.show_featured),
            stringResource(MR.strings.visible_sections_title),
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> ContentFilterTab(
                    state = state,
                    onSetMediaFilter = onSetMediaFilter,
                    onSetItemsPerSection = onSetItemsPerSection,
                    onToggleHideCompleted = onToggleHideCompleted,
                    onToggleEnableTmdb = onToggleEnableTmdb,
                    onToggleEnableAnilist = onToggleEnableAnilist,
                )

                1 -> FeaturedSettingsTab(
                    state = state,
                    onToggleAutoScrollHero = onToggleAutoScrollHero,
                    onSetHeroSource = onSetHeroSource,
                )

                2 -> VisibleSectionsTab(
                    state = state,
                    onToggleSection = onToggleSection,
                )
            }
        }
    }
}

@Composable
private fun ContentFilterTab(
    state: HomeFeedScreenModel.State,
    onSetMediaFilter: (HomeMediaFilter) -> Unit,
    onSetItemsPerSection: (Int) -> Unit,
    onToggleHideCompleted: () -> Unit,
    onToggleEnableTmdb: () -> Unit,
    onToggleEnableAnilist: () -> Unit,
) {
    HeadingItem(stringResource(MR.strings.content_filter_title))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        CompactSegmentedControl(
            options = listOf(
                HomeMediaFilter.ALL to stringResource(MR.strings.home_media_filter_all),
                HomeMediaFilter.VIDEO_ONLY to stringResource(MR.strings.home_media_filter_video),
                HomeMediaFilter.MANGA_ONLY to stringResource(MR.strings.home_media_filter_manga),
            ),
            selected = state.mediaFilter,
            onSelect = onSetMediaFilter,
        )
    }

    HeadingItem(stringResource(MR.strings.items_per_section))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        CompactSegmentedControl(
            options = listOf(
                6 to stringResource(MR.strings.items_count_format, 6),
                12 to stringResource(MR.strings.items_count_format, 12),
                24 to stringResource(MR.strings.items_count_format, 24),
            ),
            selected = state.itemsPerSection,
            onSelect = onSetItemsPerSection,
        )
    }

    HeadingItem(stringResource(MR.strings.additional_filters_title))
    CheckboxItem(
        label = stringResource(MR.strings.hide_completed_recommended),
        checked = state.hideCompletedInRecommended,
        onClick = onToggleHideCompleted,
    )
    CheckboxItem(
        label = stringResource(MR.strings.home_enable_tmdb),
        checked = state.enableTmdb,
        onClick = onToggleEnableTmdb,
    )
    CheckboxItem(
        label = stringResource(MR.strings.home_enable_anilist),
        checked = state.enableAnilist,
        onClick = onToggleEnableAnilist,
    )
}

@Composable
private fun FeaturedSettingsTab(
    state: HomeFeedScreenModel.State,
    onToggleAutoScrollHero: () -> Unit,
    onSetHeroSource: (HeroSource) -> Unit,
) {
    HeadingItem(stringResource(MR.strings.show_featured))
    CheckboxItem(
        label = stringResource(MR.strings.auto_scroll_hero),
        checked = state.autoScrollHero,
        onClick = onToggleAutoScrollHero,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        CompactSegmentedControl(
            options = listOf(
                HeroSource.BOTH to stringResource(MR.strings.hero_source_both),
                HeroSource.LIBRARY_ONLY to stringResource(MR.strings.hero_source_library),
            ),
            selected = state.heroSource,
            onSelect = onSetHeroSource,
        )
    }
}

@Composable
private fun VisibleSectionsTab(
    state: HomeFeedScreenModel.State,
    onToggleSection: (String) -> Unit,
) {
    HeadingItem(stringResource(MR.strings.visible_sections_title))
    CheckboxItem(
        label = stringResource(MR.strings.show_featured),
        checked = state.showFeatured,
        onClick = { onToggleSection("featured") },
    )
    CheckboxItem(
        label = stringResource(MR.strings.show_continue),
        checked = state.showContinue,
        onClick = { onToggleSection("continue") },
    )
    CheckboxItem(
        label = stringResource(MR.strings.show_because_you_watched),
        checked = state.showBecauseYouWatched,
        onClick = { onToggleSection("because_you_watched") },
    )
    CheckboxItem(
        label = stringResource(MR.strings.show_recommended),
        checked = state.showRecommended,
        onClick = { onToggleSection("recommended") },
    )
    CheckboxItem(
        label = stringResource(MR.strings.label_popular_movies),
        checked = state.showPopularMovies,
        onClick = { onToggleSection("popular_movies") },
    )
    CheckboxItem(
        label = stringResource(MR.strings.label_popular_series),
        checked = state.showPopularSeries,
        onClick = { onToggleSection("popular_series") },
    )
    CheckboxItem(
        label = stringResource(MR.strings.show_popular_anime),
        checked = state.showPopularAnime,
        onClick = { onToggleSection("popular_anime") },
    )
    CheckboxItem(
        label = stringResource(MR.strings.show_popular_manga),
        checked = state.showPopularManga,
        onClick = { onToggleSection("popular_manga") },
    )
}
