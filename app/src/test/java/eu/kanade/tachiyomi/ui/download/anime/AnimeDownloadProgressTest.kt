package eu.kanade.tachiyomi.ui.download.anime

import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AnimeDownloadProgressTest {

    /** What the queue shows after [samples] were reported, at [nowMs]. */
    data class Case(
        val description: String,
        val samples: List<AnimeDownload.Transfer>,
        val nowMs: Long = samples.last().atMs,
        val fraction: Float?,
        val totalBytes: Long?,
        val totalBytesEstimated: Boolean = false,
        /** Expected smoothed speed, within 1%. */
        val bytesPerSecond: Double?,
        /** Expected time left, within 1 second. */
        val timeLeft: Duration?,
        val stalled: Boolean = false,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `speed, progress and time left follow the reports`(case: Case) {
        val estimate = case.samples.drop(1).fold(TransferEstimator.update(null, case.samples.first())) { estimate, s ->
            TransferEstimator.update(estimate, s)
        }
        val progress = TransferEstimator.progress(estimate, case.nowMs)

        assertEquals(case.fraction, progress.fraction, case.description)
        assertEquals(case.totalBytes, progress.totalBytes, case.description)
        assertEquals(case.totalBytesEstimated, progress.totalBytesEstimated, case.description)
        assertEquals(case.stalled, progress.stalled, case.description)
        if (case.bytesPerSecond == null) {
            assertEquals(null, progress.bytesPerSecond, case.description)
        } else {
            val tolerance = case.bytesPerSecond * 0.01 + 1e-9
            assertEquals(case.bytesPerSecond, progress.bytesPerSecond!!, tolerance, case.description)
        }
        if (case.timeLeft == null) {
            assertEquals(null, progress.timeLeft, case.description)
        } else {
            val actual = progress.timeLeft ?: error("no time left: ${case.description}")
            assertEquals(
                case.timeLeft.inWholeMilliseconds.toDouble(),
                actual.inWholeMilliseconds.toDouble(),
                1000.0,
                case.description,
            )
        }
    }

    data class FormatCase(val description: String, val actual: () -> String, val expected: String) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("formatCases")
    fun `sizes, speeds and times left read compactly`(case: FormatCase) {
        assertEquals(case.expected, case.actual(), case.description)
    }

    data class SummaryCase(
        val description: String,
        val states: List<AnimeDownloadItemState>,
        val downloaderRunning: Boolean = true,
        val expected: AnimeDownloadQueueSummary,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("summaryCases")
    fun `the header sums up the queue`(case: SummaryCase) {
        assertEquals(case.expected, AnimeDownloadQueueSummary.of(case.states, case.downloaderRunning), case.description)
    }

    data class StateCase(
        val description: String,
        val status: AnimeDownload.State,
        val active: Boolean = false,
        val downloaderRunning: Boolean = true,
        val expected: AnimeDownloadItemState,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("stateCases")
    fun `each row says what its download is doing`(case: StateCase) {
        val state = AnimeDownloadItemState.of(
            status = case.status,
            active = case.active,
            estimate = null,
            downloaderRunning = case.downloaderRunning,
            nowMs = 0,
        )
        assertEquals(case.expected, state, case.description)
    }

    companion object {
        private const val MB = 1_000_000L
        private val HOUR_MS = 1.hours.inWholeMilliseconds

        /** HLS through FFmpeg: no total size, a 24 minute duration, [mediaMs] of it written. */
        private fun hls(atMs: Long, bytes: Long, mediaMs: Long, durationMs: Long? = 24.minutes.inWholeMilliseconds) =
            AnimeDownload.Transfer(atMs, bytes, totalBytes = null, mediaMs = mediaMs, durationMs = durationMs)

        private fun direct(atMs: Long, bytes: Long, totalBytes: Long?) =
            AnimeDownload.Transfer(atMs, bytes, totalBytes = totalBytes, mediaMs = 0, durationMs = null)

        /** Every 500 ms (FFmpeg's statistics interval) for [count] reports at [bytesPerSecond]. */
        private fun steady(count: Int, bytesPerSecond: Long, startMs: Long = 0, startBytes: Long = 0) =
            (0 until count).map { direct(startMs + it * 500L, startBytes + it * bytesPerSecond / 2, 100 * MB) }

        @JvmStatic
        fun cases(): List<Case> {
            val steady = steady(count = 20, bytesPerSecond = MB)
            val spike = steady + direct(steady.last().atMs + 500, steady.last().bytes + 5 * MB, 100 * MB)
            return listOf(
                Case(
                    "a single report has no speed yet, so no time left, rather than a guess",
                    samples = listOf(direct(0, 10 * MB, 100 * MB)),
                    fraction = 0.1f,
                    totalBytes = 100 * MB,
                    bytesPerSecond = null,
                    timeLeft = null,
                ),
                Case(
                    "known size: time left is the remaining bytes at the measured speed",
                    samples = steady,
                    fraction = 0.095f,
                    totalBytes = 100 * MB,
                    bytesPerSecond = MB.toDouble(),
                    timeLeft = 90.5.seconds,
                ),
                Case(
                    "a one-report burst at 10x the speed moves the smoothed speed about 12% of the way to it, " +
                        "not all the way",
                    samples = spike,
                    fraction = 0.145f,
                    totalBytes = 100 * MB,
                    bytesPerSecond = MB + 9 * MB * 0.1175,
                    timeLeft = (85.5 / 2.0575).seconds,
                ),
                Case(
                    "unknown total and unknown duration: no fraction (indeterminate bar) and no time left, " +
                        "but the speed still shows",
                    samples = listOf(hls(0, 0, 0, durationMs = null), hls(1000, 2 * MB, 10_000, durationMs = null)),
                    fraction = null,
                    totalBytes = null,
                    bytesPerSecond = 2.0 * MB,
                    timeLeft = null,
                ),
                Case(
                    "HLS: progress and time left come from media time against the duration, " +
                        "and the size is extrapolated from it",
                    samples = (0..10).map { hls(it * 1000L, it * 2 * MB, it * 18_000L) },
                    // 180 s of 1440 s written at 18x real time: 1260 s left takes 70 s
                    fraction = 0.125f,
                    totalBytes = 160 * MB,
                    totalBytesEstimated = true,
                    bytesPerSecond = 2.0 * MB,
                    timeLeft = 70.seconds,
                ),
                Case(
                    "HLS at its very start: the extrapolated size would be wild, so it is left out",
                    samples = listOf(hls(0, 0, 0), hls(1000, MB, 10_000)),
                    fraction = (10_000.0 / 1_440_000).toFloat(),
                    totalBytes = null,
                    bytesPerSecond = MB.toDouble(),
                    timeLeft = 143.seconds,
                ),
                Case(
                    "zero speed (reports keep coming but nothing is written): no time left, not infinity",
                    samples = listOf(direct(0, 10 * MB, 100 * MB), direct(1000, 10 * MB, 100 * MB)),
                    fraction = 0.1f,
                    totalBytes = 100 * MB,
                    bytesPerSecond = 0.0,
                    timeLeft = null,
                ),
                Case(
                    "no report for a while counts as stalled: zero speed and no time left, even though the last " +
                        "measured speed was fine",
                    samples = steady,
                    nowMs = steady.last().atMs + TransferEstimator.STALLED_AFTER_MS,
                    fraction = 0.095f,
                    totalBytes = 100 * MB,
                    bytesPerSecond = 0.0,
                    timeLeft = null,
                    stalled = true,
                ),
                Case(
                    "counters going back mean a new attempt (another server): its speed starts over instead of " +
                        "going negative",
                    samples = steady + direct(steady.last().atMs + 500, MB, 100 * MB),
                    fraction = 0.01f,
                    totalBytes = 100 * MB,
                    bytesPerSecond = null,
                    timeLeft = null,
                ),
                Case(
                    "a speed so low the time left would be days is shown without a time left",
                    samples = listOf(hls(0, 0, 0, HOUR_MS * 10), hls(10_000, 1000, 1, HOUR_MS * 10)),
                    fraction = (1.0 / (HOUR_MS * 10)).toFloat(),
                    totalBytes = null,
                    bytesPerSecond = 100.0,
                    timeLeft = null,
                ),
            )
        }

        @JvmStatic
        fun formatCases() = listOf(
            FormatCase("bytes below a kilobyte", { DownloadFormat.bytes(999, Locale.US) }, "999 B"),
            FormatCase(
                "999.6 KB rounds into the next unit instead of reading 1000 KB",
                { DownloadFormat.bytes(999_600, Locale.US) },
                "1.00 MB",
            ),
            FormatCase("three significant digits", { DownloadFormat.bytes(45_670_000, Locale.US) }, "45.7 MB"),
            FormatCase("large sizes", { DownloadFormat.bytes(1_234_000_000, Locale.US) }, "1.23 GB"),
            FormatCase("speed", { DownloadFormat.speed(2_400_000.0, Locale.US) }, "2.40 MB/s"),
            FormatCase("under a minute: seconds, rounded up", { DownloadFormat.timeLeft(44.2.seconds) }, "45s"),
            FormatCase(
                "59.5 s rounds up to a minute, not to 0s",
                { DownloadFormat.timeLeft(59_500.milliseconds) },
                "1m 00s",
            ),
            FormatCase("minutes with seconds while short", { DownloadFormat.timeLeft(185.seconds) }, "3m 05s"),
            FormatCase(
                "over 10 minutes the seconds are noise",
                { DownloadFormat.timeLeft(12.minutes + 1.seconds) },
                "13m",
            ),
            FormatCase(
                "just under an hour rounds to an hour, not 60m",
                { DownloadFormat.timeLeft(59.minutes + 30.seconds) },
                "1h 00m",
            ),
            FormatCase("hours", { DownloadFormat.timeLeft(1.hours + 2.minutes) }, "1h 02m"),
        )

        private fun downloading(bytesPerSecond: Double?, timeLeft: Duration?) = AnimeDownloadItemState.Downloading(
            DownloadProgress(
                fraction = 0.5f,
                downloadedBytes = MB,
                totalBytes = 2 * MB,
                totalBytesEstimated = false,
                bytesPerSecond = bytesPerSecond,
                timeLeft = timeLeft,
                stalled = false,
            ),
        )

        @JvmStatic
        fun summaryCases() = listOf(
            SummaryCase(
                "two side by side downloads: speeds add up, and they are done when the slower one is",
                states = listOf(
                    downloading(MB.toDouble(), 2.minutes),
                    downloading(2.0 * MB, 5.minutes),
                    AnimeDownloadItemState.Queued,
                    AnimeDownloadItemState.Unfinished,
                ),
                expected = AnimeDownloadQueueSummary(
                    total = 4,
                    active = 2,
                    unfinished = 1,
                    bytesPerSecond = 3.0 * MB,
                    timeLeft = 5.minutes,
                    paused = false,
                ),
            ),
            SummaryCase(
                "one current download can't tell its time left, so the header doesn't claim one",
                states = listOf(downloading(MB.toDouble(), 2.minutes), downloading(MB.toDouble(), null)),
                expected = AnimeDownloadQueueSummary(2, 2, 0, 2.0 * MB, null, paused = false),
            ),
            SummaryCase(
                "a download finding a server counts as active, without speed until it transfers",
                states = listOf(AnimeDownloadItemState.Starting, AnimeDownloadItemState.Queued),
                expected = AnimeDownloadQueueSummary(2, 1, 0, null, null, paused = false),
            ),
            SummaryCase(
                "downloader stopped with work left: paused",
                states = listOf(AnimeDownloadItemState.Paused, AnimeDownloadItemState.Unfinished),
                downloaderRunning = false,
                expected = AnimeDownloadQueueSummary(2, 0, 1, null, null, paused = true),
            ),
        )

        @JvmStatic
        fun stateCases() = listOf(
            StateCase(
                "the download being resolved is still QUEUE, but is not just waiting in line",
                status = AnimeDownload.State.QUEUE,
                active = true,
                expected = AnimeDownloadItemState.Starting,
            ),
            StateCase(
                "queued with the downloader stopped is paused, not queued",
                status = AnimeDownload.State.QUEUE,
                downloaderRunning = false,
                expected = AnimeDownloadItemState.Paused,
            ),
            StateCase(
                "ERROR is a failure or a Stop (or lost network) mid-download; either starts again with the queue",
                status = AnimeDownload.State.ERROR,
                expected = AnimeDownloadItemState.Unfinished,
            ),
            StateCase(
                "DOWNLOADED shows as finished until it leaves the queue",
                status = AnimeDownload.State.DOWNLOADED,
                expected = AnimeDownloadItemState.Finished,
            ),
            StateCase(
                "downloading before the first report has no progress yet (indeterminate bar)",
                status = AnimeDownload.State.DOWNLOADING,
                expected = AnimeDownloadItemState.Downloading(null),
            ),
        )
    }
}
