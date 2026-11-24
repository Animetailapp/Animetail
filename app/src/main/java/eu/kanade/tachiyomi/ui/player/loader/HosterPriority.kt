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

        /**
         * Applies the order picked for one episode to the saved order without dropping the names
         * of other sources: the episode's names replace the first of them in [savedOrder], or go
         * first if none was saved.
         *
         * @param episodeOrder every hoster name of the episode, highest priority first.
         */
        fun mergeOrder(savedOrder: List<String>, episodeOrder: List<String>): List<String> {
            val others = savedOrder.filterNot { it.isIn(episodeOrder) }
            val insertAt = savedOrder.indexOfFirst { it.isIn(episodeOrder) }.coerceAtLeast(0)
            return others.take(insertAt) + episodeOrder + others.drop(insertAt)
        }

        /**
         * Replaces the disabled state of the episode's hosters, keeping the rest of [savedDisabled].
         */
        fun mergeDisabled(
            savedDisabled: List<String>,
            episodeNames: List<String>,
            episodeDisabled: Set<String>,
        ): List<String> = savedDisabled.filterNot { it.isIn(episodeNames) } + episodeDisabled

        private fun String.isIn(names: List<String>) = names.any { it.equals(this, ignoreCase = true) }
    }
}
