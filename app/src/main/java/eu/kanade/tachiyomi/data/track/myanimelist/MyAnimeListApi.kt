package eu.kanade.tachiyomi.data.track.myanimelist

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALAnime
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALAnimeMetadata
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListAnimeItem
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListAnimeItemStatus
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListMangaItem
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListMangaItemStatus
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALManga
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMangaMetadata
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALAnimeSearchResult
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALSearchResult
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALUser
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

class MyAnimeListApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: MyAnimeListInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun getAccessToken(authCode: String): MALOAuth {
        return withIOContext {
            val formBody: RequestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("code", authCode)
                .add("code_verifier", codeVerifier)
                .add("grant_type", "authorization_code")
                .build()
            with(json) {
                client.newCall(POST("$BASE_OAUTH_URL/token", body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    suspend fun getCurrentUser(): String {
        return withIOContext {
            val request = Request.Builder()
                .url("$BASE_API_URL/users/@me")
                .get()
                .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs<MALUser>()
                    .name
            }
        }
    }

    suspend fun search(query: String): List<MangaTrackSearch> {
        return withIOContext {
            val url = "$BASE_API_URL/manga".toUri().buildUpon()
                // MAL API throws a 400 when the query is over 64 characters...
                .appendQueryParameter("q", query.take(64))
                .appendQueryParameter("nsfw", "true")
                .appendQueryParameter("fields", SEARCH_FIELDS)
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALSearchResult>()
                    .data
                    .filter { !(it.node.mediaType.contains("novel")) }
                    .map { parseSearchItem(it.node) }
            }
        }
    }

    suspend fun searchAnime(query: String): List<AnimeTrackSearch> {
        return withIOContext {
            val url = "$BASE_API_URL/anime".toUri().buildUpon()
                // MAL API throws a 400 when the query is over 64 characters...
                .appendQueryParameter("q", query.take(64))
                .appendQueryParameter("nsfw", "true")
                .appendQueryParameter("fields", SEARCH_FIELDS_ANIME)
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALAnimeSearchResult>()
                    .data
                    .map { parseAnimeSearchItem(it.node) }
            }
        }
    }

    suspend fun getMangaDetails(id: Int): MangaTrackSearch {
        return withIOContext {
            val url = "$BASE_API_URL/manga".toUri().buildUpon()
                .appendPath(id.toString())
                .appendQueryParameter("fields", SEARCH_FIELDS)
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALManga>()
                    .let { parseSearchItem(it) }
            }
        }
    }

