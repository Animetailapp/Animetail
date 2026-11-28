package eu.kanade.tachiyomi.data.download.anime.model

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * One entry of the download queue. Entries are compared by identity: [video] and the other state
 * change while it downloads, and the downloader keys its running jobs by entry, so a value-based
 * equality made a download with a newly picked video look like another one and start it again.
 */
class AnimeDownload(
    val source: AnimeHttpSource,
    val anime: Anime,
    val episode: Episode,
    val changeDownloader: Boolean = false,
    var video: Video? = null,
) : ProgressListener {

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    @Transient
    private val progressStateFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = progressStateFlow.asStateFlow()
    var progress: Int
        get() = progressStateFlow.value
        set(value) {
            progressStateFlow.value = value
        }

    @Transient
    private val transferStateFlow = MutableStateFlow<Transfer?>(null)

    /**
     * The latest raw progress report of the running download attempt, or null before its first
     * report. Each attempt starts over from null. The download queue derives speed and time left
     * from consecutive reports.
     */
    @Transient
    val transferFlow = transferStateFlow.asStateFlow()
    var transfer: Transfer?
        get() = transferStateFlow.value
        set(value) {
            transferStateFlow.value = value
        }

    /**
     * Cumulative progress of one download attempt at [atMs] (`SystemClock.elapsedRealtime`).
     * [totalBytes] and [durationMs] are null when unknown, e.g. HLS has no total size and a stream
     * without a known duration has neither.
     */
    data class Transfer(
        val atMs: Long,
        val bytes: Long,
        val totalBytes: Long?,
        val mediaMs: Long,
        val durationMs: Long?,
    )

    @Transient
    private val activeStateFlow = MutableStateFlow(false)

    /**
     * Whether the downloader is working on this download, including the time spent finding a
     * working video before it starts transferring (status is still QUEUE then).
     */
    @Transient
    val activeFlow = activeStateFlow.asStateFlow()
    var active: Boolean
        get() = activeStateFlow.value
        set(value) {
            activeStateFlow.value = value
        }

    /**
     * Updates the status of the download
     *
     * @param bytesRead the updated TOTAL number of bytes read (not a partial increment)
     * @param contentLength the updated content length
     * @param done whether progress has completed or not
     */
    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        val newProgress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
        if (progress != newProgress) progress = newProgress
    }

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }

    companion object {
        suspend fun fromEpisodeId(
            episodeId: Long,
            getEpisode: GetEpisode,
            getAnimeById: GetAnime,
            sourceManager: AnimeSourceManager,
        ): AnimeDownload? {
            val episode = getEpisode.await(episodeId) ?: return null
            val anime = getAnimeById.await(episode.animeId) ?: return null
            val source = sourceManager.get(anime.source) as? AnimeHttpSource ?: return null

            return AnimeDownload(source, anime, episode)
        }
    }
}
