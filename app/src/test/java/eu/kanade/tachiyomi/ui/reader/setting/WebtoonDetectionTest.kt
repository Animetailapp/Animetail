package eu.kanade.tachiyomi.ui.reader.setting

import eu.kanade.tachiyomi.ui.reader.setting.WebtoonDetection.PageSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class WebtoonDetectionTest {

    data class Case(
        val description: String,
        val genres: List<String>? = null,
        val sourceName: String = "MangaDex",
        val pages: List<PageSize> = emptyList(),
        val chapterPageCount: Int = 20,
        val seriesMode: ReadingMode = ReadingMode.DEFAULT,
        val appDefault: ReadingMode = ReadingMode.RIGHT_TO_LEFT,
        val detectEnabled: Boolean = true,
        val expected: ReadingMode,
    ) {
        override fun toString() = description
    }

    // Runs the reader's order: a series mode skips detection, metadata decides before pages, then pages
    private fun open(case: Case): ReadingMode {
        var verdict = WebtoonVerdict.UNDECIDED
        if (WebtoonDetection.needsDetection(case.seriesMode, case.detectEnabled, verdict)) {
            verdict = WebtoonDetection.verdictFromMetadata(case.genres, case.sourceName)
        }
        if (WebtoonDetection.needsDetection(case.seriesMode, case.detectEnabled, verdict)) {
            verdict = WebtoonDetection.verdictFromPages(case.pages, case.chapterPageCount)
        }
        return WebtoonDetection.resolveReadingMode(case.seriesMode, case.appDefault, case.detectEnabled, verdict)
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `webtoons open in long strip without gaps unless the series says otherwise`(case: Case) {
        assertEquals(case.expected, open(case), case.description)
    }

    @Test
    fun `the verdict bits stay clear of the reading mode and orientation bits`() {
        assertEquals(0, WebtoonVerdict.MASK and ReadingMode.MASK)
        assertEquals(0, WebtoonVerdict.MASK and ReaderOrientation.MASK)
        val flags = ReadingMode.VERTICAL.flagValue.toLong() or WebtoonVerdict.NOT_WEBTOON.flagValue.toLong()
        assertEquals(WebtoonVerdict.NOT_WEBTOON, WebtoonVerdict.fromFlags(flags))
        assertEquals(ReadingMode.VERTICAL.flagValue.toLong(), flags and ReadingMode.MASK.toLong())
    }

    companion object {
        private val mangaPage = PageSize(width = 1100, height = 1600)
        private val stripPage = PageSize(width = 800, height = 12000)

        @JvmStatic
        fun cases() = listOf(
            Case(
                description = "a Manhwa genre tag is enough, before any page has loaded",
                genres = listOf("Action", "Manhwa"),
                expected = ReadingMode.WEBTOON,
            ),
            Case(
                description = "tags are matched loosely, like MangaDex's \"Long Strip\" format tag",
                genres = listOf("long-strip"),
                expected = ReadingMode.WEBTOON,
            ),
            Case(
                description = "a source that only carries webtoons marks its series without tags",
                sourceName = "Webtoons.com",
                expected = ReadingMode.WEBTOON,
            ),
            Case(
                description = "a plain manga without tags and normal pages keeps the app default",
                genres = emptyList(),
                pages = List(4) { mangaPage },
                expected = ReadingMode.RIGHT_TO_LEFT,
            ),
            Case(
                description = "untagged tall pages are a webtoon, and its default is long strip without gaps even " +
                    "when the app default is long strip with gaps",
                pages = List(4) { stripPage },
                appDefault = ReadingMode.CONTINUOUS_VERTICAL,
                expected = ReadingMode.WEBTOON,
            ),
            Case(
                description = "one tall splash or credits page among normal pages must not flip a manga to long strip",
                pages = listOf(mangaPage, stripPage, mangaPage, mangaPage),
                expected = ReadingMode.RIGHT_TO_LEFT,
            ),
            Case(
                description = "a single tall page is not enough to decide while the rest of the sample is loading",
                pages = listOf(stripPage),
                expected = ReadingMode.RIGHT_TO_LEFT,
            ),
            Case(
                description = "a two-page chapter of tall strips decides on what it has",
                pages = List(2) { stripPage },
                chapterPageCount = 2,
                expected = ReadingMode.WEBTOON,
            ),
            Case(
                description = "pages that could not be measured don't count as normal pages",
                pages = listOf(stripPage, PageSize(0, 0), stripPage, stripPage, stripPage),
                expected = ReadingMode.WEBTOON,
            ),
            Case(
                description = "a reading mode the user set on the series wins over a Manhwa tag and tall pages",
                genres = listOf("Manhwa"),
                pages = List(4) { stripPage },
                seriesMode = ReadingMode.LEFT_TO_RIGHT,
                expected = ReadingMode.LEFT_TO_RIGHT,
            ),
            Case(
                description = "with detection turned off a tagged webtoon keeps the app default",
                genres = listOf("Webtoon"),
                detectEnabled = false,
                expected = ReadingMode.RIGHT_TO_LEFT,
            ),
        )
    }
}
