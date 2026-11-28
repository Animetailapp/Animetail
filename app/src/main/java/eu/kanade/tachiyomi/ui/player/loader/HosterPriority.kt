package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.model.Video

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

    /**
     * Sorts videos of an extension without hosters: keywords match the whole title, so a quality
     * can also be preferred, while disabled entries are server names (see [serverName]).
     */
    fun sortVideosWithoutHosters(videos: List<Video>): List<Video> =
        sort(videos) { it.videoTitle }.sortedBy { isDisabled(serverName(it.videoTitle)) }

    fun isDisabled(name: String): Boolean = disabled.any { it.equals(name, ignoreCase = true) }

    private fun rank(name: String): Int {
        val index = keywords.indexOfFirst { name.contains(it, ignoreCase = true) }
        return if (index == -1) keywords.size else index
    }

    companion object {
        fun parseNames(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        private val SERVER_SEPARATOR = Regex(""":| - |\(|\[|\|""")
        private val TRAILING_QUALITY = Regex("""\s+\d{3,4}p\b.*$""", RegexOption.IGNORE_CASE)

        /**
         * Server of a video from an extension without hosters, which puts it in the video title,
         * like "Okru" in "Okru:1080p" or "StreamWish - 720p".
         */
        fun serverName(videoTitle: String): String {
            val server = videoTitle.split(SERVER_SEPARATOR, limit = 2).first().replace(TRAILING_QUALITY, "").trim()
            return server.ifEmpty { videoTitle.trim() }
        }

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
