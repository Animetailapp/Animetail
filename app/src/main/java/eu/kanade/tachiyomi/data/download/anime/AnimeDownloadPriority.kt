package eu.kanade.tachiyomi.data.download.anime

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Where episodes queued on the user's behalf (by the library update, or ahead of the episode being
 * watched) go in the download queue: the next episode of a show being watched first, then the
 * other episodes of shows being watched, then the backlog.
 *
 * @param activity the shows' watch history, by anime id.
 * @param now the current time, in epoch milliseconds.
 */
class AnimeDownloadPriority(
    private val activity: Map<Long, WatchActivity>,
    private val now: Long,
    private val activeWindow: Duration = ACTIVE_WINDOW,
) {

    /**
     * @param lastWatchedAt when an episode of the show was last watched, in epoch milliseconds.
     * @param upNextEpisodeId the episode to watch next: the last watched one if unfinished, else
     * the one after it.
     */
    data class WatchActivity(val lastWatchedAt: Long, val upNextEpisodeId: Long?)

    data class Item(val animeId: Long, val episodeId: Long)

    /** In priority order. */
    enum class Tier { UP_NEXT, WATCHING, BACKLOG }

    fun tier(item: Item): Tier {
        val activity = activeShow(item.animeId) ?: return Tier.BACKLOG
        return if (activity.upNextEpisodeId == item.episodeId) Tier.UP_NEXT else Tier.WATCHING
    }

    private fun activeShow(animeId: Long): WatchActivity? {
        return activity[animeId]?.takeIf { now - it.lastWatchedAt <= activeWindow.inWholeMilliseconds }
    }

    /**
     * Returns [queue] with [added] inserted, each before the first waiting item of a lower tier, so
     * the existing items keep their order. Nothing goes before an item that is downloading, nor
     * before the items ahead of it, which would interrupt the download. Among the added items of a
     * tier, those of the shows watched more recently go first, then they keep their order.
     *
     * @param item the anime and episode of a queue item.
     * @param isDownloading whether a queue item is being downloaded.
     */
    fun <T> insert(queue: List<T>, added: List<T>, item: (T) -> Item, isDownloading: (T) -> Boolean): List<T> {
        val result = queue.toMutableList()
        val firstMovable = queue.indexOfLast(isDownloading) + 1
        added
            .sortedWith(
                compareBy<T> { tier(item(it)) }
                    .thenByDescending { activeShow(item(it).animeId)?.lastWatchedAt ?: 0L },
            )
            .forEach { new ->
                val tier = tier(item(new))
                val index = (firstMovable..<result.size)
                    .firstOrNull { tier(item(result[it])) > tier }
                    ?: result.size
                result.add(index, new)
            }
        return result
    }

    companion object {
        /** How recently a show must have been watched to count as being watched. */
        val ACTIVE_WINDOW = 14.days
    }
}
