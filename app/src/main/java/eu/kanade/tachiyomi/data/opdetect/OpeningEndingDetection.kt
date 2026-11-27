package eu.kanade.tachiyomi.data.opdetect

import tachiyomi.domain.items.episode.segment.model.EpisodeSegment

/** The fingerprint of `[offsetSeconds, offsetSeconds + duration)` of an episode. */
class WindowFingerprint(val offsetSeconds: Double, val fingerprint: AudioFingerprint)

/** What is known about one downloaded episode's audio; windows are null if they couldn't be read. */
class EpisodeAudio(val durationSeconds: Double, val head: WindowFingerprint?, val tail: WindowFingerprint?)

data class DetectedSegment(val type: EpisodeSegment.Type, val startSeconds: Double, val endSeconds: Double)

/**
 * Finds openings in the first [WINDOW_SECONDS] and endings in the last [WINDOW_SECONDS] of each
 * episode of a show, as the audio there that other episodes share (see [SegmentConsensus]).
 */
object OpeningEndingDetection {
    /** How much of the start (openings) and end (endings) of each episode is analysed. */
    const val WINDOW_SECONDS = 360.0

    /** An opening this close to the start is taken to start at 0. */
    private const val SNAP_TO_START_SECONDS = 2.0

    /** An ending this close to the end is taken to last until the end. */
    private const val SNAP_TO_END_SECONDS = 2.0

    /** Seconds from the start of the episode to analyse for the opening. */
    fun headWindow(durationSeconds: Double): ClosedFloatingPointRange<Double> =
        0.0..minOf(WINDOW_SECONDS, durationSeconds / 2)

    /** Seconds from the start of the episode to analyse for the ending. */
    fun tailWindow(durationSeconds: Double): ClosedFloatingPointRange<Double> =
        (durationSeconds - minOf(WINDOW_SECONDS, durationSeconds / 2))..durationSeconds

    /** For each episode, in the given (episode list) order, the openings and endings found. */
    fun detect(
        episodes: List<EpisodeAudio>,
        consensus: SegmentConsensus = SegmentConsensus(),
    ): List<List<DetectedSegment>> {
        val openings = consensus.detect(episodes.map { it.head?.fingerprint })
        val endings = consensus.detect(episodes.map { it.tail?.fingerprint })
        return episodes.mapIndexed { i, episode ->
            listOfNotNull(
                openings[i]?.let { span ->
                    episode.segment(EpisodeSegment.Type.Opening, episode.head!!, span)
                },
                endings[i]?.let { span ->
                    episode.segment(EpisodeSegment.Type.Ending, episode.tail!!, span)
                },
            )
        }
    }

    private fun EpisodeAudio.segment(
        type: EpisodeSegment.Type,
        window: WindowFingerprint,
        span: FrameSpan,
    ): DetectedSegment {
        // A frame's hash stands for the middle of the frame, give or take half a hop.
        val halfHop = AudioFingerprinter.HOP_SECONDS / 2
        var start = window.offsetSeconds + span.start * AudioFingerprinter.HOP_SECONDS +
            AudioFingerprinter.FRAME_CENTER_SECONDS - halfHop
        var end = window.offsetSeconds + (span.end - 1) * AudioFingerprinter.HOP_SECONDS +
            AudioFingerprinter.FRAME_CENTER_SECONDS + halfHop
        start = start.coerceAtLeast(0.0)
        end = end.coerceAtMost(durationSeconds)
        if (start < SNAP_TO_START_SECONDS) start = 0.0
        if (durationSeconds - end < SNAP_TO_END_SECONDS) end = durationSeconds
        return DetectedSegment(type, start, end)
    }

    /**
     * FFmpeg arguments that decode `[startSeconds, startSeconds + durationSeconds)` of the first
     * audio track of [input] to raw mono 16-bit PCM at [AudioFingerprinter.SAMPLE_RATE] Hz in
     * [output], the input of [AudioFingerprinter.fingerprint].
     */
    fun decodeArguments(
        input: String,
        output: String,
        startSeconds: Double,
        durationSeconds: Double,
    ): Array<String> =
        arrayOf(
            "-hide_banner", "-nostdin", "-v", "error",
            "-ss", "%.3f".format(java.util.Locale.ROOT, startSeconds),
            "-t", "%.3f".format(java.util.Locale.ROOT, durationSeconds),
            "-i", input,
            "-map", "0:a:0", "-vn", "-sn", "-dn",
            "-ac", "1", "-ar", AudioFingerprinter.SAMPLE_RATE.toString(),
            "-f", "s16le", "-y", output,
        )
}
