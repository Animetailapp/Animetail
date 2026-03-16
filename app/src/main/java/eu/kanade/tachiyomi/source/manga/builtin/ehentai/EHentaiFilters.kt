package eu.kanade.tachiyomi.source.manga.builtin.ehentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getEHentaiFilters(): FilterList = FilterList(
    CategoryGroup(),
    SortOrderFilter(),
    RatingFilter(),
    LanguageGroup(),
    TagSearchFilter(),
    UploaderFilter(),
    MinPagesFilter(),
    MaxPagesFilter(),
)

// ── Categories ────────────────────────────────────────────────────────────────

class CategoryGroup : Filter.Group<CategoryFilter>(
    "Categories",
    EHentaiConstants.CATEGORY_NAMES.entries.map { CategoryFilter(it.value, it.key) },
)

class CategoryFilter(name: String, val bitmask: Int) : Filter.CheckBox(name, true)

// ── Sort order ────────────────────────────────────────────────────────────────

class SortOrderFilter : Filter.Select<String>(
    "Sort order",
    arrayOf("Newest", "Popular (today)", "Popular (week)", "Popular (month)", "Popular (all-time)"),
)

// ── Star rating ───────────────────────────────────────────────────────────────

class RatingFilter : Filter.Select<String>(
    "Minimum rating",
    arrayOf("Any", "2 stars", "3 stars", "4 stars", "5 stars"),
)

// ── Language filter ───────────────────────────────────────────────────────────

class LanguageGroup : Filter.Group<LanguageCheckBox>(
    "Languages (tag filter)",
    EHentaiConstants.LANGUAGES.map { lang ->
        LanguageCheckBox(lang.replaceFirstChar { it.uppercase() }, lang)
    },
)

class LanguageCheckBox(name: String, val langTag: String) : Filter.CheckBox(name, false)

// ── Text search extras ────────────────────────────────────────────────────────

/**
 * Extra search terms appended as `f_search=<value>`.
 * Supports E-Hentai tag syntax, e.g. `artist:foo language:english`.
 */
class TagSearchFilter : Filter.Text("Search tags (e.g. artist:foo)")

/**
 * Filters results by uploader name (prepended as `uploader:<value>`).
 */
class UploaderFilter : Filter.Text("Uploader")

// ── Page count ────────────────────────────────────────────────────────────────

class MinPagesFilter : Filter.Text("Minimum pages")
class MaxPagesFilter : Filter.Text("Maximum pages")
