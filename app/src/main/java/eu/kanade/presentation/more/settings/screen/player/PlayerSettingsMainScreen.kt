package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.navigation3.runtime.NavKey
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.Navigator
import mihon.core.navigation.PlayerSettingsCustomButtonRoute
import mihon.core.navigation.PlayerSettingsEditorRoute
import mihon.core.navigation.SettingsSearchRoute
import mihon.presentation.core.util.isTabletUi
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Magnet

@Serializable
data class PlayerSettingsMainScreen(val mainSettings: Boolean) : NavKey {

    @Composable
    private fun getPalerSurface(): Color {
        val surface = MaterialTheme.colorScheme.surface
        val dark = isSystemInDarkTheme()
        return remember(surface, dark) {
            val arr = FloatArray(3)
            ColorUtils.colorToHSL(surface.toArgb(), arr)
            arr[2] = if (dark) {
                arr[2] - 0.05f
            } else {
                arr[2] + 0.02f
            }.coerceIn(0f, 1f)
            Color.hsl(arr[0], arr[1], arr[2])
        }
    }

    @Composable
    fun Content() {
        val navigator = LocalNavigator.current
        val twoPane = isTabletUi()
        val containerColor = if (twoPane) getPalerSurface() else MaterialTheme.colorScheme.surface
        val topBarState = rememberTopAppBarState()

        Scaffold(
            topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState),
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(
                        if (mainSettings) {
                            AYMR.strings.label_player
                        } else {
                            AYMR.strings.label_player_settings
                        },
                    ),
                    navigateUp = {
                        navigator.popTo { it == this }
                        navigator.pop()
                    },
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_search),
                                    icon = Icons.Outlined.Search,
                                    onClick = { navigator.navigate(SettingsSearchRoute(isPlayer = true), twoPane) },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            containerColor = containerColor,
            content = { contentPadding ->
                val state = rememberLazyListState()
                val indexSelected = if (twoPane) {
                    val detail = navigator.routes.takeLastWhile { it != this }.firstOrNull()
                    items.indexOfFirst { it.screen == detail }
                        .takeIf { it > -1 }
                        ?.also {
                            LaunchedEffect(Unit) {
                                state.animateScrollToItem(it)
                                if (it > 0) {
                                    // Lift scroll
                                    topBarState.contentOffset = topBarState.heightOffsetLimit
                                }
                            }
                        }
                } else {
                    null
                }

                LazyColumn(
                    state = state,
                    contentPadding = contentPadding,
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, item -> item.hashCode() },
                    ) { index, item ->
                        val selected = indexSelected == index
                        var modifier: Modifier = Modifier
                        var contentColor = LocalContentColor.current
                        if (twoPane) {
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .then(
                                    if (selected) {
                                        Modifier.background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                            if (selected) {
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }
                        CompositionLocalProvider(LocalContentColor provides contentColor) {
                            TextPreferenceWidget(
                                modifier = modifier,
                                title = stringResource(item.titleRes),
                                subtitle = item.formatSubtitle(),
                                icon = item.icon,
                                onPreferenceClick = { navigator.navigate(item.screen, twoPane) },
                            )
                        }
                    }
                }
            },
        )
    }

    private fun Navigator.navigate(screen: NavKey, twoPane: Boolean) {
        if (twoPane) popTo { it == this@PlayerSettingsMainScreen }
        push(screen)
    }

    private companion object {
        data class Item(
            val titleRes: StringResource,
            val subtitleRes: StringResource? = null,
            val formatSubtitle: @Composable () -> String? = { subtitleRes?.let { stringResource(it) } },
            val icon: ImageVector,
            val screen: NavKey,
        )

        val items = listOf(
            Item(
                titleRes = AYMR.strings.pref_player_internal,
                subtitleRes = AYMR.strings.pref_player_internal_summary,
                icon = Icons.Outlined.PlayCircleOutline,
                screen = PlayerSettingsPlayerScreen,
            ),
            Item(
                titleRes = AYMR.strings.pref_player_gestures,
                subtitleRes = AYMR.strings.pref_player_gestures_summary,
                icon = Icons.Outlined.Gesture,
                screen = PlayerSettingsGesturesScreen,
            ),
            Item(
                titleRes = AYMR.strings.pref_player_decoder,
                subtitleRes = AYMR.strings.pref_player_decoder_summary,
                icon = Icons.Outlined.Memory,
                screen = PlayerSettingsDecoderScreen,
            ),
            Item(
                titleRes = AYMR.strings.pref_player_subtitle,
                subtitleRes = AYMR.strings.pref_player_subtitle_summary,
                icon = Icons.Outlined.Subtitles,
                screen = PlayerSettingsSubtitleScreen,
            ),
            Item(
                titleRes = AYMR.strings.pref_player_audio,
                subtitleRes = AYMR.strings.pref_player_audio_summary,
                icon = Icons.Outlined.Audiotrack,
                screen = PlayerSettingsAudioScreen,
            ),
            Item(
                titleRes = AYMR.strings.pref_player_custom_button,
                subtitleRes = AYMR.strings.pref_player_custom_button_summary,
                icon = Icons.Outlined.Terminal,
                screen = PlayerSettingsCustomButtonRoute,
            ),
            Item(
                titleRes = AYMR.strings.pref_player_editor,
                subtitleRes = AYMR.strings.pref_player_editor_summary,
                icon = Icons.Outlined.EditNote,
                screen = PlayerSettingsEditorRoute,
            ),
            Item(
                titleRes = AYMR.strings.pref_player_torrents,
                subtitleRes = AYMR.strings.pref_player_torrents_summary,
                icon = CustomIcons.Magnet,
                screen = PlayerSettingsTorrentScreen,
            ),
            Item(
                titleRes = AYMR.strings.pref_player_advanced,
                subtitleRes = AYMR.strings.pref_player_advanced_summary,
                icon = Icons.Outlined.Code,
                screen = PlayerSettingsAdvancedScreen,
            ),
        )
    }
}

/**
 * Opens player settings at [screen], or at the main player settings screen if null. In two panes, reuses open
 * player settings as the list pane and shows internal player settings when no [screen] is given.
 */
fun Navigator.openPlayerSettings(mainSettings: Boolean, screen: NavKey? = null, twoPane: Boolean) {
    if (!twoPane) {
        push(screen ?: PlayerSettingsMainScreen(mainSettings))
        return
    }
    val target = PlayerSettingsMainScreen(mainSettings)
    if (!popTo { it == target }) push(target)
    push(screen ?: PlayerSettingsPlayerScreen)
}
