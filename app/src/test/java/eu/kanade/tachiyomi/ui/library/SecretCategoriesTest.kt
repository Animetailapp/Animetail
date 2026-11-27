package eu.kanade.tachiyomi.ui.library

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.domain.category.model.Category

class SecretCategoriesTest {

    /** A library row: entry [id] filed in category [category]. */
    data class Row(val id: Long, val category: Long)

    data class Case(
        val description: String,
        val categories: List<Category>,
        val rows: List<Row>,
        val unlocked: Boolean,
        val expectedTabs: List<Long>,
        val expectedEntries: Map<Long, List<Long>>,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `the library shows secret categories and their entries only while unlocked`(case: Case) {
        val view = SecretCategoryFilter.apply(
            allCategories = case.categories,
            entriesByCategoryId = case.rows.groupBy { it.category },
            entryId = Row::id,
            unlocked = case.unlocked,
        )

        assertEquals(case.expectedTabs, view.categories.map { it.id }, case.description)
        assertEquals(
            case.expectedEntries,
            view.entriesByCategoryId.mapValues { (_, rows) -> rows.map { it.id } },
            case.description,
        )
    }

    data class OutsideCase(
        val description: String,
        val categories: List<Category>,
        val rows: List<Row>,
        val expectedIds: List<Long>,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("outsideCases")
    fun `lists outside the library never show secret entries`(case: OutsideCase) {
        val rows = SecretCategoryFilter.withoutSecretEntries(case.categories, case.rows, Row::id, Row::category)

        assertEquals(case.expectedIds, rows.map { it.id }.distinct(), case.description)
    }

    data class TapCase(
        val description: String,
        val tapTimesMillis: List<Long>,
        val unlocksOnTap: List<Int>,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("tapCases")
    fun `only five quick taps in a row unlock`(case: TapCase) {
        val counter = SecretUnlockTapCounter()

        val unlocks = case.tapTimesMillis.mapIndexedNotNull { index, at -> (index + 1).takeIf { counter.onTap(at) } }

        assertEquals(case.unlocksOnTap, unlocks, case.description)
    }

    @AfterEach
    fun resetLock() {
        SecretCategoryLock.lock()
        SecretCategoryLock.onHomeTabShown(LIBRARY_TAB)
    }

    @Test
    fun `returning to the library tab keeps it unlocked, showing another tab locks it`() {
        SecretCategoryLock.onHomeTabShown(LIBRARY_TAB)
        SecretCategoryLock.unlock()

        // Back from an entry opened in the secret category: the home screen shows the library tab again
        SecretCategoryLock.onHomeTabShown(LIBRARY_TAB)
        assertTrue(SecretCategoryLock.unlocked.value)

        SecretCategoryLock.onHomeTabShown("updates")
        assertFalse(SecretCategoryLock.unlocked.value)
    }

    companion object {
        private const val LIBRARY_TAB = "library"
        private const val SECRET = 9L

        private fun category(id: Long, hidden: Boolean = false, secret: Boolean = false) =
            Category(id = id, name = "c$id", order = id, flags = 0, hidden = hidden, secret = secret)

        private val default = category(Category.UNCATEGORIZED_ID)
        private val shows = category(1)
        private val movies = category(2)
        private val secret = category(SECRET, secret = true)

        @JvmStatic
        fun cases() = listOf(
            Case(
                "locked: the secret category is not a tab and its entries are gone",
                categories = listOf(default, shows, secret),
                rows = listOf(Row(1, 1), Row(2, SECRET)),
                unlocked = false,
                expectedTabs = listOf(0, 1),
                expectedEntries = mapOf(1L to listOf(1L)),
            ),
            Case(
                "locked: an entry also filed in a visible category must not show through it, nor through " +
                    "ungrouped views that flatten every category",
                categories = listOf(default, shows, movies, secret),
                rows = listOf(Row(1, 1), Row(2, 1), Row(2, SECRET), Row(3, 2)),
                unlocked = false,
                expectedTabs = listOf(0, 1, 2),
                expectedEntries = mapOf(1L to listOf(1L), 2L to listOf(3L)),
            ),
            Case(
                "locked: a category emptied by the filter is dropped from the entries like one that never had any, " +
                    "so the library still knows whether the default category is needed",
                categories = listOf(default, shows, secret),
                rows = listOf(Row(2, 1), Row(2, SECRET)),
                unlocked = false,
                expectedTabs = listOf(0, 1),
                expectedEntries = emptyMap(),
            ),
            Case(
                "unlocked: the secret category is a tab in its usual place and its entries show everywhere",
                categories = listOf(default, shows, secret, movies),
                rows = listOf(Row(1, 1), Row(2, 1), Row(2, SECRET)),
                unlocked = true,
                expectedTabs = listOf(0, 1, SECRET, 2),
                expectedEntries = mapOf(1L to listOf(1L, 2L), SECRET to listOf(2L)),
            ),
            Case(
                "hidden categories stay out of the tabs either way, the unlock is only for secret ones",
                categories = listOf(default, shows, category(3, hidden = true), secret),
                rows = listOf(Row(1, 1)),
                unlocked = true,
                expectedTabs = listOf(0, 1, SECRET),
                expectedEntries = mapOf(1L to listOf(1L)),
            ),
            Case(
                "locked: a category that is both hidden and secret still keeps its entries out of other tabs",
                categories = listOf(default, shows, category(SECRET, hidden = true, secret = true)),
                rows = listOf(Row(1, 1), Row(2, 1), Row(2, SECRET)),
                unlocked = false,
                expectedTabs = listOf(0, 1),
                expectedEntries = mapOf(1L to listOf(1L)),
            ),
            Case(
                "no secret categories: nothing changes, locked or not",
                categories = listOf(default, shows),
                rows = listOf(Row(1, 0), Row(2, 1)),
                unlocked = false,
                expectedTabs = listOf(0, 1),
                expectedEntries = mapOf(0L to listOf(1L), 1L to listOf(2L)),
            ),
        )

        @JvmStatic
        fun outsideCases() = listOf(
            OutsideCase(
                "every row of an entry in a secret category goes, including its rows for visible categories",
                categories = listOf(default, shows, secret),
                rows = listOf(Row(1, 1), Row(2, 1), Row(2, SECRET), Row(3, 0)),
                expectedIds = listOf(1, 3),
            ),
            OutsideCase(
                "without secret categories every row stays",
                categories = listOf(default, shows),
                rows = listOf(Row(1, 1), Row(2, 0)),
                expectedIds = listOf(1, 2),
            ),
        )

        @JvmStatic
        fun tapCases() = listOf(
            TapCase("five taps 200 ms apart unlock on the fifth", listOf(0L, 200, 400, 600, 800), listOf(5)),
            TapCase("four quick taps are not enough", listOf(0L, 200, 400, 600), emptyList()),
            TapCase(
                "a pause longer than 500 ms starts the count over, so taps spread over time never add up",
                listOf(0L, 200, 400, 600, 1200, 1400, 1600, 1800),
                emptyList(),
            ),
            TapCase(
                "a gap of exactly 500 ms still counts, the limit is inclusive",
                listOf(0L, 500, 1000, 1500, 2000),
                listOf(5),
            ),
            TapCase(
                "after unlocking the count restarts, a sixth tap is not a second unlock",
                listOf(0L, 100, 200, 300, 400, 500, 600, 700, 800, 900),
                listOf(5, 10),
            ),
        )
    }
}
