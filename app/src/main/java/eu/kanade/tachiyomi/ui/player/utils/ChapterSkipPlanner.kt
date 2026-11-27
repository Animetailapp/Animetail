package eu.kanade.tachiyomi.ui.player.utils

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.animesource.model.ChapterType
import tachiyomi.i18n.tail.TLMR
import kotlin.math.floor

/** What the player does when playback enters an opening or an ending chapter. */
enum class ChapterSkipMode(val titleRes: StringResource) {
    Off(TLMR.strings.pref_chapter_skip_off),
    Button(TLMR.strings.pref_chapter_skip_button),
    Countdown(TLMR.strings.pref_chapter_skip_countdown),
    AutoSkip(TLMR.strings.pref_chapter_skip_auto),

    /** Endings only: start the next episode after a countdown that can be cancelled. */
    PlayNext(TLMR.strings.pref_chapter_skip_play_next),
    ;

    companion object {
        val OpeningModes = listOf(Off, Button, Countdown, AutoSkip)
        val EndingModes = listOf(Off, Button, Countdown, AutoSkip, PlayNext)
    }
}

/**
 * Decides what to do with the opening and ending chapters of an episode, from the merged chapter
 * list only (extension timestamps, AniSkip or anything else that types chapters).
 *
 * Openings, mixed openings and recaps follow the opening mode; endings follow the ending mode.
 * Other chapters are never skipped.
 *
 * Each chapter is acted on automatically at most once per episode: after it was skipped, its
 * countdown was cancelled, or the user seeked backwards into it, re-entering it only shows the
 * skip button.
 *
 * Driven by the player: [onPosition] on every position update, [onButtonPressed] when the skip
 * button is pressed.
 */
class ChapterSkipPlanner {

    data class Chapter(val start: Float, val type: ChapterType, val name: String)

    data class Settings(
        val openingMode: ChapterSkipMode,
        val endingMode: ChapterSkipMode,
        val countdownSeconds: Int,
        val hasNextEpisode: Boolean,
    )

    sealed interface Action {
        /** Not in a skippable chapter: hide the skip button. */
        data object None : Action

        data class ShowSkipButton(val chapter: Chapter) : Action

        /**
         * Counting down to skipping [chapter] or, with [playNext], to the next episode. [started] is
         * true on the first update of the countdown.
         */
        data class CountingDown(
            val chapter: Chapter,
            val secondsLeft: Int,
            val started: Boolean,
            val playNext: Boolean,
        ) : Action

        data class SkipTo(val chapter: Chapter, val position: Float) : Action

        /** Skipping [chapter] reaches the end of the video. */
        data class EndOfEpisode(val chapter: Chapter) : Action

        data class PlayNext(val chapter: Chapter) : Action
    }

    private class Countdown(val chapterStart: Float, val startPosition: Float)

    private var episodeKey: Any? = null
    private var lastPosition: Float? = null
    private val handledChapterStarts = mutableSetOf<Float>()
    private var countdown: Countdown? = null

    fun onPosition(
        episodeKey: Any?,
        position: Float,
        duration: Float,
        chapters: List<Chapter>,
        settings: Settings,
    ): Action {
        if (episodeKey != this.episodeKey) {
            this.episodeKey = episodeKey
            lastPosition = null
            handledChapterStarts.clear()
            countdown = null
        }
        val seekedBackwards = lastPosition?.let { position < it - BACKWARD_SEEK_TOLERANCE } == true
        lastPosition = position

        val (chapter, end) = chapterAt(position, duration, chapters)
            ?: return none()
        val mode = modeFor(chapter, settings)
        if (mode == null || mode == ChapterSkipMode.Off) return none()

        if (seekedBackwards) handledChapterStarts += chapter.start
        if (chapter.start in handledChapterStarts) {
            countdown = null
            return Action.ShowSkipButton(chapter)
        }

        return when (mode) {
            ChapterSkipMode.Off, ChapterSkipMode.Button -> {
                countdown = null
                Action.ShowSkipButton(chapter)
            }

            ChapterSkipMode.AutoSkip -> {
                countdown = null
                handledChapterStarts += chapter.start
                skip(chapter, end, duration)
            }

            ChapterSkipMode.Countdown, ChapterSkipMode.PlayNext -> {
                val current = countdown?.takeIf { it.chapterStart == chapter.start }
                val active = current ?: Countdown(chapter.start, position).also { countdown = it }
                val elapsed = floor(position - active.startPosition).toInt().coerceAtLeast(0)
                val secondsLeft = settings.countdownSeconds - elapsed
                val playNext = mode == ChapterSkipMode.PlayNext
                if (secondsLeft <= 0) {
                    countdown = null
                    handledChapterStarts += chapter.start
                    if (playNext) Action.PlayNext(chapter) else skip(chapter, end, duration)
                } else {
                    Action.CountingDown(chapter, secondsLeft, started = current == null, playNext = playNext)
                }
            }
        }
    }

    /** Cancels a running countdown, or skips the current chapter. */
    fun onButtonPressed(position: Float, duration: Float, chapters: List<Chapter>): Action {
        val (chapter, end) = chapterAt(position, duration, chapters)
            ?: return none()
        if (chapter.type == ChapterType.Other) return none()
        handledChapterStarts += chapter.start
        if (countdown?.chapterStart == chapter.start) {
            countdown = null
            return Action.ShowSkipButton(chapter)
        }
        return skip(chapter, end, duration)
    }

    private fun none(): Action {
        countdown = null
        return Action.None
    }

    private fun modeFor(chapter: Chapter, settings: Settings): ChapterSkipMode? = when (chapter.type) {
        ChapterType.Opening, ChapterType.MixedOp, ChapterType.Recap ->
            settings.openingMode.takeUnless { it == ChapterSkipMode.PlayNext } ?: ChapterSkipMode.Button

        ChapterType.Ending ->
            if (settings.endingMode == ChapterSkipMode.PlayNext && !settings.hasNextEpisode) {
                ChapterSkipMode.Button
            } else {
                settings.endingMode
            }

        ChapterType.Other -> null
    }

    private fun skip(chapter: Chapter, end: Float?, duration: Float): Action = when {
        end == null -> Action.ShowSkipButton(chapter)
        duration > 0f && end >= duration - END_TOLERANCE -> Action.EndOfEpisode(chapter)
        else -> Action.SkipTo(chapter, end)
    }

    /** The chapter playing at [position] and where it ends, or null for unknown. */
    private fun chapterAt(position: Float, duration: Float, chapters: List<Chapter>): Pair<Chapter, Float?>? {
        val chapter = chapters.filter { it.start <= position }.maxByOrNull { it.start } ?: return null
        val end = chapters.filter { it.start > chapter.start }.minOfOrNull { it.start }
            ?: duration.takeIf { it > chapter.start }
        return chapter to end
    }

    companion object {
        /** Position updates arrive about once a second; a bigger jump backwards is a seek. */
        const val BACKWARD_SEEK_TOLERANCE = 1.5f

        /** A chapter ending this close to the end of the video runs to the end. */
        const val END_TOLERANCE = 1f
    }
}
