package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.TimeUnit

/**
 * Keeps the anime downloads under the storage limit after episodes were downloaded, see
 * [AnimeStorageCleaner.enforceStorageLimit].
 */
class AnimeStorageLimitJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject lateinit var cleaner: AnimeStorageCleaner

    override suspend fun doWork(): Result {
        graph.inject(this)
        return try {
            withIOContext { cleaner.enforceStorageLimit() }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to keep the anime downloads under the storage limit" }
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "AnimeStorageLimit"

        /** Waits this long after a download so a batch of downloads is checked once. */
        private const val AFTER_DOWNLOAD_DELAY_SECONDS = 30L

        fun onEpisodeDownloaded(context: Context) {
            if (context.metroGraph<AppGraph>().downloadPreferences.animeDownloadStorageLimitGb.get() <= 0) return
            val request = OneTimeWorkRequestBuilder<AnimeStorageLimitJob>()
                .addTag(TAG)
                .setInitialDelay(AFTER_DOWNLOAD_DELAY_SECONDS, TimeUnit.SECONDS)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
