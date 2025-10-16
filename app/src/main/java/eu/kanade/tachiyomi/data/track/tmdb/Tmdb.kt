package eu.kanade.tachiyomi.data.track.tmdb

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.model.Credit
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import logcat.LogPriority
import okhttp3.OkHttpClient
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.util.Locale
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class Tmdb(id: Long) : BaseTracker(id, "TMDB"), AnimeTracker {

    companion object {
        const val WATCHING = 11L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 15L
        const val REWATCHING = 16L

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)
            .toImmutableList()
    }

    override val client: OkHttpClient
        get() = networkService.client

    private val apiKey: String
        get() = trackPreferences.trackApiKey(this).get()

    private val sessionId: String
        get() = trackPreferences.trackToken(this).get()

    private val api: TmdbApi
        get() = TmdbApi(client, apiKey, sessionId)

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun displayScore(track: DomainAnimeTrack): String {
        return track.score.toInt().toString()
    }

    private suspend fun add(track: AnimeTrack): AnimeTrack {
        if (sessionId.isNotBlank()) {
            try {
                val mediaType = if (track.tracking_url.contains("/tv/")) "tv" else "movie"
                api.addToWatchlist(mediaType, track.remote_id, true)
            } catch (_: Exception) {
            }
        } else {
            logcat(LogPriority.WARN) { "TMDB add: sessionId is blank, skipping addToWatchlist" }
        }
        return track
    }

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack {
        if (track.status != COMPLETED) {
            if (didWatchEpisode) {
                if (track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0) {
                    track.status = COMPLETED
                } else {
                    track.status = PLAN_TO_WATCH
                }
            }
        }

        // Sync rating if session exists
        if (sessionId.isNotBlank()) {
            try {
                val mediaType = if (track.tracking_url.contains("/tv/")) "tv" else "movie"
                updateRating(track, mediaType)

                // Sync watchlist based on status
                val shouldWatchlist = track.status == PLAN_TO_WATCH
                api.addToWatchlist(mediaType, track.remote_id, shouldWatchlist)
                logcat(LogPriority.INFO) {
                    "TMDB update: synced watchlist for status ${track.status}, shouldWatchlist=$shouldWatchlist"
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "TMDB update: failed to sync watchlist. Error: ${e.message}" }
            }
        }

        return track
    }

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        val remoteTrack = findLibAnime(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                track.status = if (hasSeenEpisodes) WATCHING else track.status
            }

            // If not in watchlist, add it
            if (remoteTrack.status != PLAN_TO_WATCH && sessionId.isNotBlank()) {
                add(track)
            }

            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            add(track)
        }
    }

    private suspend fun findLibAnime(track: AnimeTrack): AnimeTrack? {
        // Emulate findLibAnime using account_states
        if (track.remote_id != 0L && sessionId.isNotBlank()) {
            try {
                val lang = Locale.getDefault().toLanguageTag()
                val accountJson = try {
                    api.getAccountStates(track.remote_id, "tv", lang)
                } catch (_: Exception) {
                    api.getAccountStates(track.remote_id, "movie", lang)
                }

                val detail = try {
                    api.getMovie(track.remote_id, lang)
                } catch (_: Exception) {
                    api.getTv(track.remote_id, lang)
                }

                return AnimeTrack.create(this@Tmdb.id).apply {
                    remote_id = track.remote_id
                    title = detail.title
                    score = when {
                        accountJson.has("rated") && accountJson.get("rated") is JSONObject -> {
                            accountJson.getJSONObject("rated").optDouble("value", 0.0)
                        }
                        else -> 0.0
                    }
                    status = if (accountJson.optBoolean("watchlist", false)) PLAN_TO_WATCH else WATCHING
                    total_episodes = detail.additional.optLong("number_of_episodes", 1)
                }.also {
                    logcat(LogPriority.INFO) {
                        "TMDB findLibAnime: watchlist=${accountJson.optBoolean(
                            "watchlist",
                            false,
                        )}, status=${it.status}"
                    }
                }
            } catch (_: Exception) { }
        }
        return null
    }

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> {
        val results = api.searchMulti(query)
        return results.filter { it.mediaType == "tv" || it.mediaType == "movie" }.map { r ->
            val isTv = r.mediaType == "tv"
            AnimeTrackSearch.create(this@Tmdb.id).apply {
                remote_id = r.id
                title = r.title
                tracking_url =
                    if (isTv) "https://www.themoviedb.org/tv/${r.id}" else "https://www.themoviedb.org/movie/${r.id}"
                summary = r.overview
                cover_url = r.posterPath?.let { TmdbApi.IMAGE_BASE + it } ?: ""
                publishing_type = if (isTv) "TV" else "Movie"
            }
        }.distinctBy { it.remote_id }
    }

    override suspend fun refresh(track: AnimeTrack): AnimeTrack {
        findLibAnime(track)?.let { remoteTrack ->
            track.score = remoteTrack.score
            track.total_episodes = remoteTrack.total_episodes
        }
        return track
    }

    override fun getLogo() = R.drawable.ic_tracker_tmdb

    override fun getLogoColor() = Color.rgb(13, 37, 63)

    override fun getStatusListAnime(): List<Long> {
        return listOf(WATCHING, PLAN_TO_WATCH, COMPLETED)
    }

    override fun getStatusForAnime(status: Long): StringResource? = when (status) {
        WATCHING -> AYMR.strings.watching
        PLAN_TO_WATCH -> AYMR.strings.plan_to_watch
        COMPLETED -> MR.strings.completed
        else -> null
    }

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = REWATCHING

    override fun getCompletionStatus(): Long = COMPLETED

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(code: String) {
        try {
            val oauth = api.createSession(code)
            trackPreferences.trackToken(this).set(oauth.optString("session_id"))
            saveCredentials("tmdb", "tmdb")
        } catch (_: Throwable) {
            logout()
        }
    }

    @Suppress("unused")
    suspend fun getAuthUrl(): String {
        if (apiKey.isBlank()) throw Exception("TMDB API key required. Please save your API key first.")
        val tokenJson = api.getRequestToken()
        val requestToken = tokenJson.optString("request_token")
        return "https://www.themoviedb.org/authenticate/$requestToken?redirect_to=animetail://tmdb-auth"
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        trackPreferences.trackApiKey(this).delete()
    }

    override suspend fun getAnimeMetadata(track: DomainAnimeTrack): TrackAnimeMetadata? {
        val query = track.title
        val lang = Locale.getDefault().toLanguageTag()
        val results = api.searchMulti(query, lang)
        val first = results.firstOrNull() ?: return null
        val detail = try {
            api.getMovie(first.id, lang)
        } catch (_: Exception) {
            api.getTv(first.id, lang)
        }
        val poster = detail.posterPath?.let { TmdbApi.IMAGE_BASE + it }

        val authors = try {
            val creditsJson = api.getCredits(detail.id, first.mediaType, lang)
            val crewArr = creditsJson.optJSONArray("crew")
            val directors = mutableListOf<String>()
            if (crewArr != null) {
                for (i in 0 until crewArr.length()) {
                    val item = crewArr.getJSONObject(i)
                    if (item.optString("job") == "Director") {
                        directors.add(item.optString("name"))
                    }
                }
            }
            directors.joinToString(", ").ifEmpty { null }
        } catch (_: Exception) {
            null
        }

        val artists = try {
            val companiesArr = detail.additional.optJSONArray("production_companies")
            val companies = mutableListOf<String>()
            if (companiesArr != null) {
                for (i in 0 until companiesArr.length()) {
                    val item = companiesArr.getJSONObject(i)
                    companies.add(item.optString("name"))
                }
            }
            companies.joinToString(", ").ifEmpty { null }
        } catch (_: Exception) {
            null
        }

        val enrichedDescription = detail.overview.ifEmpty { null }

        return TrackAnimeMetadata(
            remoteId = detail.id,
            title = detail.title,
            thumbnailUrl = poster,
            description = enrichedDescription,
            authors = authors,
            artists = artists,
        )
    }

    override suspend fun getMangaMetadata(track: DomainMangaTrack): TrackMangaMetadata? {
        throw NotImplementedError("Not implemented.")
    }

    private suspend fun updateRating(track: AnimeTrack, mediaType: String) {
        if (track.score > 0) {
            if (mediaType == "movie") {
                api.addMovieRating(track.remote_id, track.score)
            } else {
                api.addTvRating(track.remote_id, track.score)
            }
        } else {
            if (mediaType == "movie") {
                api.deleteMovieRating(track.remote_id)
            } else {
                api.deleteTvRating(track.remote_id)
            }
        }
    }

    override suspend fun fetchCastByTitle(title: String?): List<Credit>? {
        if (title.isNullOrBlank()) return null

        return try {
            val first = api.searchMulti(title).firstOrNull() ?: return null
            val creditsJson = api.getCredits(first.id, first.mediaType)

            val credits = mutableListOf<Credit>()

            val castArr = creditsJson.optJSONArray("cast")
            if (castArr != null) {
                for (i in 0 until castArr.length()) {
                    val item = castArr.getJSONObject(i)
                    val name = item.optString("name")
                    val character = item.optString("character").ifEmpty { null }
                    var image: String? = item.optString("profile_path", null)
                    if (!image.isNullOrBlank()) image = TmdbApi.IMAGE_BASE + image
                    credits.add(Credit(name = name, role = "Cast", character = character, image_url = image))
                }
            }

            val crewArr = creditsJson.optJSONArray("crew")
            if (crewArr != null) {
                for (i in 0 until crewArr.length()) {
                    val item = crewArr.getJSONObject(i)
                    val name = item.optString("name")
                    val job = item.optString("job").ifEmpty { null }
                    var image: String? = item.optString("profile_path", null)
                    if (!image.isNullOrBlank()) image = TmdbApi.IMAGE_BASE + image
                    credits.add(Credit(name = name, role = job, image_url = image))
                }
            }

            credits.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun register(item: AnimeTrack, animeId: Long) {
        if (item.remote_id == 0L) {
            val url = item.tracking_url
            if (url.isNotBlank()) {
                val tvRegex = ".*/tv/(\\d+).*".toRegex()
                val movieRegex = ".*/movie/(\\d+).*".toRegex()
                when {
                    tvRegex.matches(url) -> item.remote_id = tvRegex.find(url)!!.groupValues[1].toLong()
                    movieRegex.matches(url) -> item.remote_id = movieRegex.find(url)!!.groupValues[1].toLong()
                    else -> {
                        val seg = url.trimEnd('/').substringAfterLast('/')
                        if (seg.all { it.isDigit() }) {
                            item.remote_id = seg.toLong()
                        }
                    }
                }
            }
        }

        val url = item.tracking_url
        val isTv = url.contains("/tv/")
        val isMovie = url.contains("/movie/")
        if (item.remote_id != 0L) {
            val lang = Locale.getDefault().toLanguageTag()
            when {
                isTv -> {
                    item.tracking_url = "https://www.themoviedb.org/tv/${item.remote_id}"
                    try {
                        val detail = api.getTv(item.remote_id, lang)
                        item.title = detail.title
                    } catch (_: Exception) {}
                }
                isMovie -> {
                    item.tracking_url = "https://www.themoviedb.org/movie/${item.remote_id}"
                    try {
                        val detail = api.getMovie(item.remote_id, lang)
                        item.title = detail.title
                    } catch (_: Exception) {}
                }
                else -> {
                    try {
                        val detailTv = api.getTv(item.remote_id, lang)
                        item.tracking_url = "https://www.themoviedb.org/tv/${item.remote_id}"
                        item.title = detailTv.title
                    } catch (_: Exception) {
                        try {
                            val detailMovie = api.getMovie(item.remote_id, lang)
                            item.tracking_url = "https://www.themoviedb.org/movie/${item.remote_id}"
                            item.title = detailMovie.title
                        } catch (_: Exception) { }
                    }
                }
            }
        }
        super.register(item, animeId)
    }

    override fun isAvailableForUse(): Boolean {
        return apiKey.isNotBlank()
    }
}
