package eu.kanade.tachiyomi.ui.player.utils

import android.content.pm.ActivityInfo
import eu.kanade.tachiyomi.ui.player.PlayerOrientation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class PlayerWindowLayoutTest {

    data class LayoutCase(
        val description: String,
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
        val folds: List<PlayerFold> = emptyList(),
        val isInPictureInPicture: Boolean = false,
        val expected: PlayerWindowLayout,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("layoutCases")
    fun `window layout`(case: LayoutCase) {
        val layout = PlayerWindowLayout.compute(
            widthPx = case.widthPx,
            heightPx = case.heightPx,
            density = case.density,
            folds = case.folds,
            isInPictureInPicture = case.isInPictureInPicture,
        )
        assertEquals(case.expected, layout)
    }

    data class NearSquareCase(val description: String, val widthPx: Int, val heightPx: Int, val expected: Boolean) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("nearSquareCases")
    fun `near-square screens`(case: NearSquareCase) {
        assertEquals(case.expected, isNearSquare(case.widthPx, case.heightPx))
    }

    data class OrientationCase(
        val description: String,
        val preference: PlayerOrientation,
        val keepScreenOrientation: Boolean,
        val videoAspect: Double? = 16.0 / 9.0,
        val expected: Int,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("orientationCases")
    fun `requested orientation`(case: OrientationCase) {
        assertEquals(
            case.expected,
            case.preference.toRequestedOrientation(case.videoAspect, case.keepScreenOrientation),
        )
    }

    companion object {
        // Pixel Fold-like inner screen held in tabletop: 1840x2208 px at 2.625 density, hinge at y = 1104.
        private val tabletopHinge = PlayerFold(isHalfOpened = true, isHorizontal = true, start = 1104, end = 1104)

        @JvmStatic
        fun layoutCases() = listOf(
            LayoutCase(
                description = "a 20:9 phone in landscape keeps the full-width controls it always had",
                widthPx = 2400,
                heightPx = 1080,
                density = 2.75f,
                expected = PlayerWindowLayout.Full,
            ),
            LayoutCase(
                description = "a 32:9 window caps the controls near the width of the fitted 16:9 video",
                widthPx = 3840,
                heightPx = 1080,
                density = 2f,
                // 540dp tall -> 960dp wide video, under the 1920dp window
                expected = PlayerWindowLayout(tabletop = null, controlsMaxWidthDp = 960f),
            ),
            LayoutCase(
                description = "a short ultra-wide window never caps the controls below the width their buttons need",
                widthPx = 2400,
                heightPx = 600,
                density = 2f,
                expected = PlayerWindowLayout(tabletop = null, controlsMaxWidthDp = 600f),
            ),
            LayoutCase(
                description = "an unfolded screen at low density caps the seek bar at the absolute maximum",
                widthPx = 3184,
                heightPx = 2232,
                density = 2f,
                expected = PlayerWindowLayout(tabletop = null, controlsMaxWidthDp = 1200f),
            ),
            LayoutCase(
                description = "a flat fold (book or fully open) is not tabletop",
                widthPx = 1840,
                heightPx = 2208,
                density = 2.625f,
                folds = listOf(tabletopHinge.copy(isHalfOpened = false)),
                expected = PlayerWindowLayout.Full,
            ),
            LayoutCase(
                description = "a half-opened vertical hinge is book posture, not tabletop",
                widthPx = 2208,
                heightPx = 1840,
                density = 2.625f,
                folds = listOf(PlayerFold(isHalfOpened = true, isHorizontal = false, start = 1104, end = 1104)),
                expected = PlayerWindowLayout.Full,
            ),
            LayoutCase(
                description = "a half-opened horizontal hinge puts the video above and the controls below it",
                widthPx = 1840,
                heightPx = 2208,
                density = 2.625f,
                folds = listOf(tabletopHinge),
                expected = PlayerWindowLayout(
                    tabletop = PlayerWindowLayout.Tabletop(videoHeightPx = 1104, controlsTopPx = 1104),
                    controlsMaxWidthDp = null,
                ),
            ),
            LayoutCase(
                description = "an occluding hinge keeps both video and controls off the hinge itself",
                widthPx = 1840,
                heightPx = 2208,
                density = 2.625f,
                folds = listOf(tabletopHinge.copy(start = 1080, end = 1130)),
                expected = PlayerWindowLayout(
                    tabletop = PlayerWindowLayout.Tabletop(videoHeightPx = 1080, controlsTopPx = 1130),
                    controlsMaxWidthDp = null,
                ),
            ),
            LayoutCase(
                description = "a hinge near the window edge (e.g. a window spanning onto the other panel) is ignored",
                widthPx = 1840,
                heightPx = 2208,
                density = 2.625f,
                folds = listOf(tabletopHinge.copy(start = 200, end = 200)),
                expected = PlayerWindowLayout.Full,
            ),
            LayoutCase(
                description = "picture-in-picture always shows the plain video, even half-folded",
                widthPx = 1840,
                heightPx = 2208,
                density = 2.625f,
                folds = listOf(tabletopHinge),
                isInPictureInPicture = true,
                expected = PlayerWindowLayout.Full,
            ),
            LayoutCase(
                description = "a window that is not laid out yet gets the plain layout",
                widthPx = 0,
                heightPx = 0,
                density = 2.625f,
                folds = listOf(tabletopHinge),
                expected = PlayerWindowLayout.Full,
            ),
        )

        @JvmStatic
        fun nearSquareCases() = listOf(
            NearSquareCase("Mate XT dual-panel (2048x2232) is near-square", 2048, 2232, true),
            NearSquareCase("Pixel Fold inner screen (2208x1840) is near-square", 2208, 1840, true),
            NearSquareCase("Mate XT fully unfolded (3184x2232, 1.43) still benefits from rotation", 3184, 2232, false),
            NearSquareCase("a phone screen in portrait is not near-square", 1080, 2400, false),
            NearSquareCase("an unknown size is not near-square", 0, 2232, false),
        )

        @JvmStatic
        fun orientationCases() = listOf(
            OrientationCase(
                description = "the default sensor-landscape still forces rotation on a normal screen",
                preference = PlayerOrientation.SensorLandscape,
                keepScreenOrientation = false,
                expected = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ),
            OrientationCase(
                description = "a near-square screen keeps whatever orientation it is held in",
                preference = PlayerOrientation.SensorLandscape,
                keepScreenOrientation = true,
                expected = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            ),
            OrientationCase(
                description = "free rotation keeps following the sensor on a near-square screen",
                preference = PlayerOrientation.Free,
                keepScreenOrientation = true,
                expected = ActivityInfo.SCREEN_ORIENTATION_SENSOR,
            ),
            OrientationCase(
                description = "reverse landscape is landscape, not reverse portrait",
                preference = PlayerOrientation.ReverseLandscape,
                keepScreenOrientation = false,
                expected = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ),
            OrientationCase(
                description = "video orientation follows a portrait video",
                preference = PlayerOrientation.Video,
                keepScreenOrientation = false,
                videoAspect = 9.0 / 16.0,
                expected = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            ),
            OrientationCase(
                description = "video orientation before the video is loaded picks portrait",
                preference = PlayerOrientation.Video,
                keepScreenOrientation = false,
                videoAspect = null,
                expected = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            ),
        )
    }
}
