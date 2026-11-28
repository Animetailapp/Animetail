package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.utils.ChapterSkipPlanner.Action
import eu.kanade.tachiyomi.ui.player.utils.ChapterSkipPlanner.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

class ChapterSkipPlannerTest {

    sealed interface Step {
        data class At(val position: Float, val episode: Long = 1) : Step
        data class Press(val position: Float) : Step
    }

    data class Case(
        val description: String,
        val chapters: List<Chapter> = EPISODE,
        val opening: ChapterSkipMode = ChapterSkipMode.Off,
        val ending: ChapterSkipMode = ChapterSkipMode.Off,
        val hasNextEpisode: Boolean = true,
        val steps: List<Step>,
        /** The action of each step, in order. */
        val expected: List<Action>,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `skip decisions`(case: Case) {
        val planner = ChapterSkipPlanner()
        val settings = ChapterSkipPlanner.Settings(
            openingMode = case.opening,
            endingMode = case.ending,
            countdownSeconds = 3,
            hasNextEpisode = case.hasNextEpisode,
        )
        val actions = case.steps.map { step ->
            when (step) {
                is Step.At -> planner.onPosition(step.episode, step.position, DURATION, case.chapters, settings)
                is Step.Press -> planner.onButtonPressed(step.position, DURATION, case.chapters)
            }
        }
        assertEquals(case.expected, actions, case.description)
    }

    @ParameterizedTest
    @MethodSource("legacyCases")
    fun `unset modes keep what the old switches did`(case: LegacyCase) {
        val store = InMemoryPreferenceStore(
            case.switches.map { (key, value) -> InMemoryPreference(key, value, false) }.asSequence(),
        )
        val preferences = PlayerPreferences(store)

        assertEquals(case.expected, preferences.openingSkipMode().get(), case.toString())
        assertEquals(case.expected, preferences.endingSkipMode().get(), case.toString())
    }

    data class LegacyCase(val switches: Map<String, Boolean>, val expected: ChapterSkipMode)

    companion object {
        private const val DURATION = 1440f

        private val PROLOGUE = Chapter(0f, ChapterType.Other, "")
        private val OPENING = Chapter(90f, ChapterType.Opening, "Opening")
        private val PART_A = Chapter(180f, ChapterType.Other, "")
        private val ENDING = Chapter(1350f, ChapterType.Ending, "Ending")
        private val PREVIEW = Chapter(1410f, ChapterType.Other, "Preview")
        private val RECAP = Chapter(0f, ChapterType.Recap, "Recap")

        /** Prologue, opening, episode, ending running to the end of the file. */
        private val EPISODE = listOf(PROLOGUE, OPENING, PART_A, ENDING)

        @JvmStatic
        fun legacyCases() = listOf(
            LegacyCase(emptyMap(), ChapterSkipMode.Button),
            LegacyCase(mapOf("pref_enable_skip_intro" to false), ChapterSkipMode.Off),
            LegacyCase(mapOf("pref_enable_auto_skip_ani_skip" to true), ChapterSkipMode.AutoSkip),
            // The countdown took precedence over auto skip
            LegacyCase(
                mapOf("pref_enable_auto_skip_ani_skip" to true, "pref_enable_netflixStyle_aniskip" to true),
                ChapterSkipMode.Countdown,
            ),
            LegacyCase(
                mapOf("pref_enable_skip_intro" to false, "pref_enable_auto_skip_ani_skip" to true),
                ChapterSkipMode.Off,
            ),
        )

        @JvmStatic
        fun cases() = listOf(
            Case(
                description = "auto skip jumps to the start of the next chapter",
                opening = ChapterSkipMode.AutoSkip,
                steps = listOf(Step.At(10f), Step.At(90f)),
                expected = listOf(Action.None, Action.SkipTo(OPENING, 180f)),
            ),
            Case(
                description = "opening off leaves it alone",
                ending = ChapterSkipMode.AutoSkip,
                steps = listOf(Step.At(91f)),
                expected = listOf(Action.None),
            ),
            Case(
                description = "button mode only shows the button, pressing it skips",
                opening = ChapterSkipMode.Button,
                steps = listOf(Step.At(91f), Step.Press(92f)),
                expected = listOf(Action.ShowSkipButton(OPENING), Action.SkipTo(OPENING, 180f)),
            ),
            Case(
                description = "an ending that is the last chapter skips to the end of the video, not to the " +
                    "current position",
                ending = ChapterSkipMode.AutoSkip,
                steps = listOf(Step.At(1351f)),
                expected = listOf(Action.EndOfEpisode(ENDING)),
            ),
            Case(
                description = "pressing skip on a last-chapter ending also ends the episode",
                ending = ChapterSkipMode.Button,
                steps = listOf(Step.At(1351f), Step.Press(1352f)),
                expected = listOf(Action.ShowSkipButton(ENDING), Action.EndOfEpisode(ENDING)),
            ),
            Case(
                description = "an ending followed by a preview skips to the preview",
                chapters = EPISODE + PREVIEW,
                ending = ChapterSkipMode.AutoSkip,
                steps = listOf(Step.At(1351f)),
                expected = listOf(Action.SkipTo(ENDING, 1410f)),
            ),
            Case(
                description = "seeking back into a skipped opening does not skip it again",
                opening = ChapterSkipMode.AutoSkip,
                steps = listOf(Step.At(90f), Step.At(180f), Step.At(100f), Step.At(101f)),
                expected = listOf(
                    Action.SkipTo(OPENING, 180f),
                    Action.None,
                    Action.ShowSkipButton(OPENING),
                    Action.ShowSkipButton(OPENING),
                ),
            ),
            Case(
                description = "seeking back into an opening not yet reached means the user wants to watch it",
                opening = ChapterSkipMode.AutoSkip,
                steps = listOf(Step.At(300f), Step.At(120f)),
                expected = listOf(Action.None, Action.ShowSkipButton(OPENING)),
            ),
            Case(
                description = "seeking forward into an opening skips it",
                opening = ChapterSkipMode.AutoSkip,
                steps = listOf(Step.At(10f), Step.At(120f)),
                expected = listOf(Action.None, Action.SkipTo(OPENING, 180f)),
            ),
            Case(
                description = "the next episode starts with a fresh skip state",
                opening = ChapterSkipMode.AutoSkip,
                steps = listOf(Step.At(90f), Step.At(90f, episode = 2)),
                expected = listOf(Action.SkipTo(OPENING, 180f), Action.SkipTo(OPENING, 180f)),
            ),
            Case(
                description = "countdown skips after the waiting time of playback",
                opening = ChapterSkipMode.Countdown,
                steps = listOf(Step.At(90f), Step.At(91f), Step.At(92f), Step.At(93f)),
                expected = listOf(
                    Action.CountingDown(OPENING, 3, started = true, playNext = false),
                    Action.CountingDown(OPENING, 2, started = false, playNext = false),
                    Action.CountingDown(OPENING, 1, started = false, playNext = false),
                    Action.SkipTo(OPENING, 180f),
                ),
            ),
            Case(
                description = "cancelling a countdown keeps playing with a skip button, for the rest of the " +
                    "episode",
                opening = ChapterSkipMode.Countdown,
                steps = listOf(Step.At(90f), Step.Press(91f), Step.At(94f), Step.Press(95f)),
                expected = listOf(
                    Action.CountingDown(OPENING, 3, started = true, playNext = false),
                    Action.ShowSkipButton(OPENING),
                    Action.ShowSkipButton(OPENING),
                    Action.SkipTo(OPENING, 180f),
                ),
            ),
            Case(
                description = "play next counts down on entering the ending, then starts the next episode once",
                ending = ChapterSkipMode.PlayNext,
                steps = listOf(Step.At(1350f), Step.At(1352f), Step.At(1353f), Step.At(1354f)),
                expected = listOf(
                    Action.CountingDown(ENDING, 3, started = true, playNext = true),
                    Action.CountingDown(ENDING, 1, started = false, playNext = true),
                    Action.PlayNext(ENDING),
                    Action.ShowSkipButton(ENDING),
                ),
            ),
            Case(
                description = "play next cancelled lets the ending play",
                ending = ChapterSkipMode.PlayNext,
                steps = listOf(Step.At(1350f), Step.Press(1351f), Step.At(1360f)),
                expected = listOf(
                    Action.CountingDown(ENDING, 3, started = true, playNext = true),
                    Action.ShowSkipButton(ENDING),
                    Action.ShowSkipButton(ENDING),
                ),
            ),
            Case(
                description = "play next on the last episode only offers the skip button",
                ending = ChapterSkipMode.PlayNext,
                hasNextEpisode = false,
                steps = listOf(Step.At(1350f)),
                expected = listOf(Action.ShowSkipButton(ENDING)),
            ),
            Case(
                description = "a countdown whose chapter ends first is dropped",
                chapters = listOf(PROLOGUE, OPENING, Chapter(92f, ChapterType.Other, "")),
                opening = ChapterSkipMode.Countdown,
                steps = listOf(Step.At(90f), Step.At(92f), Step.At(95f)),
                expected = listOf(
                    Action.CountingDown(OPENING, 3, started = true, playNext = false),
                    Action.None,
                    Action.None,
                ),
            ),
            Case(
                description = "recaps follow the opening mode, endings do not",
                chapters = listOf(RECAP, OPENING, PART_A, ENDING),
                opening = ChapterSkipMode.AutoSkip,
                ending = ChapterSkipMode.Button,
                steps = listOf(Step.At(0f), Step.At(1351f)),
                expected = listOf(Action.SkipTo(RECAP, 90f), Action.ShowSkipButton(ENDING)),
            ),
            Case(
                description = "untyped chapters have no button and are never skipped",
                opening = ChapterSkipMode.AutoSkip,
                ending = ChapterSkipMode.AutoSkip,
                steps = listOf(Step.At(200f), Step.Press(201f)),
                expected = listOf(Action.None, Action.None),
            ),
        )
    }
}
