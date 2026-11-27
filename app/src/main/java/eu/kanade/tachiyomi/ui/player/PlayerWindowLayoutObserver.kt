package eu.kanade.tachiyomi.ui.player

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import eu.kanade.tachiyomi.ui.player.utils.PlayerFold
import eu.kanade.tachiyomi.ui.player.utils.PlayerWindowLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat

/**
 * Tracks the player window's size and folds, and keeps [layout] and the video view in sync with them.
 *
 * Folds come from Jetpack WindowManager. Devices or containers without a window extension (e.g. some
 * Android compatibility layers on foldables) report no folding features; the player then uses the full
 * window as on any other device.
 */
class PlayerWindowLayoutObserver(
    private val activity: ComponentActivity,
    private val root: View,
    private val videoView: View,
) {
    private val _layout = MutableStateFlow(PlayerWindowLayout.Full)
    val layout: StateFlow<PlayerWindowLayout> = _layout.asStateFlow()

    private var folds: List<PlayerFold> = emptyList()
    private val rootLocation = IntArray(2)

    fun start() {
        // In tabletop the pane below the hinge has no video; keep it black, not the app theme's background.
        root.setBackgroundColor(Color.BLACK)
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                // Re-laying out the video view from inside a layout pass would be dropped.
                root.post { update() }
            }
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect(::onWindowLayoutInfo)
            }
        }
    }

    private fun onWindowLayoutInfo(info: WindowLayoutInfo) {
        // Logged so the features a device reports (none at all on some foldables) show up in bug reports.
        logcat(LogPriority.INFO) { "Window layout info: ${info.displayFeatures}" }
        folds = info.toPlayerFolds()
        update()
    }

    /** Recomputes the layout, e.g. after entering or leaving picture-in-picture. */
    fun update() {
        val layout = PlayerWindowLayout.compute(
            widthPx = root.width,
            heightPx = root.height,
            density = root.resources.displayMetrics.density,
            folds = folds,
            isInPictureInPicture = activity.isInPictureInPictureMode,
        )
        _layout.value = layout
        applyToVideoView(layout.tabletop)
    }

    private fun WindowLayoutInfo.toPlayerFolds(): List<PlayerFold> {
        root.getLocationInWindow(rootLocation)
        val (rootLeft, rootTop) = rootLocation
        return displayFeatures.filterIsInstance<FoldingFeature>().map { fold ->
            val isHorizontal = fold.orientation == FoldingFeature.Orientation.HORIZONTAL
            PlayerFold(
                isHalfOpened = fold.state == FoldingFeature.State.HALF_OPENED,
                isHorizontal = isHorizontal,
                start = if (isHorizontal) fold.bounds.top - rootTop else fold.bounds.left - rootLeft,
                end = if (isHorizontal) fold.bounds.bottom - rootTop else fold.bounds.right - rootLeft,
            )
        }
    }

    /** In tabletop the video keeps to the top half; mpv follows the resized surface. */
    private fun applyToVideoView(tabletop: PlayerWindowLayout.Tabletop?) {
        val height = tabletop?.videoHeightPx ?: ViewGroup.LayoutParams.MATCH_PARENT
        val params = videoView.layoutParams
        if (params.height != height) {
            params.height = height
            videoView.layoutParams = params
        }
    }
}
