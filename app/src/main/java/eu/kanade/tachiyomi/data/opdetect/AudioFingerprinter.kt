package eu.kanade.tachiyomi.data.opdetect

import java.io.DataInput
import java.io.DataOutput
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Per-frame 32-bit audio hashes of one stretch of an episode, one every [AudioFingerprinter.HOP_SECONDS].
 *
 * @property hashes the frame hashes. Frames that sound alike have hashes that differ in few bits.
 * @property silent whether each frame is (near) silence. Silence is the same in every episode, so it
 *   never counts as shared audio.
 */
class AudioFingerprint(val hashes: IntArray, val silent: BooleanArray) {
    init {
        require(hashes.size == silent.size)
    }

    val size: Int get() = hashes.size

    fun writeTo(out: DataOutput) {
        out.writeInt(size)
        hashes.forEach(out::writeInt)
        silent.forEach(out::writeBoolean)
    }

    companion object {
        fun readFrom(input: DataInput): AudioFingerprint {
            val size = input.readInt()
            val hashes = IntArray(size) { input.readInt() }
            val silent = BooleanArray(size) { input.readBoolean() }
            return AudioFingerprint(hashes, silent)
        }
    }
}

/**
 * Computes [AudioFingerprint]s from mono 16-bit PCM at [SAMPLE_RATE] Hz.
 *
 * The bundled FFmpeg has no chromaprint muxer, so this is a small Haitsma-Kalker ("Philips") style
 * fingerprint instead: each frame's 300-2000 Hz spectrum is split into 33 log-spaced bands, and bit
 * `m` of the hash is set when the energy difference between bands `m` and `m + 1` grew since the
 * previous frame. Only the sign of energy changes matters, so it survives re-encoding, volume
 * changes and the small time shifts between two copies of the same music.
 */
object AudioFingerprinter {
    /** FFmpeg resamples to this; it keeps everything up to the 2000 Hz top band. */
    const val SAMPLE_RATE = 5512

    /** 2048 samples, about 0.37 s. */
    const val FRAME_SIZE = 2048

    /** About 0.1 s between frames, so two copies of a segment are at most 50 ms out of step. */
    const val HOP_SIZE = 551

    const val HOP_SECONDS = HOP_SIZE.toDouble() / SAMPLE_RATE

    /** Time from the start of a frame to its centre, which is where its hash is placed in time. */
    const val FRAME_CENTER_SECONDS = FRAME_SIZE / 2.0 / SAMPLE_RATE

    private const val BAND_COUNT = 33
    private const val MIN_FREQUENCY = 300.0
    private const val MAX_FREQUENCY = 2000.0

    /** Frames quieter than this RMS (about -45 dBFS) count as silence. */
    private const val SILENCE_RMS = 184.0

    private val window = DoubleArray(FRAME_SIZE) { 0.5 - 0.5 * cos(2 * PI * it / (FRAME_SIZE - 1)) }

    /** FFT bin where each band starts; band `b` covers bins `[bandEdges[b], bandEdges[b + 1])`. */
    private val bandEdges = IntArray(BAND_COUNT + 1) {
        val frequency = MIN_FREQUENCY * (MAX_FREQUENCY / MIN_FREQUENCY).pow(it.toDouble() / BAND_COUNT)
        (frequency * FRAME_SIZE / SAMPLE_RATE).toInt()
    }

    fun frameCount(sampleCount: Int): Int =
        if (sampleCount < FRAME_SIZE) 0 else (sampleCount - FRAME_SIZE) / HOP_SIZE + 1

    fun fingerprint(samples: ShortArray): AudioFingerprint {
        val count = frameCount(samples.size)
        val hashes = IntArray(count)
        val silent = BooleanArray(count)
        val re = DoubleArray(FRAME_SIZE)
        val im = DoubleArray(FRAME_SIZE)
        var previous = DoubleArray(BAND_COUNT)
        var current = DoubleArray(BAND_COUNT)
        for (frame in 0 until count) {
            val offset = frame * HOP_SIZE
            var sumSquares = 0.0
            for (i in 0 until FRAME_SIZE) {
                val sample = samples[offset + i].toDouble()
                sumSquares += sample * sample
                re[i] = sample * window[i]
                im[i] = 0.0
            }
            silent[frame] = sqrt(sumSquares / FRAME_SIZE) < SILENCE_RMS
            fft(re, im)
            for (band in 0 until BAND_COUNT) {
                var energy = 0.0
                for (bin in bandEdges[band] until bandEdges[band + 1]) {
                    energy += re[bin] * re[bin] + im[bin] * im[bin]
                }
                current[band] = energy
            }
            if (frame == 0) current.copyInto(previous)
            var hash = 0
            for (band in 0 until BAND_COUNT - 1) {
                val delta = (current[band] - current[band + 1]) - (previous[band] - previous[band + 1])
                if (delta > 0) hash = hash or (1 shl band)
            }
            hashes[frame] = hash
            val swap = previous
            previous = current
            current = swap
        }
        return AudioFingerprint(hashes, silent)
    }

    /** In-place iterative radix-2 complex FFT; the size must be a power of two. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]
                re[i] = re[j]
                re[j] = t
                t = im[i]
                im[i] = im[j]
                im[j] = t
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2 * PI / length
            val wRe = cos(angle)
            val wIm = sin(angle)
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until length / 2) {
                    val a = start + k
                    val b = a + length / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                start += length
            }
            length = length shl 1
        }
    }
}
