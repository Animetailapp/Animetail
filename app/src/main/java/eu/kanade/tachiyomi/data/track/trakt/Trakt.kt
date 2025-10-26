package eu.kanade.tachiyomi.data.track.trakt

import android.graphics.Color
import android.util.Log
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
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktOAuth
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
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
    id: Long
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

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> {
        return api.search(query).mapNotNull { result ->
            when (result.type) {
                "show" -> result.show?.let {
                    AnimeTrackSearch.create(this@Trakt.id).apply {
                        remote_id = it.ids.trakt
                        title = it.title
                        summary = it.overview ?: ""
                        cover_url = run {
                            // Lightweight poster extraction from search result only — avoid extra network calls here to keep search fast.
                            val posterEl = it.images?.poster
                            val posterUrl = posterEl?.let { el ->
                                try {
                                    when (el) {
                                        is kotlinx.serialization.json.JsonArray -> el.firstOrNull()?.jsonPrimitive?.contentOrNull
                                        is kotlinx.serialization.json.JsonObject -> {
                                            el["full"]?.jsonPrimitive?.contentOrNull
                                                ?: el["medium"]?.jsonPrimitive?.contentOrNull
                                                ?: el["thumb"]?.jsonPrimitive?.contentOrNull
                                        }
                                        else -> el.jsonPrimitive?.contentOrNull
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            posterUrl?.let { u -> if (u.startsWith("http")) u else "https://$u" } ?: ""
                        }
                        // Defer episode count lookup to metadata call (avoid blocking search). Use 0 as placeholder.
                        total_episodes = 0L
                        tracking_url = "https://trakt.tv/shows/${it.ids.slug}"
                    }
                }
                "movie" -> result.movie?.let {
                    AnimeTrackSearch.create(this@Trakt.id).apply {
                        remote_id = it.ids.trakt
                        title = it.title
                        summary = it.overview ?: ""
                        cover_url = run {
                            // Keep search fast — only use poster data present in the search result.
                            val posterEl = it.images?.poster
                            val posterUrl = posterEl?.let { el ->
                                try {
                                    when (el) {
                                        is kotlinx.serialization.json.JsonArray -> el.firstOrNull()?.jsonPrimitive?.contentOrNull
                                        is kotlinx.serialization.json.JsonObject -> {
                                            el["full"]?.jsonPrimitive?.contentOrNull
                                                ?: el["medium"]?.jsonPrimitive?.contentOrNull
                                                ?: el["thumb"]?.jsonPrimitive?.contentOrNull
                                        }
                                        else -> el.jsonPrimitive?.contentOrNull
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            posterUrl?.let { u -> if (u.startsWith("http")) u else "https://$u" } ?: ""
                        }
                        total_episodes = 1L
                        tracking_url = "https://trakt.tv/movies/${it.ids.slug}"
                    }
                }
                else -> null
            }
        }
    }

    private fun idsFromRemoteId(remoteId: String): TraktIds {
        val traktId = try {
            remoteId.toLong()
        } catch (_: Exception) {
            0L
        }
        return TraktIds(trakt = traktId, slug = "", imdb = null, tmdb = null)
    }

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack {
        if (track.remote_id == 0L) return track
        // Update local state similar to other trackers
        if (track.status != COMPLETED) {
            if (didWatchEpisode) {
                if (track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0) {
                    track.status = COMPLETED
                } else {
                    track.status = WATCHING
                }
            }
        }

        // Push to Trakt
        val ids = idsFromRemoteId(track.remote_id.toString())
        // If this is a movie (single-episode), use movie-watched sync.
        if (track.total_episodes == 1L) {
            val syncMovie = eu.kanade.tachiyomi.data.track.trakt.dto.TraktSyncMovie(
                ids = ids,
                watched = true
            )
            try {
                // Avoid creating duplicate watched entries on Trakt:
                // - Only send when the local track indicates the movie was watched (last_episode_seen >= 1)
                // - And only if the movie isn't already present in the user's watched movies list.
                if (track.last_episode_seen.toLong() >= 1L) {
                    val alreadyWatched = try {
                        api.getUserMovies().any { it.traktId == ids.trakt }
                    } catch (_: Exception) {
                        false
                    }
                    if (!alreadyWatched) {
                        api.updateMovieWatched(syncMovie)
                    }
                }

                // Sync rating for movies if present (Trakt expects 1-10 integers).
                if (track.score > 0.0) {
                    try {
                        val rating = track.score.toInt().coerceIn(1, 10)
                        api.sendRatings(movieRatings = listOf(Pair(ids.trakt, rating)))
                    } catch (_: Exception) {
                        // ignore rating failures
                    }
                }
            } catch (_: Exception) {
            }
            return track
        }

        // For shows, sync the single episode watched (avoid marking entire show as watched).
        val traktId = ids.trakt

        // Interpret fractional last_episode_seen as season.episode when present.
        // Example: 2.05 or 2.5 stored as 2.05/2.5 -> season 2, episode 5 (fractional digits represent episode).
        val lastSeen = track.last_episode_seen
        val seasonParam: Int?
        val episodeParam: Int
        val lastSeenStr = try {
            java.math.BigDecimal.valueOf(lastSeen).stripTrailingZeros().toPlainString()
        } catch (_: Exception) {
            null
        }

        if (!lastSeenStr.isNullOrBlank() && lastSeenStr.contains('.')) {
            val parts = lastSeenStr.split('.', limit = 2)
            seasonParam = parts.getOrNull(0)?.toIntOrNull() ?: 1
            // Parse fractional part as episode (handles "1.2" -> episode 2, "1.02" -> episode 2)
            episodeParam = parts.getOrNull(1)?.replace("^0+".toRegex(), "")?.toIntOrNull()?.takeIf { it != 0 }
                ?: parts.getOrNull(1)?.toIntOrNull()
                ?: lastSeen.roundToInt().coerceAtLeast(1)
        } else {
            seasonParam = null
            episodeParam = lastSeen.roundToInt().coerceAtLeast(1)
        }

        try {
            try {
                Log.d("Trakt", "update() -> track.id=${track.id} remote_id=${track.remote_id} last_episode_seen=${track.last_episode_seen} total_episodes=${track.total_episodes} didWatchEpisode=$didWatchEpisode seasonParam=${seasonParam ?: "null"} episodeParam=$episodeParam")
            } catch (_: Exception) {}
            // Send resolved season/episode to Trakt (seasonParam may be null to trigger API heuristics).
            api.updateShowEpisodeProgress(traktId, seasonParam, episodeParam)

            // Sync rating for shows if present (Trakt expects 1-10 integers).
            if (track.score > 0.0) {
                try {
                    val rating = track.score.toInt().coerceIn(1, 10)
                    api.sendRatings(showRatings = listOf(Pair(traktId, rating)))
                } catch (_: Exception) {
                    // ignore rating failures
                }
            }
        } catch (_: Exception) {
        }

        return track
    }

    override suspend fun delete(track: DomainAnimeTrack) {
        // Best-effort removal using the domain model fields.
        try {
            val rid = track.remoteId
            if (rid == 0L) return
            // Try both removals; one will be a no-op server-side if not applicable.
            try { api.removeShowHistory(rid) } catch (_: Exception) {}
            try { api.removeMovieHistory(rid) } catch (_: Exception) {}
        } catch (_: Exception) {
            // ignore failures for best-effort removal
        }
    }

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        // Try to find the item in the user's watched/collection. If found, copy progress into the track.
        try {
            val remoteId = track.remote_id
            if (remoteId == 0L) return update(track, didWatchEpisode = hasSeenEpisodes)
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

    suspend fun login(code: String) {
        // Make the login function suspending and add diagnostics logging to help identify why no access token is present.
        try {
            android.util.Log.d("Trakt", "Exchanging code for token (code length=${code.length})")
            val token = try {
                api.loginOAuth(code, CLIENT_ID, CLIENT_SECRET, REDIRECT_URI)
            } catch (e: Exception) {
                android.util.Log.e("Trakt", "loginOAuth request failed", e)
                null
            }
            if (token == null) {
                android.util.Log.e("Trakt", "Failed to obtain token from Trakt (null response)")
                throw Exception("Failed to get token from Trakt")
            }
            android.util.Log.d("Trakt", "Obtained access_token (masked): ${token.access_token.take(8)}... refresh_token present=${!token.refresh_token.isNullOrBlank()}")
            oauth = token
            // Set interceptor auth immediately so subsequent calls include Authorization header.
            interceptor.setAuth(token.access_token)
            // Persist token
            saveToken(token)

            // fetch username and save as credentials (password stores access token per BaseTracker convention)
            val username = try {
                api.getCurrentUser() ?: ""
            } catch (e: Exception) {
                android.util.Log.e("Trakt", "getCurrentUser failed", e)
                ""
            }
            saveCredentials(username, token.access_token)
            android.util.Log.d("Trakt", "Login completed and credentials saved for user='$username'")
        } catch (e: Throwable) {
            android.util.Log.e("Trakt", "Login failed, performing logout", e)
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
}
