package tachiyomi.domain.download.anime.model

/**
 * A show's own download cleanup settings. A null field follows the global setting.
 *
 * @property deleteAfterSeen true to delete episodes once they are seen, false to never delete them
 * automatically (also not to stay under the storage limit).
 * @property keepUnseen how many of the next unseen episodes to keep downloaded, 0 for none.
 */
data class AnimeDownloadPolicyOverride(
    val animeId: Long,
    val deleteAfterSeen: Boolean?,
    val keepUnseen: Int?,
) {
    val isDefault: Boolean
        get() = deleteAfterSeen == null && keepUnseen == null
}

/**
 * The global download settings that cleanup is based on, as set in Settings > Downloads.
 *
 * @property removeAfterSeenSlots "Delete after watching" in the player: -1 when off, otherwise how
 * many of the seen episodes before the one just watched to keep (0 deletes the one just watched).
 * @property removeAfterMarkedAsSeen "Delete after manually marked as seen".
 * @property keepUnseen "Keep the next unseen episodes downloaded", 0 when off.
 * @property deleteBookmarked "Allow deleting bookmarked episodes".
 * @property downloadFillers whether filler-marked episodes are downloaded.
 * @property excludedCategoryIds categories whose seen episodes are never deleted automatically.
 */
data class GlobalDownloadPolicy(
    val removeAfterSeenSlots: Int,
    val removeAfterMarkedAsSeen: Boolean,
    val keepUnseen: Int,
    val deleteBookmarked: Boolean,
    val downloadFillers: Boolean,
    val excludedCategoryIds: Set<Long>,
)

/**
 * The download cleanup policy that applies to one show: the global settings with the show's
 * override on top.
 *
 * @property deleteAfterSeen whether seen episodes are deleted.
 * @property keepSeen how many of the last seen episodes stay downloaded when [deleteAfterSeen].
 * @property playerRemoveAfterSeenSlots the slot the player deletes after watching, -1 for none.
 * @property deleteOnMarkedSeen whether marking episodes as seen deletes them.
 * @property keepUnseen how many of the next unseen episodes to keep downloaded.
 * @property protected never delete this show's episodes automatically, not even for the storage
 * limit: the show is set to never delete, or is in an excluded category.
 */
data class AnimeDownloadPolicy(
    val deleteAfterSeen: Boolean,
    val keepSeen: Int,
    val playerRemoveAfterSeenSlots: Int,
    val deleteOnMarkedSeen: Boolean,
    val keepUnseen: Int,
    val protected: Boolean,
    val deleteBookmarked: Boolean,
    val downloadFillers: Boolean,
) {
    companion object {
        fun resolve(
            global: GlobalDownloadPolicy,
            override: AnimeDownloadPolicyOverride?,
            categoryIds: Collection<Long>,
        ): AnimeDownloadPolicy {
            // Like AnimeDownloadManager.getEpisodesToDelete, an entry without categories is in
            // the default category (0)
            val inExcludedCategory = categoryIds.ifEmpty { listOf(0L) }.any { it in global.excludedCategoryIds }
            val showSetting = override?.deleteAfterSeen
            val keepSeen = global.removeAfterSeenSlots.coerceAtLeast(0)
            val deleteAfterSeen = !inExcludedCategory &&
                (showSetting ?: (global.removeAfterSeenSlots >= 0 || global.removeAfterMarkedAsSeen))
            return AnimeDownloadPolicy(
                deleteAfterSeen = deleteAfterSeen,
                keepSeen = keepSeen,
                playerRemoveAfterSeenSlots = when (showSetting) {
                    null -> global.removeAfterSeenSlots
                    true -> keepSeen
                    false -> -1
                },
                deleteOnMarkedSeen = showSetting ?: global.removeAfterMarkedAsSeen,
                keepUnseen = (override?.keepUnseen ?: global.keepUnseen).coerceAtLeast(0),
                protected = showSetting == false || inExcludedCategory,
                deleteBookmarked = global.deleteBookmarked,
                downloadFillers = global.downloadFillers,
            )
        }
    }
}
