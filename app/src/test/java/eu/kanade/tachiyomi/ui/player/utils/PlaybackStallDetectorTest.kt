package eu.kanade.tachiyomi.ui.player.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class PlaybackStallDetectorTest {

    /** One second of player state. */
    sealed interface Step {
        data class Second(val pausedByUser: Boolean = false) : Step
        data class Restart(val position: Float) : Step
        data class Position(val position: Float) : Step
    }

    data class Case(
        val description: String,
        val steps: List<Step>,
        /** The 1-based second at which the stall fires, or null if it never does. */
        val expectedStallAt: Int?,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `stall detection`(case: Case) {
        val detector = PlaybackStallDetector(timeoutSeconds = 3)
        detector.start()

        var second = 0
        var stalledAt: Int? = null
        case.steps.forEach { step ->
            when (step) {
                is Step.Second -> {
                    second++
                    if (detector.tick(step.pausedByUser)) {
                        assertEquals(null, stalledAt, "fires only once: ${case.description}")
                        stalledAt = second
                    }
                }

                is Step.Restart -> detector.onPlaybackRestart(step.position)

                is Step.Position -> detector.onPosition(step.position)
            }
        }

        assertEquals(case.expectedStallAt, stalledAt, case.description)
    }

    @Test
    fun `a new video starts a fresh count`() {
        val detector = PlaybackStallDetector(timeoutSeconds = 2)
        detector.start()
        assertFalse(detector.tick(pausedByUser = false))
        detector.start()
        assertFalse(detector.tick(pausedByUser = false))
        assertTrue(detector.tick(pausedByUser = false))
    }

    companion object {
        private val second = Step.Second()

        @JvmStatic
        fun cases() = listOf(
            Case(
                "File never loads: stalls after the timeout",
                listOf(second, second, second, second, second),
                expectedStallAt = 3,
            ),
            Case(
                "Loaded but sits at 00:00 (dead segments): still a stall",
                listOf(Step.Restart(0f), second, Step.Position(0f), second, second),
                expectedStallAt = 3,
            ),
            Case(
                "Plays from the resume position: never stalls",
                listOf(Step.Restart(600f), second, Step.Position(601f), second, second, second, second),
                expectedStallAt = null,
            ),
            Case(
                "Positions before mpv is ready (the previous file's) don't count as playing",
                listOf(Step.Position(1400f), second, second, second),
                expectedStallAt = 3,
            ),
            Case(
                "User pause after loading doesn't count towards the timeout",
                listOf(
                    Step.Restart(0f),
                    second,
                    Step.Second(pausedByUser = true),
                    Step.Second(pausedByUser = true),
                    Step.Second(pausedByUser = true),
                    second,
                ),
                expectedStallAt = null,
            ),
        )
    }
}
