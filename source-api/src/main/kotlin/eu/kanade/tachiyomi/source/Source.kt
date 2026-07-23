package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 * Maintained for backward compatibility with Tachiyomi / Mihon extensions.
 */
interface Source {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean
        get() = true

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): FilterList = FilterList()
}