    suspend fun getAnimeDetails(id: Int): AnimeTrackSearch {
        return withIOContext {
            val url = "$BASE_API_URL/anime".toUri().buildUpon()
                .appendPath(id.toString())
                .appendQueryParameter("fields", SEARCH_FIELDS_ANIME)
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALAnime>()
                    .let { parseAnimeSearchItem(it) }
            }
        }
    }

    suspend fun updateItem(track: MangaTrack): MangaTrack {
        return withIOContext {
            val formBodyBuilder = FormBody.Builder()
                .add("status", track.toMyAnimeListStatus() ?: "reading")
                .add("is_rereading", (track.status == MyAnimeList.REREADING).toString())
                .add("score", track.score.toString())
                .add("num_chapters_read", track.last_chapter_read.toInt().toString())
            convertToIsoDate(track.started_reading_date)?.let {
                formBodyBuilder.add("start_date", it)
            }
            convertToIsoDate(track.finished_reading_date)?.let {
                formBodyBuilder.add("finish_date", it)
            }

            val request = Request.Builder()
                .url(mangaUrl(track.remote_id).toString())
                .put(formBodyBuilder.build())
                .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs<MALListMangaItemStatus>()
                    .let { parseMangaItem(it, track) }
            }
        }
    }

    suspend fun updateItem(track: AnimeTrack): AnimeTrack {
        return withIOContext {
            val formBodyBuilder = FormBody.Builder()
                .add("status", track.toMyAnimeListStatus() ?: "watching")
                .add("is_rewatching", (track.status == MyAnimeList.REWATCHING).toString())
                .add("score", track.score.toString())
                .add("num_watched_episodes", track.last_episode_seen.toInt().toString())
            convertToIsoDate(track.started_watching_date)?.let {
                formBodyBuilder.add("start_date", it)
            }
            convertToIsoDate(track.finished_watching_date)?.let {
                formBodyBuilder.add("finish_date", it)
            }

            val request = Request.Builder()
                .url(animeUrl(track.remote_id).toString())
                .put(formBodyBuilder.build())
                .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs<MALListAnimeItemStatus>()
                    .let { parseAnimeItem(it, track) }
            }
        }
    }

    suspend fun deleteMangaItem(track: DomainMangaTrack) {
        withIOContext {
            authClient
                .newCall(DELETE(mangaUrl(track.remoteId).toString()))
                .awaitSuccess()
        }
    }

    suspend fun deleteAnimeItem(track: DomainAnimeTrack) {
        withIOContext {
            authClient
                .newCall(DELETE(animeUrl(track.remoteId).toString()))
                .awaitSuccess()
        }
    }

    suspend fun findListItem(track: MangaTrack): MangaTrack? {
        return withIOContext {
            val uri = "$BASE_API_URL/manga".toUri().buildUpon()
                .appendPath(track.remote_id.toString())
                .appendQueryParameter("fields", "num_chapters,my_list_status{start_date,finish_date}")
                .build()
            with(json) {
                authClient.newCall(GET(uri.toString()))
                    .awaitSuccess()
                    .parseAs<MALListMangaItem>()
                    .let { item ->
                        track.total_chapters = item.numChapters
                        item.myListStatus?.let { parseMangaItem(it, track) }
                    }
            }
        }
    }

    suspend fun findListItem(track: AnimeTrack): AnimeTrack? {
        return withIOContext {
            val uri = "$BASE_API_URL/anime".toUri().buildUpon()
                .appendPath(track.remote_id.toString())
                .appendQueryParameter("fields", "num_episodes,my_list_status{start_date,finish_date}")
                .build()
            with(json) {
                authClient.newCall(GET(uri.toString()))
                    .awaitSuccess()
                    .parseAs<MALListAnimeItem>()
                    .let { item ->
                        track.total_episodes = item.numEpisodes
                        item.myListStatus?.let { parseAnimeItem(it, track) }
                    }
            }
        }
    }

    suspend fun findListItems(query: String, offset: Int = 0): List<MangaTrackSearch> {
        return withIOContext {
            val myListSearchResult = getListPage(offset)

            val matches = myListSearchResult.data
                .filter { it.node.title.contains(query, ignoreCase = true) }
                .map { parseSearchItem(it.node) }

            // Check next page if there's more
            if (!myListSearchResult.paging.next.isNullOrBlank()) {
                matches + findListItems(query, offset + LIST_PAGINATION_AMOUNT)
            } else {
                matches
            }
        }
    }

    suspend fun findListItemsAnime(query: String, offset: Int = 0): List<AnimeTrackSearch> {
        return withIOContext {
            val myListSearchResult = getAnimeListPage(offset)

            val matches = myListSearchResult.data
                .filter { it.node.title.contains(query, ignoreCase = true) }
                .map { parseAnimeSearchItem(it.node) }

            // Check next page if there's more
            if (!myListSearchResult.paging.next.isNullOrBlank()) {
                matches + findListItemsAnime(query, offset + LIST_PAGINATION_AMOUNT)
            } else {
                matches
            }
        }
    }

    suspend fun getAnimeMetadata(track: DomainAnimeTrack): TrackAnimeMetadata? {
        return withIOContext {
            val url = "$BASE_API_URL/anime".toUri().buildUpon()
                .appendPath(track.remoteId.toString())
                .appendQueryParameter(
                    "fields",
                    "id,title,synopsis,main_picture,studios{name}",
                )
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALAnimeMetadata>()
                    .let { it ->
                        TrackAnimeMetadata(
                            remoteId = it.id,
                            title = it.title,
                            thumbnailUrl = it.covers.large.ifEmpty { null } ?: it.covers.medium,
                            description = it.synopsis,
                            authors = it.studios
                                .map { it.name.trim() }
                                .joinToString(separator = ", ")
                                .ifEmpty { null },
                            artists = it.studios
                                .map { it.name.trim() }
                                .joinToString(separator = ", ")
                                .ifEmpty { null },
                        )
                    }
            }
        }
    }

    suspend fun getMangaMetadata(track: DomainMangaTrack): TrackMangaMetadata? {
        return withIOContext {
            val url = "$BASE_API_URL/manga".toUri().buildUpon()
                .appendPath(track.remoteId.toString())
                .appendQueryParameter(
                    "fields",
                    "id,title,synopsis,main_picture,authors{first_name,last_name}",
                )
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALMangaMetadata>()
                    .let {
                        TrackMangaMetadata(
                            remoteId = it.id,
                            title = it.title,
                            thumbnailUrl = it.covers.large.ifEmpty { null } ?: it.covers.medium,
                            description = it.synopsis,
                            authors = it.authors
                                .filter { it.role == "Story" || it.role == "Story & Art" }
                                .map { "${it.node.firstName} ${it.node.lastName}".trim() }
                                .joinToString(separator = ", ")
                                .ifEmpty { null },
                            artists = it.authors
                                .filter { it.role == "Art" || it.role == "Story & Art" }
                                .map { "${it.node.firstName} ${it.node.lastName}".trim() }
                                .joinToString(separator = ", ")
                                .ifEmpty { null },
                        )
                    }
            }
        }
    }

    private suspend fun getListPage(offset: Int): MALSearchResult {
        return withIOContext {
            val urlBuilder = "$BASE_API_URL/users/@me/mangalist".toUri().buildUpon()
                .appendQueryParameter("fields", SEARCH_FIELDS)
                .appendQueryParameter("limit", LIST_PAGINATION_AMOUNT.toString())
            if (offset > 0) {
                urlBuilder.appendQueryParameter("offset", offset.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build().toString())
                .get()
                .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private suspend fun getAnimeListPage(offset: Int): MALAnimeSearchResult {
        return withIOContext {
            val urlBuilder = "$BASE_API_URL/users/@me/animelist".toUri().buildUpon()
                .appendQueryParameter("fields", SEARCH_FIELDS_ANIME)
                .appendQueryParameter("limit", LIST_PAGINATION_AMOUNT.toString())
            if (offset > 0) {
                urlBuilder.appendQueryParameter("offset", offset.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build().toString())
                .get()
                .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun parseMangaItem(listStatus: MALListMangaItemStatus, track: MangaTrack): MangaTrack {
        return track.apply {
            val isRereading = listStatus.isRereading
            status = if (isRereading) MyAnimeList.REREADING else getStatus(listStatus.status)
            last_chapter_read = listStatus.numChaptersRead
            score = listStatus.score.toDouble()
            listStatus.startDate?.let { started_reading_date = parseDate(it) }
            listStatus.finishDate?.let { finished_reading_date = parseDate(it) }
        }
    }

    private fun parseAnimeItem(listStatus: MALListAnimeItemStatus, track: AnimeTrack): AnimeTrack {
        return track.apply {
            val isRewatching = listStatus.isRewatching
            status = if (isRewatching) MyAnimeList.REWATCHING else getStatus(listStatus.status)
            last_episode_seen = listStatus.numEpisodesWatched
            score = listStatus.score.toDouble()
            listStatus.startDate?.let { started_watching_date = parseDate(it) }
            listStatus.finishDate?.let { finished_watching_date = parseDate(it) }
        }
    }

    private fun parseAnimeSearchItem(searchItem: MALAnime): AnimeTrackSearch {
        return AnimeTrackSearch.create(trackId).apply {
            remote_id = searchItem.id
            title = searchItem.title
            summary = searchItem.synopsis
            total_episodes = searchItem.numEpisodes
            score = searchItem.mean
            cover_url = searchItem.covers?.large.orEmpty()
            tracking_url = "https://myanimelist.net/anime/$remote_id"
            publishing_status = searchItem.status.replace("_", " ")
            publishing_type = searchItem.mediaType.replace("_", " ")
            start_date = searchItem.startDate ?: ""
            authors = searchItem.studios.map { it.name }
            artists = searchItem.studios.map { it.name }
        }
    }

    private fun parseSearchItem(searchItem: MALManga): MangaTrackSearch {
        return MangaTrackSearch.create(trackId).apply {
            remote_id = searchItem.id
            title = searchItem.title
            summary = searchItem.synopsis
            total_chapters = searchItem.numChapters
            score = searchItem.mean
            cover_url = searchItem.covers?.large.orEmpty()
            tracking_url = "https://myanimelist.net/manga/$remote_id"
            publishing_status = searchItem.status.replace("_", " ")
            publishing_type = searchItem.mediaType.replace("_", " ")
            start_date = searchItem.startDate ?: ""
        }
    }

    private fun parseDate(isoDate: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)?.time ?: 0L
    }

    private fun convertToIsoDate(epochTime: Long): String? {
        if (epochTime == 0L) {
            return ""
        }
        return try {
            val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            outputDf.format(epochTime)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val CLIENT_ID = "18e5087eaeef557833a075a4d30d2afe"

        private const val BASE_OAUTH_URL = "https://myanimelist.net/v1/oauth2"
        private const val BASE_API_URL = "https://api.myanimelist.net/v2"

        private const val SEARCH_FIELDS =
            "id,title,synopsis,num_chapters,mean,main_picture,status,media_type,start_date"
        private const val SEARCH_FIELDS_ANIME =
            "id,title,synopsis,num_episodes,mean,main_picture,status,media_type,start_date,studios"

        private const val LIST_PAGINATION_AMOUNT = 250

        private var codeVerifier: String = ""

        fun authUrl(): Uri = "$BASE_OAUTH_URL/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("code_challenge", getPkceChallengeCode())
            .appendQueryParameter("response_type", "code")
            .build()

        fun mangaUrl(id: Long): Uri = "$BASE_API_URL/manga".toUri().buildUpon()
            .appendPath(id.toString())
            .appendPath("my_list_status")
            .build()

        fun animeUrl(id: Long): Uri = "$BASE_API_URL/anime".toUri().buildUpon()
            .appendPath(id.toString())
            .appendPath("my_list_status")
            .build()

        fun refreshTokenRequest(oauth: MALOAuth): Request {
            val formBody: RequestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("refresh_token", oauth.refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            // Add the Authorization header manually as this particular
            // request is called by the interceptor itself so it doesn't reach
            // the part where the token is added automatically.
            val headers = Headers.Builder()
                .add("Authorization", "Bearer ${oauth.accessToken}")
                .build()

            return POST("$BASE_OAUTH_URL/token", body = formBody, headers = headers)
        }

        private fun getPkceChallengeCode(): String {
            codeVerifier = PkceUtil.generateCodeVerifier()
            return codeVerifier
        }
    }
}
