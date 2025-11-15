package eu.kanade.tachiyomi.data.track.trakt

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableAnimeTracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktIds
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktMovie
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktOAuth
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktShow
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktSyncMovie
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.injectLazy
import kotlin.math.roundToInt
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * Trakt.tv tracker implementation (anime / shows / movies).
 */
class Trakt(
    id: Long,
) : BaseTracker(id, "Trakt"), AnimeTracker, DeletableAnimeTracker {

    companion object {
        const val WATCHING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 5L

        // Replace these with your app credentials (user provided values are filled here)
        const val CLIENT_ID = "8c0053aa008708d212e8d6651194866455110802f33c8ba82c5e7ee5f15d3a16"
        private const val CLIENT_SECRET = "24b1314e8a6f0176eb6c4249c72381e7aa1ef91f64743293676476a461fb20d4"
        const val REDIRECT_URI = "animetail://trakt-auth"
        const val SCOPES = "public"
    }

    private val json: Json by injectLazy()

    // Interceptor and API built with injected OKHttp client
    private val interceptor by lazy { TraktInterceptor(this, null, CLIENT_ID) }
    private val api by lazy { TraktApi(client, interceptor) }

    // In-memory current oauth
    private var oauth: TraktOAuth? = null

    override val name: String = "Trakt"
    override val id: Long = 201L
    override val supportsReadingDates: Boolean = true
    override val supportsPrivateTracking: Boolean = false

    override fun getLogo() = R.drawable.ic_tracker_trakt
    override fun getLogoColor() = Color.rgb(255, 69, 0)

    override fun getStatusListAnime(): List<Long> {
        return listOf(WATCHING, PLAN_TO_WATCH, COMPLETED, ON_HOLD, DROPPED)
    }

    override fun getStatusForAnime(status: Long): StringResource? {
        return when (status) {
            WATCHING -> AYMR.strings.watching
            COMPLETED -> MR.strings.completed
            ON_HOLD -> MR.strings.on_hold
            DROPPED -> MR.strings.dropped
            PLAN_TO_WATCH -> AYMR.strings.plan_to_watch
            else -> null
        }
    }

    override fun getWatchingStatus(): Long = WATCHING
    override fun getRewatchingStatus(): Long = 0L
    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> {
        return persistentListOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
    }

    override fun get10PointScore(track: DomainAnimeTrack): Double {
        return track.score
    }

    override fun indexToScore(index: Int): Double = index.toDouble()

    override fun displayScore(track: DomainAnimeTrack): String = track.score.toString()

    init {
        // Restore persisted token (if any) and set auth on interceptor so api calls use it.
        restoreToken()?.let { saved ->
            oauth = saved
            interceptor.setAuth(saved.access_token)
        }
    }

    fun saveToken(oauth: TraktOAuth?) {
        if (oauth == null) {
            trackPreferences.trackToken(this).delete()
        } else {
            trackPreferences.trackToken(this).set(json.encodeToString(oauth))
        }
    }

    fun restoreToken(): TraktOAuth? {
        return try {
            val raw = trackPreferences.trackToken(this).get()
            if (raw.isBlank()) return null
            json.decodeFromString<TraktOAuth>(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureTotalEpisodes(track: AnimeTrack) {
        if (track.total_episodes > 0 || track.remote_id == 0L) return
        if (isMovieTrack(track)) {
            // Some older movie entries might have been stored without explicitly setting episode count.
            track.total_episodes = 1L
            return
        }

        try {
            val total = api.getShowEpisodeCount(track.remote_id)
            if (total > 0) {
                track.total_episodes = total
            }
        } catch (_: Exception) {
            // Network/parse errors shouldn't block the rest of the sync flow.
        }
    }

    private fun isMovieTrack(track: AnimeTrack): Boolean {
        if (track.total_episodes == 1L) return true
        val url = track.tracking_url
        return url.contains("/movies/", ignoreCase = true)
    }

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> {
        return api.search(query).mapNotNull { result ->
            when (result.type) {
                "show" -> result.show?.toTrackSearch()
                "movie" -> result.movie?.toTrackSearch()
                else -> null
            }
        }
    }

    private fun idsFromRemoteId(remoteId: Long): TraktIds {
        return TraktIds(trakt = remoteId, slug = "", imdb = null, tmdb = null)
    }

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack {
        if (track.remote_id == 0L) return track
        ensureTotalEpisodes(track)
        applyLocalStatus(track, didWatchEpisode)
        return if (isMovieTrack(track)) {
            updateMovieTrack(track)
        } else {
            updateShowTrack(track)
        }
    }

    override suspend fun delete(track: DomainAnimeTrack) {
        // Best-effort removal using the domain model fields.
        try {
            val rid = track.remoteId
            if (rid == 0L) return
            // Try both removals; one will be a no-op server-side if not applicable.
            try {
                api.removeShowHistory(rid)
            } catch (_: Exception) {}
            try {
                api.removeMovieHistory(rid)
            } catch (_: Exception) {}
        } catch (_: Exception) {
            // ignore failures for best-effort removal
        }
    }

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        // Try to find the item in the user's watched/collection. If found, copy progress into the track.
        try {
            val remoteId = track.remote_id
            if (remoteId == 0L) return update(track, didWatchEpisode = hasSeenEpisodes)
            ensureTotalEpisodes(track)
            val traktId = remoteId
            val items = if (track.total_episodes == 1L) {
                api.getUserMovies()
            } else {
                api.getUserShows()
            }
            val found = items.firstOrNull { it.traktId == traktId }
            if (found != null) {
                track.library_id = traktId
                track.last_episode_seen = found.progress.toDouble()
                return track
            }
        } catch (_: Exception) {
            // ignore and fallback to update
        }
        return update(track, didWatchEpisode = hasSeenEpisodes)
    }

    override suspend fun refresh(track: AnimeTrack): AnimeTrack {
        try {
            val remoteId = track.remote_id
            if (remoteId == 0L) return track
            ensureTotalEpisodes(track)
            val traktId = remoteId
            val items = if (track.total_episodes == 1L) {
                api.getUserMovies()
            } else {
                api.getUserShows()
            }
            val found = items.firstOrNull { it.traktId == traktId }
            if (found != null) {
                track.last_episode_seen = found.progress.toDouble()
            }
        } catch (_: Exception) {
            // ignore errors, return track as-is
        }
        return track
    }

    // OAuth login helpers:
    // The app's TrackLoginActivity should provide the authorization code to this login(code) method.
    override suspend fun login(username: String, password: String) = login(password)

    fun login(code: String) {
        try {
            val token = try {
                api.loginOAuth(code, CLIENT_ID, CLIENT_SECRET, REDIRECT_URI)
            } catch (_: Exception) {
                null
            }
            if (token == null) {
                throw Exception("Failed to get token from Trakt")
            }
            oauth = token
            interceptor.setAuth(token.access_token)
            saveToken(token)

            // fetch username and save as credentials (password stores access token per BaseTracker convention)
            val username = try {
                api.getCurrentUser() ?: ""
            } catch (_: Exception) {
                ""
            }
            saveCredentials(username, token.access_token)
        } catch (e: Throwable) {
            logout()
            throw e
        }
    }

    /**
     * Blocking refresh used by the interceptor when executing synchronous requests.
     * Returns true if the token was refreshed successfully.
     */
    fun refreshAuthBlocking(): Boolean {
        return try {
            val saved = restoreToken() ?: return false
            val refreshed = api.refreshOAuth(saved.refresh_token, CLIENT_ID, CLIENT_SECRET) ?: return false
            oauth = refreshed
            interceptor.setAuth(refreshed.access_token)
            saveToken(refreshed)
            // Try to update stored username
            try {
                val username = api.getCurrentUser() ?: ""
                saveCredentials(username, refreshed.access_token)
            } catch (_: Exception) {
                // ignore
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun logout() {
        // Clear persisted tokens and interceptor
        oauth = null
        saveToken(null)
        interceptor.setAuth(null)
        super.logout()
    }

    override suspend fun getMangaMetadata(track: MangaTrack): TrackMangaMetadata? =
        throw NotImplementedError("Not implemented.")

    override suspend fun getAnimeMetadata(track: DomainAnimeTrack): TrackAnimeMetadata? {
        // Try to fetch show metadata from Trakt. If not available, fall back to movie metadata.
        val remote = track.remoteId
        if (remote == 0L) return null

        return try {
            // Prefer public (no-cookie) metadata for UI lazy loads to avoid auth/cookie issues and be faster.
            api.getShowMetadataPublic(remote)
                ?: api.getMovieMetadataPublic(remote)
                // Fallback to authenticated variants if public variants fail or require auth.
                ?: api.getShowMetadata(remote)
                ?: api.getMovieMetadata(remote)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun fetchCastByTitle(title: String?): List<eu.kanade.tachiyomi.animesource.model.Credit>? {
        if (title.isNullOrBlank()) return null
        return try {
            // Search Trakt for the title and use the first show result to fetch cast.
            val results = api.search(title)
            val showResult = results.firstOrNull { it.type == "show" }?.show
            val traktId = showResult?.ids?.trakt ?: return null
            api.getShowCast(traktId)
        } catch (_: Exception) {
            null
        }
    }

    private fun TraktShow.toTrackSearch(): AnimeTrackSearch =
        createTrackSearch(ids.trakt, title, overview, images?.poster, ids.slug, isMovie = false)

    private fun TraktMovie.toTrackSearch(): AnimeTrackSearch =
        createTrackSearch(ids.trakt, title, overview, images?.poster, ids.slug, isMovie = true)

    private fun createTrackSearch(
        remoteId: Long,
        title: String,
        overview: String?,
        posterEl: JsonElement?,
        slug: String,
        isMovie: Boolean,
    ): AnimeTrackSearch {
        val path = if (isMovie) "movies" else "shows"
        val slugOrId = slug.takeIf { it.isNotBlank() } ?: remoteId.toString()
        return AnimeTrackSearch.create(this@Trakt.id).apply {
            this.remote_id = remoteId
            this.title = title
            summary = overview ?: ""
            cover_url = extractPosterUrl(posterEl)
            total_episodes = if (isMovie) 1L else 0L
            tracking_url = "https://trakt.tv/$path/$slugOrId"
        }
    }

    private fun extractPosterUrl(posterEl: JsonElement?): String {
        val raw = when (posterEl) {
            null -> null
            is JsonObject -> posterEl["full"]?.jsonPrimitive?.contentOrNull
                ?: posterEl["medium"]?.jsonPrimitive?.contentOrNull
                ?: posterEl["thumb"]?.jsonPrimitive?.contentOrNull
                ?: posterEl.entries.firstOrNull()?.value?.let { extractPosterUrl(it) }
            is JsonArray -> posterEl.firstOrNull()?.jsonPrimitive?.contentOrNull
            else -> posterEl.jsonPrimitive.contentOrNull
        }?.trim().takeUnless { it.isNullOrBlank() } ?: return ""

        return when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("/") -> raw
            else -> "https://$raw"
        }
    }

    private fun applyLocalStatus(track: AnimeTrack, didWatchEpisode: Boolean) {
        if (!didWatchEpisode || track.status == COMPLETED) return
        track.status = if (track.total_episodes > 0 && track.last_episode_seen.toLong() == track.total_episodes) {
            COMPLETED
        } else {
            WATCHING
        }
    }

    private fun updateMovieTrack(track: AnimeTrack): AnimeTrack {
        val ids = idsFromRemoteId(track.remote_id)
        runCatching {
            if (track.last_episode_seen.toLong() >= 1L) {
                val alreadyWatched = runCatching {
                    api.getUserMovies().any { it.traktId == ids.trakt }
                }.getOrDefault(false)
                if (!alreadyWatched) {
                    val syncMovie = TraktSyncMovie(ids = ids, watched = true)
                    api.updateMovieWatched(syncMovie)
                }
            }
            syncRating(ids.trakt, track.score, isMovie = true)
        }
        return track
    }

    private fun updateShowTrack(track: AnimeTrack): AnimeTrack {
        val traktId = track.remote_id
        val (seasonParam, episodeParam) = resolveSeasonEpisode(track.last_episode_seen)
        runCatching {
            api.updateShowEpisodeProgress(traktId, seasonParam, episodeParam)
        }
        syncRating(traktId, track.score, isMovie = false)
        return track
    }

    private fun resolveSeasonEpisode(lastSeen: Double): Pair<Int?, Int> {
        val lastSeenStr = runCatching {
            java.math.BigDecimal.valueOf(lastSeen).stripTrailingZeros().toPlainString()
        }.getOrNull()
        if (!lastSeenStr.isNullOrBlank() && lastSeenStr.contains('.')) {
            val parts = lastSeenStr.split('.', limit = 2)
            val season = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it > 0 } ?: 1
            val fraction = parts.getOrNull(1).orEmpty()
            val episode = fraction.trimStart('0').toIntOrNull()
                ?: fraction.toIntOrNull()
                ?: lastSeen.roundToInt().coerceAtLeast(1)
            return season to episode
        }
        return null to lastSeen.roundToInt().coerceAtLeast(1)
    }

    private fun syncRating(traktId: Long, score: Double, isMovie: Boolean) {
        if (score <= 0.0) return
        val rating = score.toInt().coerceIn(1, 10)
        runCatching {
            if (isMovie) {
                api.sendRatings(movieRatings = listOf(traktId to rating))
            } else {
                api.sendRatings(showRatings = listOf(traktId to rating))
            }
        }
    }
}
