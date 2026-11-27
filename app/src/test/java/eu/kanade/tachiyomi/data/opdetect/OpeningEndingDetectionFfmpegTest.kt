package eu.kanade.tachiyomi.data.opdetect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.items.episode.segment.model.EpisodeSegment
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * End to end on the JVM with the host's ffmpeg: two generated episodes (video + lossy audio,
 * encoded at different bitrates) share a 30 s opening and a 20 s ending at different offsets that
 * are not multiples of the fingerprint hop. They are decoded with the app's FFmpeg arguments,
 * fingerprinted and matched. Skipped when ffmpeg is not on the PATH.
 */
class OpeningEndingDetectionFfmpegTest {

    @TempDir
    lateinit var dir: File

    private data class Episode(val name: String, val parts: List<Pair<String, Double>>, val audioBitrate: String) {
        val duration get() = parts.sumOf { it.second }
        fun start(kind: String) = parts.takeWhile { it.first != kind }.sumOf { it.second }
    }

    @Test
    fun `finds the shared opening and ending of two generated episodes`() {
        assumeTrue(run("ffmpeg", "-version") == 0, "ffmpeg not available")

        val episodes = listOf(
            Episode(
                "a",
                listOf("noise1" to 12.3, "op" to 30.0, "noise2" to 50.0, "ed" to 20.0, "noise3" to 7.7),
                audioBitrate = "128k",
            ),
            Episode(
                "b",
                listOf("noise4" to 25.65, "op" to 30.0, "noise5" to 31.35, "ed" to 20.0, "noise6" to 13.0),
                audioBitrate = "96k",
            ),
        )
        val audio = episodes.map { episode ->
            val file = File(dir, "${episode.name}.mkv")
            generate(episode, file)
            val head = OpeningEndingDetection.headWindow(episode.duration)
            val tail = OpeningEndingDetection.tailWindow(episode.duration)
            EpisodeAudio(
                durationSeconds = episode.duration,
                head = WindowFingerprint(head.start, fingerprint(file, head)),
                tail = WindowFingerprint(tail.start, fingerprint(file, tail)),
            )
        }

        val detected = OpeningEndingDetection.detect(audio)

        episodes.zip(detected).forEach { (episode, segments) ->
            val opening = segments.single { it.type == EpisodeSegment.Type.Opening }
            val ending = segments.single { it.type == EpisodeSegment.Type.Ending }
            val message = "episode ${episode.name}: $segments"
            assertEquals(episode.start("op"), opening.startSeconds, TOLERANCE, message)
            assertEquals(episode.start("op") + 30.0, opening.endSeconds, TOLERANCE, message)
            assertEquals(episode.start("ed"), ending.startSeconds, TOLERANCE, message)
            assertEquals(episode.start("ed") + 20.0, ending.endSeconds, TOLERANCE, message)
        }
    }

    private fun fingerprint(file: File, window: ClosedFloatingPointRange<Double>): AudioFingerprint {
        val pcm = File(dir, "${file.nameWithoutExtension}-${window.start}.pcm")
        val args = OpeningEndingDetection.decodeArguments(
            file.absolutePath,
            pcm.absolutePath,
            window.start,
            window.endInclusive - window.start,
        )
        assertEquals(0, run("ffmpeg", *args))
        val bytes = ByteBuffer.wrap(pcm.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return AudioFingerprinter.fingerprint(ShortArray(bytes.remaining()).also { bytes.get(it) })
    }

    private fun generate(episode: Episode, output: File) {
        val inputs = episode.parts.flatMap { (kind, seconds) ->
            listOf("-f", "lavfi", "-i", source(kind, seconds))
        }
        val concat = episode.parts.indices.joinToString("") { "[$it:a]" } + "concat=n=${episode.parts.size}:v=0:a=1[a]"
        val result = run(
            "ffmpeg", "-hide_banner", "-v", "error", "-y",
            *inputs.toTypedArray(),
            "-f", "lavfi", "-i", "testsrc2=size=160x90:rate=10:duration=${episode.duration}",
            "-filter_complex", concat,
            "-map", "${episode.parts.size}:v", "-map", "[a]",
            "-c:v", "libx264", "-preset", "ultrafast",
            "-c:a", "aac", "-b:a", episode.audioBitrate, "-ar", "44100",
            output.absolutePath,
        )
        assertEquals(0, result)
    }

    /** Deterministic "music" for shared parts, different noise for unique parts. */
    private fun source(kind: String, seconds: Double): String = when (kind) {
        // A melody: a new note every 0.25 s, two voices, with a slow tremolo.
        "op" ->
            "aevalsrc='0.3*sin(2*PI*t*(330+220*abs(sin(floor(t*4)*1.3))))" +
                "+0.2*sin(2*PI*t*(660+330*abs(cos(floor(t*2)*0.7))))*(0.6+0.4*sin(2*PI*0.5*t))'" +
                ":s=44100:d=$seconds"

        "ed" ->
            "aevalsrc='0.35*sin(2*PI*t*(500+300*abs(sin(floor(t*3)*2.1))))" +
                "+0.15*sin(2*PI*t*(250+120*abs(cos(floor(t*6)*0.9))))':s=44100:d=$seconds"

        else -> "anoisesrc=d=$seconds:c=pink:a=0.25:r=44100:seed=${kind.removePrefix("noise")}"
    }

    private fun run(vararg command: String): Int {
        return try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                return -1
            }
            if (process.exitValue() != 0) println(output)
            process.exitValue()
        } catch (e: java.io.IOException) {
            -1
        }
    }

    private companion object {
        const val TOLERANCE = 1.0
    }
}
