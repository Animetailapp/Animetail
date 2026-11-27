package eu.kanade.tachiyomi.data.opdetect

import android.content.Context
import android.media.MediaMetadataRetriever
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

/**
 * Reads the [EpisodeAudio] of downloaded episodes: decodes the start and end windows with FFmpeg
 * and fingerprints them. Fingerprints are cached per downloaded file (by size), so analysing a show
 * again after another episode is downloaded only decodes the new one.
 */
@Inject
class EpisodeAudioReader(
    private val context: Context,
) {
    private val cacheDir get() = File(context.cacheDir, CACHE_DIR)

    suspend fun read(animeId: Long, episodeId: Long, file: UniFile): EpisodeAudio? {
        val size = file.length()
        val cached = cacheFile(animeId, episodeId, size)
        readCached(cached)?.let { return it }

        val duration = probeDuration(file) ?: return null
        val headRange = OpeningEndingDetection.headWindow(duration)
        val tailRange = OpeningEndingDetection.tailWindow(duration)
        val head = fingerprintWindow(file, headRange)?.let { WindowFingerprint(headRange.start, it) }
        val tail = fingerprintWindow(file, tailRange)?.let { WindowFingerprint(tailRange.start, it) }
        val audio = EpisodeAudio(duration, head, tail)
        if (head != null && tail != null) writeCached(cached, audio)
        return audio
    }

    /** Deletes the cached fingerprints of [animeId] other than those of [keep] (episode id to file size). */
    fun prune(animeId: Long, keep: Map<Long, Long>) {
        val keepNames = keep.map { (episodeId, size) -> cacheFile(animeId, episodeId, size).name }.toSet()
        cacheDir.listFiles { file -> file.name.startsWith("${animeId}_") && file.name !in keepNames }
            ?.forEach { it.delete() }
    }

    private fun cacheFile(animeId: Long, episodeId: Long, size: Long) =
        File(cacheDir, "${animeId}_${episodeId}_${size}_v$CACHE_VERSION.fp")

    private fun readCached(file: File): EpisodeAudio? {
        if (!file.exists()) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val duration = input.readDouble()
                val headOffset = input.readDouble()
                val head = AudioFingerprint.readFrom(input)
                val tailOffset = input.readDouble()
                val tail = AudioFingerprint.readFrom(input)
                EpisodeAudio(duration, WindowFingerprint(headOffset, head), WindowFingerprint(tailOffset, tail))
            }
        } catch (e: IOException) {
            logcat(LogPriority.WARN, e) { "Discarding unreadable fingerprint cache ${file.name}" }
            file.delete()
            null
        }
    }

    private fun writeCached(file: File, audio: EpisodeAudio) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeDouble(audio.durationSeconds)
                out.writeDouble(audio.head!!.offsetSeconds)
                audio.head.fingerprint.writeTo(out)
                out.writeDouble(audio.tail!!.offsetSeconds)
                audio.tail.fingerprint.writeTo(out)
            }
            tmp.renameTo(file)
        } catch (e: IOException) {
            logcat(LogPriority.WARN, e) { "Failed to cache fingerprint ${file.name}" }
        }
    }

    /**
     * Duration of [file] in seconds. FFprobeKit.getMediaInformation came back successful with no
     * output for downloaded files (saf: inputs), so this runs ffprobe for just the duration, as the
     * downloader does, and falls back to Android's MediaMetadataRetriever.
     */
    private suspend fun probeDuration(file: UniFile): Double? {
        val args = arrayOf(
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
            "-i",
            file.toFFmpegString(context, "r"),
        )
        val session = suspendCancellableCoroutine { continuation ->
            val session = FFprobeKit.executeWithArgumentsAsync(args) { continuation.resume(it) }
            continuation.invokeOnCancellation { session.cancel() }
        }
        val output = session.output.orEmpty()
        output.lineSequence().mapNotNull { it.trim().toDoubleOrNull() }.lastOrNull()?.takeIf { it > 0 }
            ?.let { return it }

        val retrieved = retrieverDuration(file)
        logcat(LogPriority.WARN) {
            "OP/ED detection: ffprobe found no duration for ${file.name} (${session.returnCode}): " +
                output.trim().takeLast(500).ifEmpty { "no output" } +
                "; MediaMetadataRetriever: ${retrieved ?: "none"}"
        }
        return retrieved
    }

    private fun retrieverDuration(file: UniFile): Double? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, file.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.let { it / 1000.0 }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "OP/ED detection: MediaMetadataRetriever failed for ${file.name}" }
            null
        } finally {
            retriever.release()
        }
    }

    private suspend fun fingerprintWindow(file: UniFile, range: ClosedFloatingPointRange<Double>): AudioFingerprint? {
        val pcm = File.createTempFile("op_ed_", ".pcm", context.cacheDir)
        try {
            val args = OpeningEndingDetection.decodeArguments(
                input = file.toFFmpegString(context, "r"),
                output = pcm.absolutePath,
                startSeconds = range.start,
                durationSeconds = range.endInclusive - range.start,
            )
            val session = runFFmpeg(args)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                logcat(LogPriority.WARN) {
                    "OP/ED detection: decoding ${file.name} failed (${session.returnCode}): " +
                        session.output?.takeLast(500)
                }
                return null
            }
            val samples = ByteBuffer.wrap(pcm.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            return AudioFingerprinter.fingerprint(ShortArray(samples.remaining()).also { samples.get(it) })
        } finally {
            pcm.delete()
        }
    }

    /** Runs one FFmpeg session; cancelling the coroutine cancels the session. */
    private suspend fun runFFmpeg(args: Array<String>): FFmpegSession = suspendCancellableCoroutine { continuation ->
        val session = FFmpegKit.executeWithArgumentsAsync(args) { continuation.resume(it) }
        continuation.invokeOnCancellation { session.cancel() }
    }

    private companion object {
        const val CACHE_DIR = "op_ed_fingerprints"

        /** Bump when the fingerprint or window parameters change. */
        const val CACHE_VERSION = 1
    }
}
