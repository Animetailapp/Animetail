package eu.kanade.tachiyomi.ui.reader.setting

/**
 * What the reader decided about a series being a webtoon, kept in the manga's viewer flags next to the
 * reading mode so it is decided once per series. It never overrides a reading mode set on the series.
 */
enum class WebtoonVerdict(val flagValue: Int) {
    UNDECIDED(0x00000000),
    WEBTOON(0x00000040),
    NOT_WEBTOON(0x00000080),
    ;

    companion object {
        const val MASK = 0x000000C0

        fun fromFlags(viewerFlags: Long): WebtoonVerdict {
            val value = (viewerFlags and MASK.toLong()).toInt()
            return entries.find { it.flagValue == value } ?: UNDECIDED
        }
    }
}

/**
 * Pure heuristics that tell webtoons (one long vertical strip) from paged manga.
 */
object WebtoonDetection {

    /** A page at least this many times taller than wide is a strip, not a page. */
    const val TALL_PAGE_RATIO = 2.5

    /** Pages measured before deciding, fewer only when the chapter is shorter. */
    const val SAMPLE_SIZE = 4

    // Genre tags sources use for vertical-scroll series, compared lowercase with only letters kept
    private val webtoonTags = setOf(
        "webtoon",
        "webtoons",
        "manhwa",
        "manhua",
        "longstrip",
        "vertical",
        "verticalscroll",
    )

    // Source names that only carry vertical-scroll series (Webtoons.com, Toonily, ManhuaPlus, ...)
    private val webtoonSourceNameParts = listOf("webtoon", "toon", "manhwa", "manhua")

    data class PageSize(val width: Int, val height: Int)

    /**
     * The reading mode to open the series in. A reading mode set on the series always wins; otherwise a
     * series detected as a webtoon opens in long strip without gaps, and anything else in [appDefault].
     */
    fun resolveReadingMode(
        seriesMode: ReadingMode,
        appDefault: ReadingMode,
        detectEnabled: Boolean,
        verdict: WebtoonVerdict,
    ): ReadingMode = when {
        seriesMode != ReadingMode.DEFAULT -> seriesMode
        detectEnabled && verdict == WebtoonVerdict.WEBTOON -> ReadingMode.WEBTOON
        else -> appDefault
    }

    /**
     * Whether detection still has to run: it is on, the series has no reading mode of its own and nothing was
     * decided for it yet.
     */
    fun needsDetection(seriesMode: ReadingMode, detectEnabled: Boolean, verdict: WebtoonVerdict): Boolean =
        detectEnabled && seriesMode == ReadingMode.DEFAULT && verdict == WebtoonVerdict.UNDECIDED

    /**
     * [WebtoonVerdict.WEBTOON] when the series' genres or its source name say it is one, otherwise
     * [WebtoonVerdict.UNDECIDED] so the pages decide. A missing tag never means it is not a webtoon.
     */
    fun verdictFromMetadata(genres: List<String>?, sourceName: String?): WebtoonVerdict {
        val tagged = genres.orEmpty().any { normalize(it) in webtoonTags }
        val source = sourceName?.lowercase().orEmpty()
        val fromSource = webtoonSourceNameParts.any { it in source }
        return if (tagged || fromSource) WebtoonVerdict.WEBTOON else WebtoonVerdict.UNDECIDED
    }

    /**
     * Decides from the sizes of the first loaded pages of a chapter with [chapterPageCount] pages. Stays
     * [WebtoonVerdict.UNDECIDED] until [SAMPLE_SIZE] pages (or the whole chapter) are measured, then needs at
     * least two thirds of them to be tall, so one tall splash or credits page doesn't make a manga a webtoon.
     */
    fun verdictFromPages(sizes: List<PageSize>, chapterPageCount: Int): WebtoonVerdict {
        val measured = sizes.filter { it.width > 0 && it.height > 0 }
        val needed = minOf(SAMPLE_SIZE, chapterPageCount).coerceAtLeast(1)
        if (measured.size < needed) return WebtoonVerdict.UNDECIDED
        val tall = measured.count { it.height.toDouble() / it.width >= TALL_PAGE_RATIO }
        return if (tall * 3 >= measured.size * 2) WebtoonVerdict.WEBTOON else WebtoonVerdict.NOT_WEBTOON
    }

    private fun normalize(tag: String): String = tag.lowercase().filter { it.isLetter() }
}
