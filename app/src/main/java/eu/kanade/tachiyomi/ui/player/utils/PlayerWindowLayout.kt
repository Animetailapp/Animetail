package eu.kanade.tachiyomi.ui.player.utils

import kotlin.math.max
import kotlin.math.min

/**
 * A fold reported by Jetpack WindowManager, reduced to what the player layout needs.
 *
 * [start] and [end] are the fold's bounds along the axis it splits, in pixels relative to the
 * player's root view: top/bottom for a horizontal fold, left/right for a vertical one.
 */
data class PlayerFold(
    val isHalfOpened: Boolean,
    val isHorizontal: Boolean,
    val start: Int,
    val end: Int,
)

/**
 * How the player arranges video and controls in its window.
 *
 * @param tabletop set when the device is half-folded with a horizontal hinge: the video goes above
 *   the hinge and the controls below it.
 * @param controlsMaxWidthDp width cap for the controls, or null to span the whole window.
 */
data class PlayerWindowLayout(
    val tabletop: Tabletop?,
    val controlsMaxWidthDp: Float?,
) {
    /** Pixel offsets from the top of the player's root view. */
    data class Tabletop(val videoHeightPx: Int, val controlsTopPx: Int)

    companion object {
        val Full = PlayerWindowLayout(tabletop = null, controlsMaxWidthDp = null)

        /** Each half of a tabletop split must be at least this tall to be usable. */
        const val MIN_TABLETOP_PANE_DP = 160f

        /** Windows wider than this (21:9 and beyond) cap the controls near the fitted video's width. */
        const val WIDE_WINDOW_ASPECT = 2.4f

        /** Absolute cap so a seek bar never spans an entire tablet or unfolded screen. */
        const val MAX_CONTROLS_WIDTH_DP = 1200f

        /** Narrowest the controls get from the wide-window cap, so all buttons still fit. */
        const val MIN_CONTROLS_WIDTH_DP = 600f

        private const val VIDEO_ASPECT = 16f / 9f

        fun compute(
            widthPx: Int,
            heightPx: Int,
            density: Float,
            folds: List<PlayerFold>,
            isInPictureInPicture: Boolean,
        ): PlayerWindowLayout {
            if (isInPictureInPicture || widthPx <= 0 || heightPx <= 0 || density <= 0f) return Full

            val tabletop = folds.firstNotNullOfOrNull { tabletopFor(it, heightPx, density) }
            val controlsHeightPx = if (tabletop != null) heightPx - tabletop.controlsTopPx else heightPx
            return PlayerWindowLayout(
                tabletop = tabletop,
                controlsMaxWidthDp = controlsMaxWidthDp(
                    widthDp = widthPx / density,
                    heightDp = controlsHeightPx / density,
                    // In tabletop the controls pane is short by design; only the absolute cap applies there.
                    capToVideoWidth = tabletop == null,
                ),
            )
        }

        private fun tabletopFor(fold: PlayerFold, heightPx: Int, density: Float): Tabletop? {
            if (!fold.isHalfOpened || !fold.isHorizontal) return null
            val minPanePx = MIN_TABLETOP_PANE_DP * density
            if (fold.start < minPanePx || heightPx - fold.end < minPanePx) return null
            return Tabletop(videoHeightPx = fold.start, controlsTopPx = fold.end)
        }

        private fun controlsMaxWidthDp(widthDp: Float, heightDp: Float, capToVideoWidth: Boolean): Float? {
            var cap = MAX_CONTROLS_WIDTH_DP
            if (capToVideoWidth && widthDp / heightDp > WIDE_WINDOW_ASPECT) {
                cap = min(cap, max(MIN_CONTROLS_WIDTH_DP, heightDp * VIDEO_ASPECT))
            }
            return cap.takeIf { it < widthDp }
        }
    }
}

/** Screens closer to square than this gain nothing from rotating the player. */
const val NEAR_SQUARE_MAX_ASPECT = 1.3f

/** Whether a screen of this size is near-square, in either orientation. */
fun isNearSquare(widthPx: Int, heightPx: Int): Boolean {
    if (widthPx <= 0 || heightPx <= 0) return false
    return max(widthPx, heightPx).toFloat() / min(widthPx, heightPx) < NEAR_SQUARE_MAX_ASPECT
}
