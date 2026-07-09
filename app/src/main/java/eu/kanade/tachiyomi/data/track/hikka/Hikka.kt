package eu.kanade.tachiyomi.data.track.hikka

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableAnimeTracker
import eu.kanade.tachiyomi.data.track.DeletableMangaTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.hikka.dto.HKOAuth
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.injectLazy

class Hikka(id: Long) : BaseTracker(id, "Hikka"), MangaTracker, AnimeTracker, DeletableMangaTracker, DeletableAnimeTracker {

    companion object {
        const val READING = 0L
        const val WATCHING = 0L
        const val COMPLETED = 1L
        const val ON_HOLD = 2L
        const val DROPPED = 3L
        const val PLAN_TO_READ = 4L
        const val PLAN_TO_WATCH = 4L
        const val REREADING = 5L
        const val REWATCHING = 5L

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)
            .toImmutableList()
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { HikkaInterceptor(this) }

    private val api by lazy { HikkaApi(id, client, interceptor) }

    override val supportsReadingDates: Boolean = true

    override fun getLogo(): Int = R.drawable.brand_hikka

    override fun getStatusListManga(): List<Long> {
        return listOf(
            READING,
            COMPLETED,
            ON_HOLD,
            DROPPED,
            PLAN_TO_READ,
            REREADING,
        )
    }

    override fun getStatusListAnime(): List<Long> {
        return listOf(
            WATCHING,
            COMPLETED,
            ON_HOLD,
            DROPPED,
            PLAN_TO_WATCH,
            REWATCHING,
        )
    }

    override fun getStatusForManga(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        PLAN_TO_READ -> MR.strings.plan_to_read
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        REREADING -> MR.strings.repeating
        else -> null
    }

    override fun getStatusForAnime(status: Long): StringResource? = when (status) {
        WATCHING -> AYMR.strings.watching
        PLAN_TO_WATCH -> AYMR.strings.plan_to_watch
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        REWATCHING -> AYMR.strings.repeating_anime
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRereadingStatus(): Long = REREADING

    override fun getRewatchingStatus(): Long = REWATCHING

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun indexToScore(index: Int): Double {
        return index.toDouble()
    }

    override fun displayScore(track: DomainMangaTrack): String {
        return track.score.toInt().toString()
    }

    override fun displayScore(track: DomainAnimeTrack): String {
        return track.score.toInt().toString()
    }

    override fun getLogoColor(): Int = 0xFF2A2C31.toInt()

    override suspend fun getMangaMetadata(track: DomainMangaTrack): TrackMangaMetadata? = null

    override suspend fun getAnimeMetadata(track: DomainAnimeTrack): TrackAnimeMetadata? = null

    override suspend fun update(
        track: MangaTrack,
        didReadChapter: Boolean,
    ): MangaTrack {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                    track.finished_reading_date = System.currentTimeMillis()
                } else if (track.status != REREADING) {
                    track.status = READING
                    if (track.last_chapter_read == 1.0) {
                        track.started_reading_date = System.currentTimeMillis()
                    }
                }
            }
        }
        return api.updateUserManga(track)
    }

    override suspend fun update(
        track: AnimeTrack,
        didWatchEpisode: Boolean,
    ): AnimeTrack {
        if (track.status != COMPLETED) {
            if (didWatchEpisode) {
                if (track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0) {
                    track.status = COMPLETED
                    track.finished_watching_date = System.currentTimeMillis()
                } else if (track.status != REWATCHING) {
                    track.status = WATCHING
                    if (track.last_episode_seen == 1.0) {
                        track.started_watching_date = System.currentTimeMillis()
                    }
                }
            }
        }
        return api.updateUserAnime(track)
    }

    override suspend fun bind(track: MangaTrack, hasReadChapters: Boolean): MangaTrack {
        val readContent = api.getRead(track)
        val remoteTrack = api.getManga(track)

        track.copyPersonalFrom(remoteTrack)
        track.remote_id = remoteTrack.remote_id
        track.library_id = remoteTrack.library_id

        if (track.status != COMPLETED) {
            val isRereading = track.status == REREADING
            track.status = if (!isRereading && hasReadChapters) READING else track.status
        }

        return if (readContent != null) {
            track.score = readContent.score.toDouble()
            track.last_chapter_read = readContent.chapters.toDouble()
            track.started_reading_date = (readContent.startDate ?: 0L) * 1000
            track.finished_reading_date = (readContent.endDate ?: 0L) * 1000
            update(track)
        } else {
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            update(track)
        }
    }

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        val watchContent = api.getWatch(track)
        val remoteTrack = api.getAnime(track)

        track.copyPersonalFrom(remoteTrack)
        track.remote_id = remoteTrack.remote_id
        track.library_id = remoteTrack.library_id

        if (track.status != COMPLETED) {
            val isRewatching = track.status == REWATCHING
            track.status = if (!isRewatching && hasSeenEpisodes) WATCHING else track.status
        }

        return if (watchContent != null) {
            track.score = watchContent.score.toDouble()
            track.last_episode_seen = watchContent.chapters.toDouble()
            track.started_watching_date = (watchContent.startDate ?: 0L) * 1000
            track.finished_watching_date = (watchContent.endDate ?: 0L) * 1000
            update(track)
        } else {
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            update(track)
        }
    }

    override suspend fun searchManga(query: String): List<MangaTrackSearch> = api.searchManga(query)

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> = api.searchAnime(query)

    override suspend fun refresh(track: MangaTrack): MangaTrack {
        val remoteTrack = api.getManga(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters

        val readContent = api.getRead(track) ?: throw Exception("Could not find manga")

        track.score = readContent.score.toDouble()
        track.last_chapter_read = readContent.chapters.toDouble()
        track.status = toTrackStatus(readContent.status)
        track.started_reading_date = (readContent.startDate ?: 0L) * 1000
        track.finished_reading_date = (readContent.endDate ?: 0L) * 1000

        return track
    }

    override suspend fun refresh(track: AnimeTrack): AnimeTrack {
        val remoteTrack = api.getAnime(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_episodes = remoteTrack.total_episodes

        val watchContent = api.getWatch(track) ?: throw Exception("Could not find anime")

        track.score = watchContent.score.toDouble()
        track.last_episode_seen = watchContent.chapters.toDouble()
        track.status = toTrackStatus(watchContent.status)
        track.started_watching_date = (watchContent.startDate ?: 0L) * 1000
        track.finished_watching_date = (watchContent.endDate ?: 0L) * 1000

        return track
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(reference: String) {
        try {
            val oauth = api.accessToken(reference)
            interceptor.setAuth(oauth)
            val user = api.getCurrentUser()
            saveDisplayUsername(user.username)
            saveCredentials(user.reference, oauth.accessToken)
        } catch (_: Throwable) {
            logout()
        }
    }

    override suspend fun delete(track: DomainMangaTrack) = api.deleteUserManga(track)

    override suspend fun delete(track: DomainAnimeTrack) = api.deleteUserAnime(track)

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    fun saveOAuth(oAuth: HKOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oAuth))
    }

    fun loadOAuth(): HKOAuth? {
        return try {
            json.decodeFromString<HKOAuth>(trackPreferences.trackToken(this).get())
        } catch (_: Exception) {
            null
        }
    }
}
