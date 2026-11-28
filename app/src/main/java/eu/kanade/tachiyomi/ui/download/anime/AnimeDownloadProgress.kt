package eu.kanade.tachiyomi.ui.download.anime

import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Smoothed rates of one download attempt, from its consecutive [AnimeDownload.Transfer] reports.
 */
data class TransferEstimate(
    /** The latest report. */
    val sample: AnimeDownload.Transfer,
    /** Smoothed download speed, null until two reports were seen. */
    val bytesPerSecond: Double?,
    /** Smoothed media milliseconds downloaded per wall clock millisecond, null until two reports. */
    val mediaRate: Double?,
)

object TransferEstimator {
    /**
     * Time constant of the exponential smoothing: a lasting speed change is about 63% reflected after this
     * long, and a one-report burst moves the speed by only a fraction of its size.
     */
    const val SMOOTHING_MS = 4_000.0

    /** No report for this long and the download counts as stalled: zero speed and no time left. */
    const val STALLED_AFTER_MS = 5_000L

    /** Below this fraction done, a total size extrapolated from the progress is too rough to show. */
    const val MIN_FRACTION_FOR_SIZE_ESTIMATE = 0.02

    /** Rates below these are treated as zero, so a near-zero speed shows no time left instead of days. */
    private const val MIN_BYTES_PER_SECOND = 1.0
    private const val MIN_MEDIA_RATE = 0.001

    /** A longer time left than this is not meaningful. */
    private val MAX_TIME_LEFT = 100.hours

    /**
     * [previous] with [sample] folded in. Each rate is an exponential moving average weighted by the time
     * between reports, so irregular report intervals weigh correctly.
     */
    fun update(previous: TransferEstimate?, sample: AnimeDownload.Transfer): TransferEstimate {
        val last = previous?.sample ?: return TransferEstimate(sample, bytesPerSecond = null, mediaRate = null)
        // Counters going back is a new attempt (another server, a retry): its speed starts over
        if (sample.bytes < last.bytes || sample.mediaMs < last.mediaMs) {
            return TransferEstimate(sample, bytesPerSecond = null, mediaRate = null)
        }
        val elapsedMs = sample.atMs - last.atMs
        if (elapsedMs <= 0) return previous
        val weight = 1 - exp(-elapsedMs / SMOOTHING_MS)
        val bytesPerSecond = (sample.bytes - last.bytes) * 1000.0 / elapsedMs
        val mediaRate = (sample.mediaMs - last.mediaMs).toDouble() / elapsedMs
        return TransferEstimate(
            sample = sample,
            bytesPerSecond = smooth(previous.bytesPerSecond, bytesPerSecond, weight),
            mediaRate = smooth(previous.mediaRate, mediaRate, weight),
        )
    }

    private fun smooth(average: Double?, value: Double, weight: Double): Double {
        return if (average == null) value else average + weight * (value - average)
    }

    /**
     * What the queue shows for [estimate] at [nowMs] (`SystemClock.elapsedRealtime`).
     *
     * Progress follows the total size when it is known, else the media time against the duration (HLS
     * through FFmpeg has no total size), else it is unknown. Time left comes from the same basis.
     */
    fun progress(estimate: TransferEstimate, nowMs: Long): DownloadProgress {
        val sample = estimate.sample
        val totalBytes = sample.totalBytes?.takeIf { it > 0 }
        val durationMs = sample.durationMs?.takeIf { it > 0 }
        val stalled = nowMs - sample.atMs >= STALLED_AFTER_MS
        val bytesPerSecond = if (stalled) 0.0 else estimate.bytesPerSecond
        val mediaRate = if (stalled) 0.0 else estimate.mediaRate

        val fraction: Double?
        val timeLeft: Duration?
        when {
            totalBytes != null -> {
                fraction = sample.bytes.toDouble() / totalBytes
                timeLeft = bytesPerSecond?.takeIf { it >= MIN_BYTES_PER_SECOND }
                    ?.let { ((totalBytes - sample.bytes).coerceAtLeast(0) / it).seconds }
            }

            durationMs != null -> {
                fraction = sample.mediaMs.toDouble() / durationMs
                timeLeft = mediaRate?.takeIf { it >= MIN_MEDIA_RATE }
                    ?.let { ((durationMs - sample.mediaMs).coerceAtLeast(0) / it).milliseconds }
            }

            else -> {
                fraction = null
                timeLeft = null
            }
        }
        val estimatedTotal = totalBytes == null && fraction != null &&
            fraction >= MIN_FRACTION_FOR_SIZE_ESTIMATE && sample.bytes > 0
        return DownloadProgress(
            fraction = fraction?.coerceIn(0.0, 1.0)?.toFloat(),
            downloadedBytes = sample.bytes,
            totalBytes = totalBytes ?: if (estimatedTotal) (sample.bytes / fraction).roundToLong() else null,
            totalBytesEstimated = estimatedTotal,
            bytesPerSecond = bytesPerSecond,
            timeLeft = timeLeft?.takeIf { it <= MAX_TIME_LEFT },
            stalled = stalled,
        )
    }
}

