package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.isCharging
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.R
import tachiyomi.i18n.tail.TLMR
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * This worker is used to manage the downloader. The system can decide to stop the worker, in
 * which case the downloader is also stopped. Downloads the device conditions hold back (no network,
 * "Only on Wi-Fi", the auto-download restrictions) stay queued; when nothing else is left, the
 * downloader stops and this worker is scheduled again for when the conditions are met.
 */
class AnimeDownloadJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var downloadManager: AnimeDownloadManager

    @Inject private lateinit var downloadPreferences: DownloadPreferences

    init {
        graph.inject(this)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(applicationContext.getString(R.string.download_notifier_downloader_title))
            setSmallIcon(android.R.drawable.stat_sys_download)
        }.build()
        return ForegroundInfo(
            Notifications.ID_DOWNLOAD_EPISODE_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        // After the app is updated or killed, WorkManager runs this job again before the queue is
        // restored. Starting with the queue still empty would end the job and leave it paused.
        downloadManager.awaitQueueRestored()

        // Before starting, so that the downloader leaves the held downloads queued
        updateHolds()
        if (!downloadManager.downloaderStart()) return Result.failure()

        // Again now that starting queued the failed downloads again
        var held = updateHolds()
        try {
            // Otherwise nothing can download now: wait for the device to meet the requirements
            if (held == null) setForegroundSafely()

            while (held == null && downloadManager.isRunning) {
                delay(1.seconds)
                held = updateHolds()
            }
        } finally {
            // Stopped by the system, e.g. because the charger was unplugged: WorkManager runs this
            // work again by itself, so the downloads only have to be held, not paused
            if (isStopped && held == null) held = updateHolds()
            if (downloadManager.isRunning && (held != null || isStopped)) {
                downloadManager.downloaderStop(held?.reason())
            }
        }

        if (held != null && !isStopped) scheduleResume()
        return Result.success()
    }

    private fun conditions() = AnimeDownloadConditions(
        onlyOnWifi = downloadPreferences.downloadOnlyOverWifi.get(),
        autoDownloadRestrictions = downloadPreferences.autoDownloadDeviceRestrictions.get(),
    )

    /**
     * Tells the downloader which downloads the device conditions hold back.
     *
     * @return why none of the pending downloads can run, or null if one can, or none is pending.
     */
    private fun updateHolds(): AnimeDownloadConditions.Hold? {
        val network = applicationContext.activeNetworkState()
        val state = AnimeDownloadConditions.DeviceState(
            isOnline = network.isOnline,
            isWifi = network.isWifi,
            isUnmetered = network.isUnmetered,
            isCharging = applicationContext.isCharging(),
        )
        val holds = conditions().holds(state)
        downloadManager.downloaderSetHolds(holds)

        val pending = downloadManager.pendingDownloads()
        if (pending.isEmpty() || pending.any { holds.of(it.automatic) == null }) return null
        // A manual download needs the least, so its hold is the one to report
        return holds.of(automatic = pending.all { it.automatic })
    }

    private fun AnimeDownloadConditions.Hold.reason(): String = when (this) {
        AnimeDownloadConditions.Hold.NO_NETWORK -> applicationContext.getString(R.string.download_notifier_no_network)

        AnimeDownloadConditions.Hold.NO_WIFI -> applicationContext.getString(R.string.download_notifier_text_only_wifi)

        AnimeDownloadConditions.Hold.METERED_NETWORK -> applicationContext.stringResource(
            TLMR.strings.download_held_metered,
        )

        AnimeDownloadConditions.Hold.NOT_CHARGING -> applicationContext.stringResource(
            TLMR.strings.download_held_not_charging,
        )
    }

    /**
     * Runs the downloader again once the device meets the requirements of the first held download
     * that could run. The new work follows this one, so pausing or starting the downloads replaces
     * it as usual.
     */
    private fun scheduleResume() {
        val pending = downloadManager.pendingDownloads()
        val requirements = conditions().resumeRequirements(pending.map { it.automatic }) ?: return
        val request = OneTimeWorkRequestBuilder<AnimeDownloadJob>()
            .addTag(TAG)
            .setConstraints(requirements.toConstraints())
            // Guards against a busy loop where the system's view of the network differs from ours
            .setInitialDelay(RESUME_DELAY.inWholeSeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        logcat(LogPriority.INFO) { "Downloads held back, resuming when the device meets $requirements" }
    }

    private fun AnimeDownloadConditions.Requirements.toConstraints(): Constraints {
        val networkType = if (unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED
        val networkRequest = NetworkRequest.Builder().apply {
            if (wifi) addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            if (unmetered) addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        return Constraints.Builder()
            // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
            .setRequiredNetworkRequest(networkRequest.build(), networkType)
            .setRequiresCharging(charging)
            .build()
    }

    companion object {
        private const val TAG = "AnimeDownloader"
        private val RESUME_DELAY = 15.seconds

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<AnimeDownloadJob>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * Checks the held downloads again now, e.g. after a restriction was turned off. Does nothing
         * unless the downloads are waiting for the device to meet the requirements.
         */
        fun retryHeld(context: Context) {
            val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(TAG)
            infos.addListener(
                {
                    val list = infos.get()
                    val waiting = list.any { it.state == WorkInfo.State.ENQUEUED }
                    if (waiting && list.none { it.state == WorkInfo.State.RUNNING }) start(context)
                },
                Runnable::run,
            )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TAG)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .let { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }
    }
}
