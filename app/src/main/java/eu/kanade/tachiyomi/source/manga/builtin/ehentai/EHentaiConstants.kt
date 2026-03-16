package eu.kanade.tachiyomi.source.manga.builtin.ehentai

internal object EHentaiConstants {

    const val BASE_URL = "https://e-hentai.org"
    const val EX_URL = "https://exhentai.org"
    const val API_URL = "https://api.e-hentai.org/api.php"
    const val FORUMS_URL = "https://forums.e-hentai.org"

    // ── Category bitmasks (f_cats = excluded categories, 0 = show all) ─────────
    const val CAT_MISC = 1
    const val CAT_DOUJINSHI = 2
    const val CAT_MANGA = 4
    const val CAT_ARTIST_CG = 8
    const val CAT_GAME_CG = 16
    const val CAT_IMAGE_SET = 32
    const val CAT_COSPLAY = 64
    const val CAT_ASIAN_PORN = 128
    const val CAT_NON_H = 256
    const val CAT_WESTERN = 512
    const val CAT_ALL = 0

    /** Bitmask with all 10 categories set (= max value). */
    const val CAT_FULL_MASK = 1023

    /** Maps bitmask → display name, ordered as they appear in the filter UI. */
    val CATEGORY_NAMES: LinkedHashMap<Int, String> = linkedMapOf(
        CAT_DOUJINSHI to "Doujinshi",
        CAT_MANGA to "Manga",
        CAT_ARTIST_CG to "Artist CG",
        CAT_GAME_CG to "Game CG",
        CAT_WESTERN to "Western",
        CAT_NON_H to "Non-H",
        CAT_IMAGE_SET to "Image Set",
        CAT_COSPLAY to "Cosplay",
        CAT_ASIAN_PORN to "Asian Porn",
        CAT_MISC to "Misc",
    )

    // ── Language tags used in tag-namespace filtering ─────────────────────────
    val LANGUAGES: List<String> = listOf(
        "japanese",
        "english",
        "chinese",
        "dutch",
        "french",
        "german",
        "hungarian",
        "italian",
        "korean",
        "polish",
        "portuguese",
        "russian",
        "spanish",
        "thai",
        "vietnamese",
        "translated",
        "rewrite",
    )

    // ── Sort order codes ───────────────────────────────────────────────────────
    const val SORT_NEWEST = 0
    const val SORT_POPULAR_TODAY = 1
    const val SORT_POPULAR_WEEK = 2
    const val SORT_POPULAR_MONTH = 3
    const val SORT_POPULAR_ALL = 4

    /** f_stor values corresponding to the sort constants above. */
    val SORT_PARAMS = mapOf(
        SORT_NEWEST to "",
        SORT_POPULAR_TODAY to "pt",
        SORT_POPULAR_WEEK to "pw",
        SORT_POPULAR_MONTH to "pm",
        SORT_POPULAR_ALL to "pa",
    )

    // ── Minimum rating filter (number of stars) ────────────────────────────────
    // f_srdd = minimum star counts as actual integer strings "2".."5"
    val RATING_FILTER_VALUES = listOf("", "2", "3", "4", "5")
}
