package eu.kanade.tachiyomi.data.opdetect

import android.content.Context
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.segment.interactor.SetEpisodeSegments
import tachiyomi.domain.items.episode.segment.model.EpisodeSegment
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.tail.TLMR
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * Detects the openings and endings of an anime's downloaded episodes (see [OpeningEndingDetection])
 * and stores them as [EpisodeSegment.Source.Detected] segments, which the player uses when the
 * source and AniSkip have none.
 *
 * Runs after downloads finish, and from the anime's menu. Automatic runs wait while the downloader
 * is running, so they don't compete with FFmpeg downloads for CPU and storage.
 */
class OpeningEndingDetectionJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var preferences: AdvancedPlayerPreferences

    @Inject private lateinit var getAnime: GetAnime

    @Inject private lateinit var getEpisodesByAnimeId: GetEpisodesByAnimeId

    @Inject private lateinit var sourceManager: AnimeSourceManager

    @Inject private lateinit var downloadManager: AnimeDownloadManager

    @Inject private lateinit var setEpisodeSegments: SetEpisodeSegments

    @Inject private lateinit var audioReader: EpisodeAudioReader

    override suspend fun doWork(): Result {
        graph.inject(this)
        if (!preferences.detectOpeningsEndings().get()) return Result.success()

        val animeId = inputData.getLong(KEY_ANIME_ID, -1)
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        return try {
            withIOContext { detect(animeId, manual) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OP/ED detection failed for anime $animeId" }
            if (manual) notify(e.message ?: e.javaClass.simpleName)
            Result.failure()
        }
    }

    private suspend fun detect(animeId: Long, manual: Boolean): Result {
        if (!manual && downloadManager.isRunning) return Result.retry()

        val startedAt = System.currentTimeMillis()
        val anime = getAnime.await(animeId) ?: return Result.success()
        val source = sourceManager.getOrStub(anime.source)
        val episodes = getEpisodesByAnimeId.await(animeId)
            .filter { downloadManager.isEpisodeDownloaded(it.name, it.scanlator, it.url, anime.title, anime.source) }
            .sortedWith(compareBy<Episode> { it.episodeNumber }.thenByDescending { it.sourceOrder })
        if (episodes.size < 2) {
            if (manual) notify(context.stringResource(TLMR.strings.op_ed_detection_not_enough))
            return Result.success()
        }

        val files = mutableMapOf<Long, Long>()
        val audio = episodes.map { episode ->
            if (!manual && downloadManager.isRunning) return Result.retry()
            val file = try {
                UniFile.fromUri(context, downloadManager.buildVideo(source, anime, episode).videoUrl.toUri())
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "OP/ED detection: no file for episode ${episode.id}" }
                null
            }
            file?.let {
                files[episode.id] = it.length()
                audioReader.read(animeId, episode.id, it)
            }
        }
        audioReader.prune(animeId, files)

        val detected = withContext(Dispatchers.Default) {
            OpeningEndingDetection.detect(audio.map { it ?: EpisodeAudio(0.0, null, null) })
        }

        val now = System.currentTimeMillis()
        episodes.forEachIndexed { i, episode ->
            val fileSize = files[episode.id] ?: return@forEachIndexed
            if (audio[i] == null) return@forEachIndexed
            val segments = detected[i].map {
                EpisodeSegment(
                    episodeId = episode.id,
                    type = it.type,
                    source = EpisodeSegment.Source.Detected,
                    startMs = (it.startSeconds * 1000).roundToLong(),
                    endMs = (it.endSeconds * 1000).roundToLong(),
                    fileSize = fileSize,
                    updatedAt = now,
                )
            }
            setEpisodeSegments.await(episode.id, EpisodeSegment.Source.Detected, segments)
        }

        val openings = detected.count { list -> list.any { it.type == EpisodeSegment.Type.Opening } }
        val endings = detected.count { list -> list.any { it.type == EpisodeSegment.Type.Ending } }
        // Episodes whose start or end couldn't be decoded, as opposed to ones with nothing found
        val unreadable = audio.count { it?.head == null || it.tail == null }
        logcat(LogPriority.INFO) {
            "OP/ED detection for anime $animeId: openings in $openings, endings in $endings of " +
                "${episodes.size}, $unreadable unreadable, in ${System.currentTimeMillis() - startedAt} ms"
        }
        if (manual) {
            val message = when {
                episodes.size - unreadable < 2 ->
                    context.stringResource(TLMR.strings.op_ed_detection_unreadable, unreadable, episodes.size)

                unreadable > 0 ->
                    context.stringResource(
                        TLMR.strings.op_ed_detection_result_some_unreadable,
                        openings,
                        endings,
                        episodes.size,
                        unreadable,
                    )

                else -> context.stringResource(TLMR.strings.op_ed_detection_result, openings, endings, episodes.size)
            }
            notify(message)
        }
        return Result.success()
    }

    private suspend fun notify(message: String) {
        withUIContext { context.toast(message) }
    }

    companion object {
        private const val TAG = "OpeningEndingDetection"
        private const val KEY_ANIME_ID = "anime_id"
        private const val KEY_MANUAL = "manual"

        /** Waits this long after a download so a batch of downloads is analysed once. */
        private const val AFTER_DOWNLOAD_DELAY_MINUTES = 2L

        /** Retry interval while the downloader is running. */
        private const val BUSY_RETRY_MINUTES = 10L

        fun isEnabled(context: Context): Boolean =
            context.metroGraph<AppGraph>().advancedPlayerPreferences.detectOpeningsEndings().get()

        /** Schedules a low-priority run for [animeId] after one of its episodes was downloaded. */
        fun onEpisodeDownloaded(context: Context, animeId: Long) {
            if (!isEnabled(context)) return
            enqueue(context, animeId, manual = false)
        }

        /** Runs detection for [animeId] now and reports the result in a toast. */
        fun startNow(context: Context, animeId: Long) {
            context.toast(context.stringResource(TLMR.strings.op_ed_detection_started))
            enqueue(context, animeId, manual = true)
        }

        private fun enqueue(context: Context, animeId: Long, manual: Boolean) {
            val request = OneTimeWorkRequestBuilder<OpeningEndingDetectionJob>()
                .addTag(TAG)
                .setInputData(workDataOf(KEY_ANIME_ID to animeId, KEY_MANUAL to manual))
                .setBackoffCriteria(BackoffPolicy.LINEAR, BUSY_RETRY_MINUTES, TimeUnit.MINUTES)
                .apply {
                    if (!manual) {
                        setInitialDelay(AFTER_DOWNLOAD_DELAY_MINUTES, TimeUnit.MINUTES)
                        setConstraints(Constraints(requiresBatteryNotLow = true, requiresStorageNotLow = true))
                    }
                }
                .build()
            // One run per anime: a newer request replaces a pending or running one, which resumes
            // from the cached fingerprints.
            context.workManager.enqueueUniqueWork("$TAG:$animeId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
