package eu.kanade.tachiyomi.data.opdetect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

class SharedSegmentMatcherTest {

    /** [frames] of [kind] audio; frames are 0.1 s. */
    data class Part(val frames: Int, val kind: Kind)

    sealed interface Kind {
        /** Audio unique to this episode. */
        data object Unique : Kind

        /** Audio shared between episodes: the same hashes, with up to [noiseBits] bits flipped. */
        data class Shared(val seed: Int, val noiseBits: Int = 4) : Kind

        /** Silence: every episode's is the same. */
        data object Silence : Kind

        /** A loud steady tone: the same hash every frame. */
        data object Tone : Kind
    }

    data class Case(
        val description: String,
        val a: List<Part>,
        val b: List<Part>,
        /** Expected start frame in a and in b, and length, or null for no match. */
        val expected: Triple<Int, Int, Int>?,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `longest shared run`(case: Case) {
        val run = SharedSegmentMatcher().findLongestSharedRun(build(case.a, seed = 1), build(case.b, seed = 2))
        if (case.expected == null) {
            assertNull(run, "expected no match, got $run")
            return
        }
        val (startA, startB, length) = case.expected
        assertTrue(run != null, "expected a match")
        run!!
        // The edges of a noisy run may be off by a couple of frames.
        assertEquals(startA.toDouble(), run.a.start.toDouble(), 3.0, "start in a: $run")
        assertEquals(startB.toDouble(), run.b.start.toDouble(), 3.0, "start in b: $run")
        assertEquals(length.toDouble(), run.a.length.toDouble(), 6.0, "length: $run")
        assertEquals(run.a.length, run.b.length)
    }

    companion object {
        private val U = Kind.Unique

        @JvmStatic
        fun cases() = listOf(
            Case(
                "shared 30 s at different offsets",
                a = listOf(Part(500, U), Part(300, Kind.Shared(7)), Part(1000, U)),
                b = listOf(Part(1200, U), Part(300, Kind.Shared(7)), Part(300, U)),
                expected = Triple(500, 1200, 300),
            ),
            Case(
                "shared at the very start of one window",
                a = listOf(Part(900, Kind.Shared(7)), Part(900, U)),
                b = listOf(Part(37, U), Part(900, Kind.Shared(7)), Part(800, U)),
                expected = Triple(0, 37, 900),
            ),
            Case(
                "nothing shared",
                a = listOf(Part(1800, U)),
                b = listOf(Part(1800, U)),
                expected = null,
            ),
            Case(
                "shared stretch shorter than 15 s (eyecatch) is ignored",
                a = listOf(Part(400, U), Part(100, Kind.Shared(7)), Part(400, U)),
                b = listOf(Part(200, U), Part(100, Kind.Shared(7)), Part(600, U)),
                expected = null,
            ),
            Case(
                "shared stretch longer than 130 s is not an opening",
                a = listOf(Part(100, U), Part(1500, Kind.Shared(7)), Part(100, U)),
                b = listOf(Part(300, U), Part(1500, Kind.Shared(7)), Part(100, U)),
                expected = null,
            ),
            Case(
                "only part of the opening is in the other window: the shared part is found",
                a = listOf(Part(1000, U), Part(900, Kind.Shared(7))),
                b = listOf(Part(1500, U), Part(300, Kind.Shared(7))),
                expected = Triple(1000, 1500, 300),
            ),
            Case(
                "shared silence never matches",
                a = listOf(Part(300, U), Part(600, Kind.Silence), Part(300, U)),
                b = listOf(Part(500, U), Part(600, Kind.Silence), Part(300, U)),
                expected = null,
            ),
            Case(
                "a loud steady tone is not an opening",
                a = listOf(Part(300, U), Part(600, Kind.Tone), Part(300, U)),
                b = listOf(Part(500, U), Part(600, Kind.Tone), Part(300, U)),
                expected = null,
            ),
            Case(
                "a short silence inside the opening does not split it",
                a = listOf(
                    Part(200, U),
                    Part(400, Kind.Shared(7)),
                    Part(15, Kind.Silence),
                    Part(400, Kind.Shared(8)),
                    Part(200, U),
                ),
                b = listOf(
                    Part(600, U),
                    Part(400, Kind.Shared(7)),
                    Part(15, Kind.Silence),
                    Part(400, Kind.Shared(8)),
                    Part(100, U),
                ),
                expected = Triple(200, 600, 815),
            ),
            Case(
                "the longer of two shared stretches wins",
                a = listOf(
                    Part(100, U),
                    Part(160, Kind.Shared(3)),
                    Part(300, U),
                    Part(900, Kind.Shared(7)),
                    Part(100, U),
                ),
                b = listOf(
                    Part(600, U),
                    Part(900, Kind.Shared(7)),
                    Part(200, U),
                    Part(160, Kind.Shared(3)),
                    Part(100, U),
                ),
                expected = Triple(560, 600, 900),
            ),
            Case(
                "heavier re-encoding noise (6 bits) still matches",
                a = listOf(Part(500, U), Part(800, Kind.Shared(7, noiseBits = 6)), Part(500, U)),
                b = listOf(Part(800, U), Part(800, Kind.Shared(7, noiseBits = 6)), Part(200, U)),
                expected = Triple(500, 800, 800),
            ),
        )

        /** Builds a fingerprint; [seed] varies the unique audio and the noise between episodes. */
        fun build(parts: List<Part>, seed: Int): AudioFingerprint {
            val random = Random(seed)
            val hashes = mutableListOf<Int>()
            val silent = mutableListOf<Boolean>()
            parts.forEach { part ->
                val kind = part.kind
                val shared = (kind as? Kind.Shared)?.let { Random(it.seed) }
                repeat(part.frames) {
                    when (kind) {
                        Kind.Unique -> {
                            hashes += random.nextInt()
                            silent += false
                        }

                        is Kind.Shared -> {
                            var hash = shared!!.nextInt()
                            repeat(random.nextInt(kind.noiseBits + 1)) { hash = hash xor (1 shl random.nextInt(32)) }
                            hashes += hash
                            silent += false
                        }

                        Kind.Silence -> {
                            hashes += 0
                            silent += true
                        }

                        Kind.Tone -> {
                            hashes += 0x2A5A5A5A
                            silent += false
                        }
                    }
                }
            }
            return AudioFingerprint(hashes.toIntArray(), silent.toBooleanArray())
        }
    }
}
