package eu.kanade.tachiyomi.data.track.trakt

import android.util.Log
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktOAuth
import eu.kanade.tachiyomi.data.track.trakt.dto.TraktSearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TraktApi(private val client: OkHttpClient, private val interceptor: TraktInterceptor) {

    private val baseUrl = "https://api.trakt.tv"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Normalize Trakt image fields into a single URL string.
     * Handles cases:
     * - JsonObject with sized keys ("full", "medium", "thumb")
     * - JsonArray of URL strings
     * - Primitive string
     */
    private fun extractImageUrl(el: JsonElement?): String? {
        if (el == null) return null
        return try {
            val raw = when (el) {
                is JsonObject -> {
                    // Try preferred keys first.
                    val full = el["full"]?.jsonPrimitive?.contentOrNull
                    if (!full.isNullOrBlank()) {
                        full
                    } else {
                        val medium = el["medium"]?.jsonPrimitive?.contentOrNull
                        if (!medium.isNullOrBlank()) {
                            medium
                        } else {
                            val thumb = el["thumb"]?.jsonPrimitive?.contentOrNull
                            if (!thumb.isNullOrBlank()) {
                                thumb
                            } else {
                                // Try nested entries (e.g., localized objects)
                                val first = el.entries.firstOrNull()?.value
                                first?.let { extractImageUrl(it) }
                            }
                        }
                    }
                }
                is JsonArray -> el.firstOrNull()?.jsonPrimitive?.contentOrNull
                else -> el.jsonPrimitive?.contentOrNull
            }?.toString()?.trim()

            if (raw.isNullOrBlank()) return null

            // Normalize protocol-relative and host-only URLs:
            // - //host/..  -> https://host/..
            // - host/...  -> https://host/...
            // - /path/... -> leave as-is (relative to API, don't assume)
            return when {
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("http://") || raw.startsWith("https://") -> raw
                // If it looks like host-only (contains a dot and no leading slash), add https://
                raw.matches(Regex("^[A-Za-z0-9.-]+/.*")) || raw.contains('.') && !raw.startsWith('/') -> "https://$raw"
                else -> raw
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun authUrl(): android.net.Uri =
            "https://trakt.tv/oauth/authorize".toUri().buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", Trakt.CLIENT_ID)
                .appendQueryParameter("redirect_uri", Trakt.REDIRECT_URI)
                // Request sync scope so obtained token has permissions to write ratings/history.
                .appendQueryParameter("scope", Trakt.SCOPES)
                .build()
    }

    private val authClient by lazy {
        client.newBuilder()
            .addInterceptor(interceptor)
            .build()
    }

    fun buildSearchRequest(query: String): Request {
        // Request extended data so overview and images are included in search results.
        return Request.Builder()
            .url(
                "$baseUrl/search/movie,show?extended=full,images&limit=20&query=${java.net.URLEncoder.encode(
                    query,
                    "utf-8",
                )}",
            )
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", Trakt.CLIENT_ID)
            .header("User-Agent", "Animetail v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .get()
            .build()
    }

    fun search(query: String): List<TraktSearchResult> {
        val request = buildSearchRequest(query)
        // Use a client without cookies for public search requests to avoid Cloudflare/session cookie issues.
        val noCookieClient = client.newBuilder().cookieJar(okhttp3.CookieJar.NO_COOKIES).build()
        val response = noCookieClient.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        try {
            Log.d("TraktApi", "search response for query='$query': $body")
        } catch (_: Exception) {}
        return json.decodeFromString(body)
    }

    fun updateShowProgress(syncShow: eu.kanade.tachiyomi.data.track.trakt.dto.TraktSyncShow): Boolean {
        // Backwards-compatible method that sends a show-level progress/status sync.
        val syncRequest = eu.kanade.tachiyomi.data.track.trakt.dto.TraktSyncRequest(shows = listOf(syncShow))
        val requestBody = json.encodeToString(
            eu.kanade.tachiyomi.data.track.trakt.dto.TraktSyncRequest.serializer(),
            syncRequest,
        )
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/sync/history")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", Trakt.CLIENT_ID)
            .header("User-Agent", "Animetail v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .post(requestBody)
            .build()
        val response = authClient.newCall(request).execute()
        return response.isSuccessful
    }

    /**
     * Sync a single episode as watched for a show.
     * Uses the /sync/history endpoint with a "shows" payload containing seasons/episodes.
     * season fallback: 1 when caller doesn't provide season info.
     */
    fun updateShowEpisodeProgress(traktId: Long, season: Int? = null, episode: Int): Boolean {
        // Log incoming params to help debug "only sending ep 1" issues.
        try {
            Log.d(
                "TraktApi",
                "updateShowEpisodeProgress called: traktId=$traktId seasonParam=${season?.toString() ?: "null"} episodeParam=$episode",
            )
        } catch (_: Exception) {}
        // If caller provides a season, send it directly. Otherwise attempt to determine the season
        // by fetching the show's seasons (with episodes) and mapping the episode index to the
        // correct season based on cumulative episode counts.
        // Determine season number and episode index within that season.
        var seasonNumber: Int
        var episodeNumberInSeason: Int
        if (season != null) {
            seasonNumber = season
            episodeNumberInSeason = episode
        } else {
            try {
                val seasonsReq = Request.Builder()
                    .url("$baseUrl/shows/$traktId/seasons?extended=episodes")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("trakt-api-version", "2")
                    .addHeader("trakt-api-key", Trakt.CLIENT_ID)
                    .header("User-Agent", "Animetail v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
                    .get()
                    .build()
                val seasonsResp = authClient.newCall(seasonsReq).execute()
                val seasonsBodyRaw = seasonsResp.body?.string()
                // If we couldn't read a response body, fall back to defaults but provide an empty array
                // so subsequent parsing/iteration is safe.
                val root = if (seasonsBodyRaw == null) {
                    seasonNumber = 1
                    episodeNumberInSeason = episode
                    kotlinx.serialization.json.JsonArray(emptyList())
                } else {
                    // Ensure we work with a String for logging/parsing to avoid platform-type issues.
                    val seasonsBody = seasonsBodyRaw.toString()
                    try {
                        // Log a truncated seasons response for debugging (limit to 2000 chars).
                        val snippet = if (seasonsBody.length > 2000) seasonsBody.substring(0, 2000) else seasonsBody
                        Log.d("TraktApi", "seasons response (traktId=$traktId): $snippet")
                    } catch (_: Exception) {}
                    try {
                        json.parseToJsonElement(seasonsBody).jsonArray
                    } catch (_: Exception) {
                        JsonArray(emptyList())
                    }
                }

                // First attempt: find a season that contains an episode with "number" == episode
                // (handles cases where last_episode_seen is per-season, e.g. S3E5 -> episode = 5).
                var seasonMatchByNumber: Int? = null
                root.forEach { seasonEl ->
                    try {
                        val seasonObj = seasonEl.jsonObject
                        val seasonNum = seasonObj["number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                        if (seasonNum == 0) return@forEach // skip specials
                        val episodesArr = seasonObj["episodes"]?.jsonArray
                        if (episodesArr != null) {
                            val hasMatch = episodesArr.any { epEl ->
                                try {
                                    epEl.jsonObject["number"]?.jsonPrimitive?.intOrNull == episode
                                } catch (_: Exception) {
                                    false
                                }
                            }
                            if (hasMatch) {
                                // prefer the highest season number when multiple seasons report the same episode number
                                seasonMatchByNumber = seasonNum
                            }
                        }
                    } catch (_: Exception) {
                        // ignore and continue
                    }
                }
                if (seasonMatchByNumber != null) {
                    seasonNumber = seasonMatchByNumber
                    episodeNumberInSeason = episode
                } else {
                    // Fallback: map episode as a global index across seasons (existing behavior),
                    // prefer explicit "episode_count" when available and skip specials (season 0).
                    var cumulative = 0
                    var foundSeasonLocal: Int? = null
                    var epInSeasonLocal = episode
                    root.forEach { seasonEl ->
                        try {
                            val seasonObj = seasonEl.jsonObject
                            val seasonNum = seasonObj["number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                            val episodesArr = seasonObj["episodes"]?.jsonArray
                            val count = seasonObj["episode_count"]?.jsonPrimitive?.intOrNull ?: episodesArr?.size ?: 0
                            if (seasonNum == 0 || count <= 0) return@forEach
                            val start = cumulative + 1
                            val end = cumulative + count
                            if (episode in start..end) {
                                foundSeasonLocal = seasonNum
                                epInSeasonLocal = episode - cumulative
                                return@forEach
                            }
                            cumulative += count
                        } catch (_: Exception) {
                            // ignore and continue
                        }
                    }
                    seasonNumber = foundSeasonLocal ?: 1
                    episodeNumberInSeason = epInSeasonLocal
                }
            } catch (_: Exception) {
                seasonNumber = 1
                episodeNumberInSeason = episode
            }
        }

        // Debug log: record resolved season and episode-in-season to help diagnose incorrect payloads.
        try {
            Log.d(
                "TraktApi",
                "updateShowEpisodeProgress: traktId=$traktId resolvedSeason=$seasonNumber episodeInSeason=$episodeNumberInSeason",
            )
        } catch (_: Exception) {}

        val payload = """
            {
                "shows": [
                    {
                        "ids": { "trakt": $traktId },
                        "seasons": [
                            {
                                "number": $seasonNumber,
                                "episodes": [
                                    { "number": $episodeNumberInSeason }
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        try {
            val payloadStr = payload.toString()
            val payloadSnippet = payloadStr.replace("\n", "\\n")
            val shortPayload = if (payloadSnippet.length > 4000) payloadSnippet.substring(0, 4000) else payloadSnippet
            Log.d("TraktApi", "updateShowEpisodeProgress payload: " + shortPayload)
        } catch (_: Exception) {}
        val request = Request.Builder()
            .url("$baseUrl/sync/history")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", Trakt.CLIENT_ID)
            .header("User-Agent", "Animetail v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = authClient.newCall(request).execute()
        return response.isSuccessful
    }

    fun updateMovieWatched(syncMovie: eu.kanade.tachiyomi.data.track.trakt.dto.TraktSyncMovie): Boolean {
        val syncRequest = eu.kanade.tachiyomi.data.track.trakt.dto.TraktSyncRequest(movies = listOf(syncMovie))
        val requestBody = json.encodeToString(
            eu.kanade.tachiyomi.data.track.trakt.dto.TraktSyncRequest.serializer(),
            syncRequest,
        )
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/sync/history")
            .post(requestBody)
            .build()
        val response = authClient.newCall(request).execute()
        return response.isSuccessful
    }

    /**
     * Send rating(s) to Trakt.
     * Accepts optional lists of shows and movies with rating (1-10).
     * Uses POST /sync/ratings with body: { "movies":[{ "ids":{...}, "rating": n }], "shows":[...] }
     */
    fun sendRatings(
        movieRatings: List<Pair<Long, Int>> = emptyList(),
        showRatings: List<Pair<Long, Int>> = emptyList(),
    ): Boolean {
        // Build JSON payload manually to avoid changing DTOs.
        if (movieRatings.isEmpty() && showRatings.isEmpty()) return true
        val moviesJson = movieRatings.joinToString(separator = ",") { (id, rating) ->
            """{ "ids": { "trakt": $id }, "rating": $rating }"""
        }
        val showsJson = showRatings.joinToString(separator = ",") { (id, rating) ->
            """{ "ids": { "trakt": $id }, "rating": $rating }"""
        }
        val parts = mutableListOf<String>()
        if (movieRatings.isNotEmpty()) parts.add("\"movies\": [ $moviesJson ]")
        if (showRatings.isNotEmpty()) parts.add("\"shows\": [ $showsJson ]")
        val payload = "{ ${parts.joinToString(", ")} }"
        val request = Request.Builder()
            .url("$baseUrl/sync/ratings")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", Trakt.CLIENT_ID)
            .header("User-Agent", "Animetail v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = authClient.newCall(request).execute()
        return response.isSuccessful
    }

    /**
     * Check if a movie already has history entries for the authenticated user.
     * Uses GET /sync/history/movies/{id} which returns an array of history entries when present.
     */
    fun hasMovieHistory(traktId: Long): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/sync/history/movies/$traktId")
                .get()
                .build()
            val response = authClient.newCall(request).execute()
            val body = response.body?.string() ?: return false
            val root = try {
                json.parseToJsonElement(body).jsonArray
            } catch (_: Exception) {
                return false
            }
            root.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun loginOAuth(code: String, clientId: String, clientSecret: String, redirectUri: String): TraktOAuth? {
        val bodyJson = """
            {
                "code":"$code",
                "client_id":"$clientId",
                "client_secret":"$clientSecret",
                "redirect_uri":"$redirectUri",
                "grant_type":"authorization_code"
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("$baseUrl/oauth/token")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null
        return try {
            json.decodeFromString<TraktOAuth>(body)
        } catch (e: Exception) {
            null
        }
    }

    fun refreshOAuth(refreshToken: String, clientId: String, clientSecret: String): TraktOAuth? {
        val bodyJson = """
            {
                "refresh_token":"$refreshToken",
                "client_id":"$clientId",
                "client_secret":"$clientSecret",
                "grant_type":"refresh_token"
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("$baseUrl/oauth/token")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null
        return try {
            json.decodeFromString<TraktOAuth>(body)
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentUser(): String? {
        val request = Request.Builder()
            .url("$baseUrl/users/me")
            .get()
            .build()
        val response = authClient.newCall(request).execute()
        val body = response.body?.string() ?: return null
        return try {
            val parsed = json.parseToJsonElement(body).jsonObject
            parsed["username"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch watched shows from the authenticated user's account.
     * Parses each item and returns a list of TraktLibraryItem with traktId and title.
     */
    fun getUserShows(): List<eu.kanade.tachiyomi.data.track.trakt.dto.TraktLibraryItem> {
        val request = Request.Builder()
            .url("$baseUrl/sync/watched/shows")
            .get()
            .build()
        val response = authClient.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        return try {
            val root = json.parseToJsonElement(body).jsonArray
            root.mapNotNull { elem ->
                try {
                    val show = elem.jsonObject["show"]?.jsonObject ?: return@mapNotNull null
                    val ids = show["ids"]?.jsonObject
                    val traktId = ids?.get("trakt")?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val title = show["title"]?.jsonPrimitive?.contentOrNull
                    eu.kanade.tachiyomi.data.track.trakt.dto.TraktLibraryItem(
                        traktId = traktId,
                        title = title,
                        progress = 0,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetch watched movies from the authenticated user's account.
     */
    fun getUserMovies(): List<eu.kanade.tachiyomi.data.track.trakt.dto.TraktLibraryItem> {
        val request = Request.Builder()
            .url("$baseUrl/sync/watched/movies")
            .get()
            .build()
        val response = authClient.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        return try {
            val root = json.parseToJsonElement(body).jsonArray
            root.mapNotNull { elem ->
                try {
                    val movie = elem.jsonObject["movie"]?.jsonObject ?: return@mapNotNull null
                    val ids = movie["ids"]?.jsonObject
                    val traktId = ids?.get("trakt")?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val title = movie["title"]?.jsonPrimitive?.contentOrNull
                    eu.kanade.tachiyomi.data.track.trakt.dto.TraktLibraryItem(
                        traktId = traktId,
                        title = title,
                        progress = 0,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Remove a show's history entry for the authenticated user (best-effort).
     * Returns true if request succeeded.
     */
    fun removeShowHistory(traktId: Long): Boolean {
        val payload = """
            {
                "shows": [
                    {
                        "ids": { "trakt": $traktId }
                    }
                ]
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("$baseUrl/sync/history/remove")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = authClient.newCall(request).execute()
        return response.isSuccessful
    }

    /**
     * Remove a movie's history entry for the authenticated user (best-effort).
     */
    fun removeMovieHistory(traktId: Long): Boolean {
        val payload = """
            {
                "movies": [
                    {
                        "ids": { "trakt": $traktId }
                    }
                ]
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("$baseUrl/sync/history/remove")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = authClient.newCall(request).execute()
        return response.isSuccessful
    }

    /**
     * Fetch show metadata (title, overview, poster) from Trakt.
     */
    fun getShowMetadata(traktId: Long): eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata? {
        val request = Request.Builder()
            .url("$baseUrl/shows/$traktId?extended=full,images")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", Trakt.CLIENT_ID)
            .header("User-Agent", "Animetail v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .get()
            .build()
        val response = authClient.newCall(request).execute()
        val body = response.body?.string() ?: return null
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull
            val overview = obj["overview"]?.jsonPrimitive?.contentOrNull
            val images = obj["images"]?.jsonObject
            val poster = extractImageUrl(images?.get("poster"))
            eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata(
                remoteId = traktId,
                title = title,
                thumbnailUrl = poster,
                description = overview,
                authors = null,
                artists = null,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Public variant of getShowMetadata that uses a no-cookie client (no auth) and can be used
     * as a fallback when search results don't include images. Uses the same /shows/{id}?extended=full endpoint.
     */
    fun getShowMetadataPublic(traktId: Long): eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata? {
        val request = Request.Builder()
            .url("$baseUrl/shows/$traktId?extended=full,images")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", Trakt.CLIENT_ID)
            .header("User-Agent", "Animetail v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .get()
            .build()
        val noCookieClient = client.newBuilder().cookieJar(okhttp3.CookieJar.NO_COOKIES).build()
        val response = noCookieClient.newCall(request).execute()
        val body = response.body?.string() ?: return null
        try {
            Log.d("TraktApi", "getShowMetadataPublic response for id=$traktId: $body")
        } catch (_: Exception) {}
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull
            val overview = obj["overview"]?.jsonPrimitive?.contentOrNull
            val images = obj["images"]?.jsonObject
            val poster = extractImageUrl(images?.get("poster"))
            eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata(
                remoteId = traktId,
                title = title,
                thumbnailUrl = poster,
                description = overview,
                authors = null,
                artists = null,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Return total episode count for a show by summing episodes across seasons.
     * Uses the seasons endpoint with embedded episodes. Uses a no-cookie client for public requests.
     */
    fun getShowEpisodeCount(traktId: Long): Long {
        val request = Request.Builder()
            .url("$baseUrl/shows/$traktId/seasons?extended=episodes")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", Trakt.CLIENT_ID)
            .header("User-Agent", "Animetail v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .get()
            .build()
        val noCookieClient = client.newBuilder().cookieJar(okhttp3.CookieJar.NO_COOKIES).build()
        val response = noCookieClient.newCall(request).execute()
        val body = response.body?.string() ?: return 0L
        return try {
            val root = json.parseToJsonElement(body).jsonArray
            var total = 0L
            root.forEach { seasonEl ->
                try {
                    val seasonObj = seasonEl.jsonObject
                    val seasonNum = seasonObj["number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                    // Prefer explicit episode_count when available, fallback to embedded episodes array size.
                    val episodes = seasonObj["episodes"]?.jsonArray
                    val count = seasonObj["episode_count"]?.jsonPrimitive?.intOrNull ?: episodes?.size ?: 0
                    // Skip specials (season 0)
                    if (seasonNum == 0) {
                        return@forEach
                    }
                    if (count > 0) {
                        total += count.toLong()
                    }
                } catch (_: Exception) {
                }
            }
            total
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Fetch movie metadata (title, overview, poster) from Trakt.
     */
    fun getMovieMetadata(traktId: Long): eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata? {
        val request = Request.Builder()
            .url("$baseUrl/movies/$traktId?extended=full,images")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", Trakt.CLIENT_ID)
            .header("User-Agent", "Animetail v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .get()
            .build()
        val response = authClient.newCall(request).execute()
        val body = response.body?.string() ?: return null
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull
            val overview = obj["overview"]?.jsonPrimitive?.contentOrNull
            val images = obj["images"]?.jsonObject
            val poster = extractImageUrl(images?.get("poster"))
            eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata(
                remoteId = traktId,
                title = title,
                thumbnailUrl = poster,
                description = overview,
                authors = null,
                artists = null,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Public variant of getMovieMetadata that uses a no-cookie client (no auth) and can be used
     * as a fallback when search results don't include images. Uses the same /movies/{id}?extended=full,images endpoint.
     */
    fun getMovieMetadataPublic(traktId: Long): eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata? {
        val request = Request.Builder()
            .url("$baseUrl/movies/$traktId?extended=full,images")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", Trakt.CLIENT_ID)
            .header("User-Agent", "Animetail v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .get()
            .build()
        val noCookieClient = client.newBuilder().cookieJar(okhttp3.CookieJar.NO_COOKIES).build()
        val response = noCookieClient.newCall(request).execute()
        val body = response.body?.string() ?: return null
        try {
            Log.d("TraktApi", "getMovieMetadataPublic response for id=$traktId: $body")
        } catch (_: Exception) {}
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull
            val overview = obj["overview"]?.jsonPrimitive?.contentOrNull
            val images = obj["images"]?.jsonObject
            val poster = extractImageUrl(images?.get("poster"))
            eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata(
                remoteId = traktId,
                title = title,
                thumbnailUrl = poster,
                description = overview,
                authors = null,
                artists = null,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch cast for a show (best-effort). Maps to Credit objects with name, role and image_url.
     */
    fun getShowCast(traktId: Long): List<eu.kanade.tachiyomi.animesource.model.Credit>? {
        val request = Request.Builder()
            .url("$baseUrl/shows/$traktId/people?extended=images")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", Trakt.CLIENT_ID)
            .header("User-Agent", "Animetail v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .get()
            .build()
        // Use no-cookie client for public people/cast endpoint to avoid auth/cookie-related 403s.
        val noCookieClient = client.newBuilder().cookieJar(okhttp3.CookieJar.NO_COOKIES).build()
        val response = noCookieClient.newCall(request).execute()
        val body = response.body?.string() ?: return null
        try {
            Log.d("TraktApi", "getShowCast response for id=$traktId: $body")
        } catch (_: Exception) {}
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val castArr = root["cast"]?.jsonArray ?: return null
            castArr.mapNotNull { el ->
                try {
                    val obj = el.jsonObject
                    val person = obj["person"]?.jsonObject
                    val character = obj["character"]?.jsonPrimitive?.contentOrNull
                    val name = person?.get("name")?.jsonPrimitive?.contentOrNull
                    val imagesEl = person?.get("images")
                    var imageUrl: String? = null
                    try {
                        val headshotEl = imagesEl?.jsonObject?.get("headshot") ?: imagesEl?.jsonArray?.firstOrNull()
                            ?: imagesEl?.jsonPrimitive
                        imageUrl = extractImageUrl(headshotEl)
                        // Prepend scheme if API returns protocol-relative URLs.
                        imageUrl = imageUrl?.let { u -> if (u.startsWith("http")) u else "https://$u" }
                    } catch (_: Exception) {
                    }
                    if (name.isNullOrBlank()) return@mapNotNull null
                    eu.kanade.tachiyomi.animesource.model.Credit(
                        name = name,
                        role = character,
                        character = character,
                        image_url = imageUrl,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
