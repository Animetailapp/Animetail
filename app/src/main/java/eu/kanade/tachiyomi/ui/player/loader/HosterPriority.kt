package eu.kanade.tachiyomi.ui.player.loader

/**
 * User defined hoster order and disabled hosters, both stored as comma separated names.
 *
 * Priority entries are keywords such as `"Voe, Byse"`: earlier keywords win and anything that
 * matches no keyword keeps the order chosen by the extension after the matched entries.
 * Disabled entries are full hoster names, compared ignoring case.
 */
class HosterPriority(rawKeywords: String, rawDisabled: String = "") {

    private val keywords = parseNames(rawKeywords)
    private val disabled = parseNames(rawDisabled)

    val isEmpty: Boolean get() = keywords.isEmpty() && disabled.isEmpty()

    /**
     * Stable sort of [items] by the first keyword contained (case-insensitively) in [name].
     * Disabled items always go last.
     */
    fun <T> sort(items: List<T>, name: (T) -> String): List<T> {
        if (isEmpty) return items
        return items.sortedWith(
            compareBy<T> { isDisabled(name(it)) }.thenBy { rank(name(it)) },
        )
    }

    fun isDisabled(name: String): Boolean = disabled.any { it.equals(name, ignoreCase = true) }

    private fun rank(name: String): Int {
        val index = keywords.indexOfFirst { name.contains(it, ignoreCase = true) }
        return if (index == -1) keywords.size else index
    }

    companion object {
        fun parseNames(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
