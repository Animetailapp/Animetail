package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import eu.kanade.tachiyomi.ui.player.controls.LocalPlayerButtonsClickEvent
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

internal const val MIN_SPEED = 0.25f
internal const val MAX_SPEED = 4f
internal const val SPEED_STEP = 0.25f

// Number of stops strictly between MIN_SPEED and MAX_SPEED, as Slider's `steps` expects.
internal val SLIDER_STEPS = ((MAX_SPEED - MIN_SPEED) / SPEED_STEP).roundToInt() - 1

internal fun Float.snapToSpeedStep(): Float =
    ((this / SPEED_STEP).roundToInt() * SPEED_STEP).coerceIn(MIN_SPEED, MAX_SPEED)

/**
 * Places the popup above its anchor, left-aligned with it and kept on screen. The speed button sits
 * just above the seek bar, so opening downwards (as DropdownMenu does) would cover the seek bar and
 * clip at the bottom edge.
 */
private class AbovePositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/**
 * Quick speed picker anchored above the speed button: a slider that snaps to
 * [SPEED_STEP] increments between [MIN_SPEED] and [MAX_SPEED].
 */
@Composable
fun PlaybackSpeedDropdown(
    expanded: Boolean,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val keepControlsVisible = LocalPlayerButtonsClickEvent.current

    if (!expanded) return
    val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val positionProvider = remember(gapPx) { AbovePositionProvider(gapPx) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = modifier.width(320.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(AYMR.strings.player_speed, speed),
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = speed.coerceIn(MIN_SPEED, MAX_SPEED),
                    onValueChange = {
                        val snapped = it.snapToSpeedStep()
                        if (snapped != speed) {
                            onSpeedChange(snapped)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        keepControlsVisible()
                    },
                    valueRange = MIN_SPEED..MAX_SPEED,
                    steps = SLIDER_STEPS,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.player_speed, MIN_SPEED),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = stringResource(AYMR.strings.player_speed, MAX_SPEED),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
