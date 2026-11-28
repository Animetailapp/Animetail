package eu.kanade.tachiyomi.data.opdetect

/** Frames `[start, end)` of a fingerprint. */
data class FrameSpan(val start: Int, val end: Int) {
    val length: Int get() = end - start

    fun overlap(other: FrameSpan): Int = (minOf(end, other.end) - maxOf(start, other.start)).coerceAtLeast(0)
}

/** A stretch of audio found in both fingerprints: [a] in the first, [b] in the second. */
data class SharedRun(val a: FrameSpan, val b: FrameSpan, val matchedFrames: Int)

/**
 * Finds the longest stretch of audio two fingerprints share, at any offset between them.
 *
 * Like the Jellyfin intro-skipper's chromaprint comparison: every relative shift of the two
 * fingerprints is tried; at each shift, aligned frames whose hashes differ in at most
 * [Config.maxBitErrors] bits match, and matches no more than [Config.maxGapFrames] apart form a
 * run. The run with the most matching frames wins if it is long enough, dense enough, and not
 * silence or a steady tone. With 6-minute windows a pair is ~3600 x 3600 frame comparisons.
 */
class SharedSegmentMatcher(private val config: Config = Config()) {

    data class Config(
        /** Hashes differing in at most this many of 32 bits match (random audio: 16 on average). */
        val maxBitErrors: Int = 10,
        /** Matches at most this many frames apart (2 s) belong to the same run. */
        val maxGapFrames: Int = 20,
        /** Shortest run accepted (15 s). */
        val minFrames: Int = 150,
        /** Longest run accepted (130 s); longer shared audio is not an opening or ending. */
        val maxFrames: Int = 1300,
        /** Fraction of a run's frames that must match. */
        val minDensity: Double = 0.5,
        /**
         * Fraction of a run's matched frames with a distinct hash. Silence, hum or a steady tone
         * hash the same frame after frame and is rejected even if it is not quiet.
         */
        val minDistinctRatio: Double = 0.3,
        /** Frames inward from each end of a run that are checked when trimming its ends. */
        val edgeFrames: Int = 4,
        /**
         * Fraction of those frames that must match. Not all of them: when two copies are half a
         * frame out of step, fast-changing music misses a frame now and then even where it is shared.
         */
        val edgeDensity: Double = 0.75,
    )

    fun findLongestSharedRun(a: AudioFingerprint, b: AudioFingerprint): SharedRun? {
        var best: SharedRun? = null
        // b frame = a frame + shift
        for (shift in -(a.size - 1) until b.size) {
            val firstA = maxOf(0, -shift)
            val lastA = minOf(a.size, b.size - shift) // exclusive
            if (lastA - firstA < config.minFrames) continue

            var runStart = -1
            var lastMatch = -1
            for (i in firstA until lastA) {
                if (!matches(a, b, i, shift)) continue
                if (runStart >= 0 && i - lastMatch > config.maxGapFrames) {
                    best = better(best, candidate(a, b, runStart, lastMatch + 1, shift))
                    runStart = -1
                }
                if (runStart < 0) runStart = i
                lastMatch = i
            }
            if (runStart >= 0) {
                best = better(best, candidate(a, b, runStart, lastMatch + 1, shift))
            }
        }
        return best
    }

    private fun better(current: SharedRun?, candidate: SharedRun?): SharedRun? = when {
        candidate == null -> current
        current == null || candidate.matchedFrames > current.matchedFrames -> candidate
        else -> current
    }

    private fun matches(a: AudioFingerprint, b: AudioFingerprint, i: Int, shift: Int): Boolean {
        val j = i + shift
        return !a.silent[i] && !b.silent[j] && Integer.bitCount(a.hashes[i] xor b.hashes[j]) <= config.maxBitErrors
    }

