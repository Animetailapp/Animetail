package eu.kanade.tachiyomi.ui.player.controls.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * The speed slider has to land on the 0.25x stops it shows, whatever position the finger leaves
 * it at, and never outside 0.25x to 4x.
 */
class PlaybackSpeedDropdownTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "1.0, 1.0",
        "1.1, 1.0",
        "1.13, 1.25",
        "1.37, 1.25",
        "2.99, 3.0",
        "3.875, 4.0",
        "0.0, 0.25",
        "0.1, 0.25",
        "5.0, 4.0",
    )
    fun `snaps to the nearest stop within the range`(raw: Float, expected: Float) {
        assertEquals(expected, raw.snapToSpeedStep())
    }

    @Test
    fun `the slider has a stop for every step`() {
        // 0.25, 0.5, ..., 4.0 is 16 stops: the two ends plus SLIDER_STEPS in between.
        assertEquals(14, SLIDER_STEPS)
        val stops = (0..SLIDER_STEPS + 1).map { MIN_SPEED + it * SPEED_STEP }
        assertEquals(MAX_SPEED, stops.last())
        assertEquals(stops, stops.map { it.snapToSpeedStep() })
    }
}
