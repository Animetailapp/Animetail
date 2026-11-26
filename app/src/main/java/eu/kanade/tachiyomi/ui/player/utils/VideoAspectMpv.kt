package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.ui.player.VideoAspect
import `is`.xyz.mpv.MPV

/**
 * Applies [aspect] to mpv's video output.
 *
 * @param surfaceAspect width / height of the surface mpv renders into. Only [VideoAspect.Stretch]
 *   depends on it, so it must be re-applied whenever the surface is resized (fold, rotation, tabletop).
 */
fun MPV.applyVideoAspect(aspect: VideoAspect, surfaceAspect: Double) {
    val (panscan, aspectOverride) = when (aspect) {
        VideoAspect.Crop -> 1.0 to -1.0
        VideoAspect.Fit -> 0.0 to -1.0
        VideoAspect.Stretch -> 0.0 to surfaceAspect
    }
    setPropertyDouble("panscan", panscan)
    setPropertyDouble("video-aspect-override", aspectOverride)
}