    /**
     * Trims chance matches off the ends of a run: unrelated audio matches about 1 frame in 40, and
     * the gap tolerance would otherwise stretch a run by up to [Config.maxGapFrames] at each end.
     * An end is kept only where [Config.edgeDensity] of the [Config.edgeFrames] frames inward match.
     */
    private fun candidate(
        a: AudioFingerprint,
        b: AudioFingerprint,
        runStart: Int,
        runEnd: Int,
        shift: Int,
    ): SharedRun? {
        if (runEnd - runStart < config.minFrames) return null
        val needed = (config.edgeFrames * config.edgeDensity).toInt()
        fun denseFrom(i: Int, step: Int): Boolean {
            var count = 0
            var k = i
            repeat(config.edgeFrames) {
                if (k !in runStart until runEnd) return@repeat
                if (matches(a, b, k, shift)) count++
                k += step
            }
            return count >= needed
        }

        var start = runStart
        while (start < runEnd && !(matches(a, b, start, shift) && denseFrom(start, 1))) start++
        var end = runEnd
        while (end > start && !(matches(a, b, end - 1, shift) && denseFrom(end - 1, -1))) end--

        val length = end - start
        if (length < config.minFrames || length > config.maxFrames) return null
        val matched = (start until end).count { matches(a, b, it, shift) }
        if (matched < length * config.minDensity) return null
        val distinct = (start until end).filter { matches(a, b, it, shift) }.mapTo(HashSet()) { a.hashes[it] }.size
        if (distinct < matched * config.minDistinctRatio) return null
        return SharedRun(
            a = FrameSpan(start, end),
            b = FrameSpan(start + shift, end + shift),
            matchedFrames = matched,
        )
    }
}

/**
 * Decides, per episode, which stretch of a window (e.g. the first 6 minutes) is the opening or
 * ending, from the audio it shares with other episodes of the show.
 *
 * Each episode is compared with its nearest episodes (by list order). A stretch is accepted when
 * at least [minAgreeingPairs] of those comparisons found overlapping shared audio, which rules
 * out music that only two episodes happen to share; with only two episodes, their one comparison
 * is enough. The neighbours tried first are the previous and next episode; when they don't agree
 * (an episode without the opening, a recap), up to [maxPartners] neighbours are tried. The
 * accepted stretch is the median start and end of the agreeing comparisons.
 */
class SegmentConsensus(
    private val matcher: SharedSegmentMatcher = SharedSegmentMatcher(),
    private val minAgreeingPairs: Int = 2,
    private val maxPartners: Int = 4,
    /** Two comparisons agree when their stretches overlap by at least this much of the shorter. */
    private val minOverlap: Double = 0.5,
) {

    /** For each fingerprint (null if unavailable), the detected stretch, or null if none. */
    fun detect(fingerprints: List<AudioFingerprint?>): List<FrameSpan?> {
        val pairs = HashMap<Pair<Int, Int>, SharedRun?>()
        fun spanIn(episode: Int, partner: Int): FrameSpan? {
            val key = minOf(episode, partner) to maxOf(episode, partner)
            val run = pairs.getOrPut(key) {
                matcher.findLongestSharedRun(fingerprints[key.first]!!, fingerprints[key.second]!!)
            } ?: return null
            return if (episode == key.first) run.a else run.b
        }

        return fingerprints.indices.map { episode ->
            if (fingerprints[episode] == null) return@map null
            val partners = fingerprints.indices
                .filter { it != episode && fingerprints[it] != null }
                .sortedWith(compareBy({ kotlin.math.abs(it - episode) }, { it }))
                .take(maxPartners)
            val required = minOf(minAgreeingPairs, partners.size)
            if (required == 0) return@map null

            val spans = mutableListOf<FrameSpan>()
            for ((examined, partner) in partners.withIndex()) {
                spanIn(episode, partner)?.let(spans::add)
                if (examined + 1 < required) continue
                val agreeing = largestAgreeingGroup(spans)
                if (agreeing.size >= required) return@map median(agreeing)
            }
            null
        }
    }

    private fun largestAgreeingGroup(spans: List<FrameSpan>): List<FrameSpan> {
        return spans
            .map { span -> spans.filter { it.overlap(span) >= minOverlap * minOf(it.length, span.length) } }
            .maxByOrNull { it.size }
            .orEmpty()
    }

    private fun median(spans: List<FrameSpan>): FrameSpan {
        fun List<Int>.median(): Int = sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }
        return FrameSpan(spans.map { it.start }.median(), spans.map { it.end }.median())
    }
}
