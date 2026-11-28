package tachiyomi.domain.download.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.download.anime.model.AnimeDownloadPolicy
import tachiyomi.domain.download.anime.model.AnimeDownloadPolicyOverride
import tachiyomi.domain.download.anime.model.GlobalDownloadPolicy
import tachiyomi.domain.download.anime.repository.AnimeDownloadPolicyRepository
import tachiyomi.domain.download.service.DownloadPreferences

/**
 * The download cleanup policy of a show: the settings in Settings > Downloads, with the show's own
 * override from the storage screen on top.
 */
@Inject
class GetAnimeDownloadPolicy(
    private val repository: AnimeDownloadPolicyRepository,
    private val preferences: DownloadPreferences,
    private val getCategories: GetAnimeCategories,
) {

    suspend fun await(animeId: Long): AnimeDownloadPolicy {
        return AnimeDownloadPolicy.resolve(
            global = global(),
            override = awaitOverride(animeId),
            categoryIds = getCategories.await(animeId).map { it.id },
        )
    }

    suspend fun awaitOverride(animeId: Long): AnimeDownloadPolicyOverride? {
        return try {
            repository.getByAnimeId(animeId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    /** Every show's override, by anime id. */
    suspend fun awaitOverrides(): Map<Long, AnimeDownloadPolicyOverride> {
        return try {
            repository.getAll().associateBy { it.animeId }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyMap()
        }
    }

    fun subscribeOverride(animeId: Long): Flow<AnimeDownloadPolicyOverride?> {
        return repository.subscribeByAnimeId(animeId)
    }

    fun global(): GlobalDownloadPolicy {
        return GlobalDownloadPolicy(
            removeAfterSeenSlots = preferences.removeAfterReadSlots.get(),
            removeAfterMarkedAsSeen = preferences.removeAfterMarkedAsRead.get(),
            keepUnseen = preferences.keepUnseenEpisodesDownloaded.get(),
            deleteBookmarked = preferences.removeBookmarkedChapters.get(),
            downloadFillers = preferences.downloadFillermarkedItems.get(),
            excludedCategoryIds = preferences.removeExcludeAnimeCategories.get()
                .mapNotNullTo(HashSet()) { it.toLongOrNull() },
        )
    }
}
