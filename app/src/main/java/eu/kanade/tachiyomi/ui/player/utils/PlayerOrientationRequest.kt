package eu.kanade.tachiyomi.ui.player.utils

import android.content.pm.ActivityInfo
import eu.kanade.tachiyomi.ui.player.PlayerOrientation

/**
 * The `requestedOrientation` for the player.
 *
 * @param videoAspect the video's display aspect ratio, used by [PlayerOrientation.Video].
 * @param keepScreenOrientation true on a near-square screen when the user lets the player skip
 *   forced rotation there: the screen stays as the user holds it and the video fits either way.
 *   [PlayerOrientation.Free] already follows the sensor and is left alone.
 */
fun PlayerOrientation.toRequestedOrientation(videoAspect: Double?, keepScreenOrientation: Boolean): Int {
    if (keepScreenOrientation && this != PlayerOrientation.Free) {
        return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    return when (this) {
        PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR

        PlayerOrientation.Video -> if ((videoAspect ?: 0.0) > 1.0) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT

        PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

        PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

        PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}
