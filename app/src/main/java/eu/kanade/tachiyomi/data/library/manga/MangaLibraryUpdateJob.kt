package eu.kanade.tachiyomi.data.library.manga

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import aniyomi.util.nullIfBlank
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.NoChaptersException
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.manga.model.MangaGroupLibraryMode
import tachiyomi.domain.library.manga.model.MangaLibraryGroup
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.source.manga.model.SourceNotInstalledException
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MangaLibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: MangaSourceManager = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val downloadManager: MangaDownloadManager = Injekt.get()
    private val coverCache: MangaCoverCache = Injekt.get()
    private val getLibraryManga: GetLibraryManga = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val updateManga: UpdateManga = Injekt.get()
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get()
    private val getTracks: GetMangaTracks = Injekt.get()
    private val mangaFetchInterval: MangaFetchInterval = Injekt.get()
    private val filterChaptersForDownload: FilterChaptersForDownload = Injekt.get()

    private val notifier = MangaLibraryUpdateNotifier(context)

    private var mangaToUpdate: List<LibraryManga> = mutableListOf()

    override suspend fun doWork(): Result {
        if (tags.contains(WORK_NAME_AUTO)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val preferences = Injekt.get<LibraryPreferences>()
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                if ((DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                    return Result.retry()
                }
            }

            // Find a running manual worker. If exists, try again later
            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }
        }

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
        }

        libraryPreferences.lastUpdatedTimestamp().set(Instant.now().toEpochMilli())

        val categoryId = inputData.getLong(KEY_CATEGORY, -1L)
        // SY -->
        val group = inputData.getInt(KEY_GROUP, MangaLibraryGroup.BY_DEFAULT)
        val groupExtra = inputData.getString(KEY_GROUP_EXTRA)
        // SY <--
        addMangaToQueue(categoryId, group, groupExtra)

        return withIOContext {
            try {
                updateChapterList()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = MangaLibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    /**
     * Adds list of manga to be updated.
     *
     * @param categoryId the ID of the category to update, or -1 if no category specified.
     */
    @Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod", "ComplexCondition")
    private suspend fun addMangaToQueue(categoryId: Long, group: Int, groupExtra: String?) {
        val libraryManga = getLibraryManga.await()

        // SY -->
        val groupMangaLibraryUpdateType = libraryPreferences.groupMangaLibraryUpdateType().get()
        // SY <--

        val listToUpdate = if (categoryId != -1L) {
            libraryManga.filter { it.category == categoryId }
        } else if (
            group == MangaLibraryGroup.BY_DEFAULT ||
            groupMangaLibraryUpdateType == MangaGroupLibraryMode.GLOBAL ||
            (
                groupMangaLibraryUpdateType == MangaGroupLibraryMode.ALL_BUT_UNGROUPED &&
                    group == MangaLibraryGroup.UNGROUPED
                )
        ) {
            val categoriesToUpdate = libraryPreferences.mangaUpdateCategories().get().map { it.toLong() }
            val includedManga = if (categoriesToUpdate.isNotEmpty()) {
                libraryManga.filter { it.category in categoriesToUpdate }
            } else {
                libraryManga
            }

            val categoriesToExclude = libraryPreferences.mangaUpdateCategoriesExclude().get().map { it.toLong() }
            val excludedMangaIds = if (categoriesToExclude.isNotEmpty()) {
                libraryManga.filter { it.category in categoriesToExclude }.map { it.manga.id }
            } else {
                emptyList()
            }

            includedManga
                .filterNot { it.manga.id in excludedMangaIds }
        } else {
            when (group) {
                MangaLibraryGroup.BY_TRACK_STATUS -> {
                    val trackingExtra = groupExtra?.toIntOrNull() ?: -1
                    val tracks = runBlocking { getTracks.await() }.groupBy { it.mangaId }

                    libraryManga.filter { (manga) ->
                        val status = tracks[manga.id]?.firstNotNullOfOrNull { track ->
                            TrackStatus.parseTrackerStatus(track.trackerId, track.status)
                        } ?: TrackStatus.OTHER
                        status.int == trackingExtra.toLong()
                    }
                }
                MangaLibraryGroup.BY_SOURCE -> {
                    val sourceExtra = groupExtra?.nullIfBlank()?.toIntOrNull()
                    val source = libraryManga.map { it.manga.source }
                        .distinct()
                        .sorted()
                        .getOrNull(sourceExtra ?: -1)

                    if (source != null) libraryManga.filter { it.manga.source == source } else emptyList()
                }
                MangaLibraryGroup.BY_TAG -> {
                    val tagExtra = groupExtra?.nullIfBlank()?.toIntOrNull()
                    val tag = libraryManga.map { it.manga.genre }
                        .distinct()
                        .getOrNull(tagExtra ?: -1)
                    if (tag != null) libraryManga.filter { it.manga.genre == tag } else emptyList()
                }
                MangaLibraryGroup.BY_STATUS -> {
                    val statusExtra = groupExtra?.toLongOrNull() ?: -1
                    libraryManga.filter {
                        it.manga.status == statusExtra
                    }
                }
                MangaLibraryGroup.UNGROUPED -> libraryManga
                else -> libraryManga
            }
            // SY <--
        }

        val restrictions = libraryPreferences.autoUpdateItemRestrictions().get()
        val skippedUpdates = mutableListOf<Pair<Manga, String?>>()
        val (_, fetchWindowUpperBound) = mangaFetchInterval.getWindow(ZonedDateTime.now())

        mangaToUpdate = listToUpdate
            // SY -->
            .distinctBy { it.manga.id }
            // SY <--
            .filter {
                when {
                    it.manga.updateStrategy != UpdateStrategy.ALWAYS_UPDATE -> {
                        skippedUpdates.add(
                            it.manga to context.stringResource(MR.strings.skipped_reason_not_always_update),
                        )
                        false
                    }

                    ENTRY_NON_COMPLETED in restrictions && it.manga.status.toInt() == SManga.COMPLETED -> {
                        skippedUpdates.add(
                            it.manga to context.stringResource(MR.strings.skipped_reason_completed),
                        )
                        false
                    }

                    ENTRY_HAS_UNVIEWED in restrictions && it.unreadCount != 0L -> {
                        skippedUpdates.add(
                            it.manga to context.stringResource(MR.strings.skipped_reason_not_caught_up),
                        )
                        false
                    }

                    ENTRY_NON_VIEWED in restrictions && it.totalChapters > 0L && !it.hasStarted -> {
                        skippedUpdates.add(
                            it.manga to context.stringResource(MR.strings.skipped_reason_not_started),
                        )
                        false
                    }

                    ENTRY_OUTSIDE_RELEASE_PERIOD in restrictions && it.manga.nextUpdate > fetchWindowUpperBound -> {
                        skippedUpdates.add(
                            it.manga to context.stringResource(MR.strings.skipped_reason_not_in_release_period),
                        )
                        false
                    }
                    else -> true
                }
            }
            .sortedBy { it.manga.title }

        // Warn when excessively checking a single source
        val maxUpdatesFromSource = mangaToUpdate
            .groupBy { it.manga.source + (0..4).random() }
            .filterKeys { sourceManager.get(it) !is UnmeteredSource }
            .maxOfOrNull { it.value.size } ?: 0
        if (maxUpdatesFromSource > MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD) {
            notifier.showQueueSizeWarningNotificationIfNeeded(mangaToUpdate)
        }

        if (skippedUpdates.isNotEmpty()) {
            // TODO: surface skipped reasons to user?
            logcat {
                skippedUpdates
                    .groupBy { it.second }
                    .map { (reason, entries) -> "$reason: [${entries.map { it.first.title }.sorted().joinToString()}]" }
                    .joinToString()
            }
        }
    }

    /**
     * Method that updates manga in [mangaToUpdate]. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     * For each manga it calls [updateManga] and updates the notification showing the current
     * progress.
     *
     * @return an observable delivering the progress of each update.
     */
    @Suppress("MagicNumber", "LongMethod")
    private suspend fun updateChapterList() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingManga = CopyOnWriteArrayList<Manga>()
        val newUpdates = CopyOnWriteArrayList<Pair<Manga, Array<Chapter>>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Manga, String?>>()
        val hasDownloads = AtomicBoolean(false)
        val fetchWindow = mangaFetchInterval.getWindow(ZonedDateTime.now())

        coroutineScope {
            mangaToUpdate.groupBy { it.manga.source + (0..4).random() }.values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.manga
                                ensureActive()

                                // Don't continue to update if manga is not in library
                                if (getManga.await(manga.id)?.favorite != true) {
                                    return@forEach
                                }

                                withUpdateNotification(
                                    currentlyUpdatingManga,
                                    progressCount,
                                    manga,
                                ) {
                                    try {
                                        val newChapters = updateManga(manga, fetchWindow)
                                            .sortedByDescending { it.sourceOrder }

                                        if (newChapters.isNotEmpty()) {
                                            val chaptersToDownload = filterChaptersForDownload.await(manga, newChapters)
                                            if (chaptersToDownload.isNotEmpty()) {
                                                downloadChapters(manga, chaptersToDownload)
                                                hasDownloads.set(true)
                                            }
                                            libraryPreferences.newMangaUpdatesCount()
                                                .getAndSet { it + newChapters.size }

                                            // Convert to the manga that contains new chapters
                                            newUpdates.add(manga to newChapters.toTypedArray())
                                        }
                                    } catch (e: Throwable) {
                                        val errorMessage = when (e) {
                                            is NoChaptersException -> context.stringResource(
                                                MR.strings.no_chapters_error,
                                            )
                                            // failedUpdates will already have the source, don't need to copy it into the message
                                            is SourceNotInstalledException -> context.stringResource(
                                                MR.strings.loader_not_implemented_error,
                                            )
                                            else -> e.message
                                        }
                                        failedUpdates.add(manga to errorMessage)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
            if (hasDownloads.get()) {
                downloadManager.startDownloads()
            }
        }

        if (failedUpdates.isNotEmpty()) {
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(
                failedUpdates.size,
                errorFile.getUriCompat(context),
            )
        }
    }

    private fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadChapters(manga, chapters, false)
    }

    /**
     * Updates the chapters for the given manga and adds them to the database.
     *
     * @param manga the manga to update.
     * @return a pair of the inserted and removed chapters.
     */
    private suspend fun updateManga(manga: Manga, fetchWindow: Pair<Long, Long>): List<Chapter> {
        val source = sourceManager.getOrStub(manga.source)

        // Update manga metadata if needed
        if (libraryPreferences.autoUpdateMetadata().get()) {
            val networkManga = source.getMangaDetails(manga.toSManga())
            updateManga.awaitUpdateFromSource(manga, networkManga, manualFetch = false, coverCache)
        }

        val chapters = source.getChapterList(manga.toSManga())

        // Get manga from database to account for if it was removed during the update and
        // to get latest data so it doesn't get overwritten later on
        val dbManga = getManga.await(manga.id)?.takeIf { it.favorite } ?: return emptyList()

        return syncChaptersWithSource.await(chapters, dbManga, source, false, fetchWindow)
    }

    private suspend fun withUpdateNotification(
        updatingManga: CopyOnWriteArrayList<Manga>,
        completed: AtomicInteger,
        manga: Manga,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingManga.add(manga)
        notifier.showProgressNotification(
            updatingManga,
            completed.get(),
            mangaToUpdate.size,
        )

        block()

        ensureActive()

        updatingManga.remove(manga)
        completed.getAndIncrement()
        notifier.showProgressNotification(
            updatingManga,
            completed.get(),
            mangaToUpdate.size,
        )
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: List<Pair<Manga, String?>>): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("animetail_update_errors.txt")
                file.bufferedWriter().use { out ->
                    out.write(
                        context.stringResource(MR.strings.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n",
                    )
                    // Error file format:
                    // ! Error
                    //   # Source
                    //     - Manga
                    errors.groupBy({ it.second }, { it.first }).forEach { (error, mangas) ->
                        out.write("\n! ${error}\n")
                        mangas.groupBy { it.source }.forEach { (srcId, mangas) ->
                            val source = sourceManager.getOrStub(srcId)
                            out.write("  # $source\n")
                            mangas.forEach {
                                out.write("    - ${it.title}\n")
                            }
                        }
                    }
                }
                return file
            }
        } catch (_: Exception) {}
        return File("")
    }

    companion object {
        private const val TAG = "LibraryUpdate"
        private const val WORK_NAME_AUTO = "LibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "LibraryUpdate-manual"

        private const val ERROR_LOG_HELP_URL = "https://aniyomi.org/help/guides/troubleshooting"
        private const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        /**
         * Key for category to update.
         */
        private const val KEY_CATEGORY = "category"

        // SY -->
        /**
         * Key for group to update.
         */
        const val KEY_GROUP = "group"
        const val KEY_GROUP_EXTRA = "group_extra"
        // SY <--

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        fun setupTask(
            context: Context,
            prefInterval: Int? = null,
        ) {
            val preferences = Injekt.get<LibraryPreferences>()
            val interval = prefInterval ?: preferences.autoUpdateInterval().get()
            if (interval > 0) {
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                val networkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val networkRequestBuilder = NetworkRequest.Builder()
                if (DEVICE_ONLY_ON_WIFI in restrictions) {
                    networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                }
                if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
                val constraints = Constraints.Builder()
                    // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                    .setRequiredNetworkRequest(networkRequestBuilder.build(), networkType)
                    .setRequiresCharging(DEVICE_CHARGING in restrictions)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<MangaLibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }

        @Suppress("ReturnCount")
        fun startNow(
            context: Context,
            category: Category? = null,
            // SY -->
            group: Int = MangaLibraryGroup.BY_DEFAULT,
            groupExtra: String? = null,
            // SY <--
        ): Boolean {
            val wm = context.workManager
            // Check if the LibraryUpdateJob is already running
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }

            val inputData = workDataOf(
                KEY_CATEGORY to category?.id,
                // SY -->
                KEY_GROUP to group,
                KEY_GROUP_EXTRA to groupExtra,
                // SY <--
            )

            val syncPreferences: SyncPreferences = Injekt.get()

            // Always sync the data before library update if syncing is enabled.
            if (syncPreferences.isSyncEnabled()) {
                // Check if SyncDataJob is already running
                if (SyncDataJob.isRunning(context)) {
                    // SyncDataJob is already running
                    return false
                }

                // Define the SyncDataJob
                val syncDataJob = OneTimeWorkRequestBuilder<SyncDataJob>()
                    .addTag(SyncDataJob.TAG_MANUAL)
                    .build()

                // Chain SyncDataJob to run before LibraryUpdateJob
                val libraryUpdateJob = OneTimeWorkRequestBuilder<MangaLibraryUpdateJob>()
                    .addTag(TAG)
                    .addTag(WORK_NAME_MANUAL)
                    .setInputData(inputData)
                    .build()

                wm.beginUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, syncDataJob)
                    .then(libraryUpdateJob)
                    .enqueue()
            } else {
                val request = OneTimeWorkRequestBuilder<MangaLibraryUpdateJob>()
                    .addTag(TAG)
                    .addTag(WORK_NAME_MANUAL)
                    .setInputData(inputData)
                    .build()

                wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)
            }

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)

                    // Re-enqueue cancelled scheduled work
                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}
