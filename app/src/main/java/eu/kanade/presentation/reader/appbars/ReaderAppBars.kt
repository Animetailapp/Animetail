package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.reader.components.ChapterNavigator
import eu.kanade.presentation.reader.components.ChapterNavigatorType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

private val animationSpec = tween<IntOffset>(200)
private val readerBarsSlideAnimationSpec = tween<IntOffset>(200)
private val readerBarsFadeAnimationSpec = tween<Float>(150)

@Composable
@Suppress("LongMethod")
fun ReaderAppBars(
    visible: Boolean,
    fullscreen: Boolean,

    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,

    chapterNavigatorType: ChapterNavigatorType,
    verticalNavigatorHeight: Float,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,

    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,
    // SY -->
    isExhToolsVisible: Boolean,
    onSetExhUtilsVisibility: (Boolean) -> Unit,
    isAutoScroll: Boolean,
    isAutoScrollEnabled: Boolean,
    onToggleAutoscroll: (Boolean) -> Unit,
    autoScrollFrequency: String,
    onSetAutoScrollFrequency: (String) -> Unit,
    onClickAutoScrollHelp: () -> Unit,
    onClickRetryAll: () -> Unit,
    onClickRetryAllHelp: () -> Unit,
    currentPageText: String,
    enabledButtons: Set<String>,
    dualPageSplitEnabled: Boolean,
    doublePages: Boolean,
    onClickPageLayout: () -> Unit,
    onClickShiftPage: () -> Unit,
    // SY <--
) {
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)

    val modifierWithInsetsPadding = if (fullscreen) {
        Modifier.systemBarsPadding()
    } else {
        Modifier
    }

    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(readerBarsSlideAnimationSpec) { -it } + fadeIn(readerBarsFadeAnimationSpec),
            exit = slideOutVertically(readerBarsSlideAnimationSpec) { -it } + fadeOut(readerBarsFadeAnimationSpec),
        ) {
            // SY -->
            Column(modifier = modifierWithInsetsPadding.clickable(onClick = onClickTopAppBar)) {
                AppBar(
                    modifier = Modifier,
                    backgroundColor = backgroundColor,
                    title = mangaTitle,
                    subtitle = chapterTitle,
                    navigateUp = navigateUp,
                    actions = {
                        AppBarActions(
                            actions = persistentListOf<AppBar.AppBarAction>().builder()
                                .apply {
                                    add(
                                        AppBar.Action(
                                            title = stringResource(
                                                if (bookmarked) {
                                                    MR.strings.action_remove_bookmark
                                                } else {
                                                    MR.strings.action_bookmark
                                                },
                                            ),
                                            icon = if (bookmarked) {
                                                Icons.Outlined.Bookmark
                                            } else {
                                                Icons.Outlined.BookmarkBorder
                                            },
                                            onClick = onToggleBookmarked,
                                        ),
                                    )
                                    onOpenInWebView?.let {
                                        add(
                                            AppBar.OverflowAction(
                                                title = stringResource(MR.strings.action_open_in_web_view),
                                                onClick = it,
                                            ),
                                        )
                                    }
                                    onOpenInBrowser?.let {
                                        add(
                                            AppBar.OverflowAction(
                                                title = stringResource(MR.strings.action_open_in_browser),
                                                onClick = it,
                                            ),
                                        )
                                    }
                                    onShare?.let {
                                        add(
                                            AppBar.OverflowAction(
                                                title = stringResource(MR.strings.action_share),
                                                onClick = it,
                                            ),
                                        )
                                    }
                                }
                                .build(),
                        )
                    },
                )
                // SY -->
                ExhUtils(
                    isVisible = isExhToolsVisible,
                    onSetExhUtilsVisibility = onSetExhUtilsVisibility,
                    backgroundColor = backgroundColor,
                    isAutoScroll = isAutoScroll,
                    isAutoScrollEnabled = isAutoScrollEnabled,
                    onToggleAutoscroll = onToggleAutoscroll,
                    autoScrollFrequency = autoScrollFrequency,
                    onSetAutoScrollFrequency = onSetAutoScrollFrequency,
                    onClickAutoScrollHelp = onClickAutoScrollHelp,
                    onClickRetryAll = onClickRetryAll,
                    onClickRetryAllHelp = onClickRetryAllHelp,
                )
                // SY <--
            } // SY <--
        }

        if (!chapterNavigatorType.isHorizontal()) {
            val sliderOnLeft = chapterNavigatorType == ChapterNavigatorType.VERTICAL_LEFT
            CompositionLocalProvider(
                LocalLayoutDirection provides if (sliderOnLeft) LayoutDirection.Ltr else LayoutDirection.Rtl,
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInHorizontally(readerBarsSlideAnimationSpec) { if (sliderOnLeft) -it else it } +
                            fadeIn(readerBarsFadeAnimationSpec),
                        exit = slideOutHorizontally(readerBarsSlideAnimationSpec) { if (sliderOnLeft) -it else it } +
                            fadeOut(readerBarsFadeAnimationSpec),
                    ) {
                        Row {
                            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                            Box(
                                modifier = Modifier.fillMaxHeight(),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                ChapterNavigator(
                                    modifier = Modifier.fillMaxHeight(verticalNavigatorHeight),
                                    type = chapterNavigatorType,
                                    onNextChapter = onNextChapter,
                                    enabledNext = enabledNext,
                                    onPreviousChapter = onPreviousChapter,
                                    enabledPrevious = enabledPrevious,
                                    currentPage = currentPage,
                                    currentPageText = currentPageText,
                                    totalPages = totalPages,
                                    onPageIndexChange = onPageIndexChange,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(readerBarsSlideAnimationSpec) { it } + fadeIn(readerBarsFadeAnimationSpec),
            exit = slideOutVertically(readerBarsSlideAnimationSpec) { it } + fadeOut(readerBarsFadeAnimationSpec),
        ) {
            Column(
                modifier = modifierWithInsetsPadding,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                if (chapterNavigatorType.isHorizontal()) {
                    ChapterNavigator(
                        type = chapterNavigatorType,
                        onNextChapter = onNextChapter,
                        enabledNext = enabledNext,
                        onPreviousChapter = onPreviousChapter,
                        enabledPrevious = enabledPrevious,
                        currentPage = currentPage,
                        currentPageText = currentPageText,
                        totalPages = totalPages,
                        onPageIndexChange = onPageIndexChange,
                    )
                }
                BottomReaderBar(
                    // SY -->
                    enabledButtons = enabledButtons,
                    // SY <--
                    backgroundColor = backgroundColor,
                    readingMode = readingMode,
                    onClickReadingMode = onClickReadingMode,
                    orientation = orientation,
                    onClickOrientation = onClickOrientation,
                    cropEnabled = cropEnabled,
                    onClickCropBorder = onClickCropBorder,
                    onClickSettings = onClickSettings,
                    // SY -->
                    dualPageSplitEnabled = dualPageSplitEnabled,
                    doublePages = doublePages,
                    onClickWebView = onOpenInWebView,
                    onClickShare = onShare,
                    onClickPageLayout = onClickPageLayout,
                    onClickShiftPage = onClickShiftPage,
                )
            }
        }
    }
}