data class DownloadProgress(
    /** Done, from 0 to 1, or null when neither the size nor the duration is known. */
    val fraction: Float?,
    val downloadedBytes: Long,
    /** Null when unknown. */
    val totalBytes: Long?,
    /** Whether [totalBytes] is extrapolated from the media time downloaded so far. */
    val totalBytesEstimated: Boolean,
    /** Null until measured, 0 while stalled. */
    val bytesPerSecond: Double?,
    /** Null when it can't be told: unknown total, no speed yet, or zero speed. */
    val timeLeft: Duration?,
    /** No progress report for [TransferEstimator.STALLED_AFTER_MS]. */
    val stalled: Boolean,
)

/** What a row of the anime download queue shows. */
sealed interface AnimeDownloadItemState {
    data object Queued : AnimeDownloadItemState

    /** Queued while the downloader is not running. */
    data object Paused : AnimeDownloadItemState

    /** Being worked on before the transfer starts: finding a working video, probing it. */
    data object Starting : AnimeDownloadItemState

    /** [progress] is null until the first report. */
    data class Downloading(val progress: DownloadProgress?) : AnimeDownloadItemState

    /** Failed, or stopped while downloading (Stop, no network); it starts again with the queue. */
    data object Unfinished : AnimeDownloadItemState

    /** Done, shown until it leaves the queue. */
    data object Finished : AnimeDownloadItemState

    companion object {
        fun of(
            status: AnimeDownload.State,
            active: Boolean,
            estimate: TransferEstimate?,
            downloaderRunning: Boolean,
            nowMs: Long,
        ): AnimeDownloadItemState = when (status) {
            AnimeDownload.State.DOWNLOADED -> Finished

            AnimeDownload.State.ERROR -> Unfinished

            AnimeDownload.State.DOWNLOADING -> Downloading(estimate?.let { TransferEstimator.progress(it, nowMs) })

            AnimeDownload.State.QUEUE, AnimeDownload.State.NOT_DOWNLOADED -> when {
                active -> Starting
                !downloaderRunning -> Paused
                else -> Queued
            }
        }
    }
}

/** The header of the anime download queue. */
data class AnimeDownloadQueueSummary(
    val total: Int,
    /** Downloading or starting. */
    val active: Int,
    /** Failed, or stopped (Stop, no network) while downloading. */
    val unfinished: Int,
    /** Sum of the speeds of the downloads transferring, null when none is measured yet. */
    val bytesPerSecond: Double?,
    /** Until the last of the current downloads is done, null unless every one of them can tell. */
    val timeLeft: Duration?,
    val paused: Boolean,
) {
    companion object {
        fun of(states: Collection<AnimeDownloadItemState>, downloaderRunning: Boolean): AnimeDownloadQueueSummary {
            val downloading = states.filterIsInstance<AnimeDownloadItemState.Downloading>()
            val active = downloading.size + states.count { it == AnimeDownloadItemState.Starting }
            val speeds = downloading.mapNotNull { it.progress?.bytesPerSecond }
            val timesLeft = downloading.map { it.progress?.timeLeft }
            return AnimeDownloadQueueSummary(
                total = states.size,
                active = active,
                unfinished = states.count { it == AnimeDownloadItemState.Unfinished },
                bytesPerSecond = speeds.takeIf { it.isNotEmpty() }?.sum(),
                // Current downloads run side by side, so they are all done when the slowest one is
                timeLeft = timesLeft.takeIf { it.isNotEmpty() && null !in it }?.maxOf { it!! },
                paused = !downloaderRunning && states.any {
                    it == AnimeDownloadItemState.Paused || it == AnimeDownloadItemState.Unfinished
                },
            )
        }
    }
}

object DownloadFormat {
    /** Decimal units like the rest of the app (see `toSize`), with three significant digits. */
    fun bytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.coerceAtLeast(0).toDouble()
        var unit = 0
        // 999.6 KB would round up to "1000 KB", so it moves to the next unit first
        while (value >= 999.5 && unit < units.lastIndex) {
            value /= 1000
            unit++
        }
        val number = when {
            unit == 0 || value >= 99.95 -> "%.0f".format(locale, value)
            value >= 9.995 -> "%.1f".format(locale, value)
            else -> "%.2f".format(locale, value)
        }
        return "$number ${units[unit]}"
    }

    fun speed(bytesPerSecond: Double, locale: Locale = Locale.getDefault()): String {
        return "${bytes(bytesPerSecond.roundToLong(), locale)}/s"
    }

    /** "45s", "3m 05s", "12m", "1h 02m": rounded up, with seconds only while they matter. */
    fun timeLeft(duration: Duration): String {
        val seconds = ceil(duration.inWholeMilliseconds / 1000.0).toLong().coerceAtLeast(0)
        if (seconds < 60) return "${seconds}s"
        if (seconds < 600) return "${seconds / 60}m ${"%02d".format(Locale.ROOT, seconds % 60)}s"
        val minutes = (seconds + 59) / 60
        if (minutes < 60) return "${minutes}m"
        return "${minutes / 60}h ${"%02d".format(Locale.ROOT, minutes % 60)}m"
    }
}
