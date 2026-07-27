package eu.kanade.tachiyomi.data.track.hikka

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.hikka.dto.HKManga
import eu.kanade.tachiyomi.data.track.hikka.dto.HKMangaPagination
import eu.kanade.tachiyomi.data.track.hikka.dto.HKOAuth
import eu.kanade.tachiyomi.data.track.hikka.dto.HKRead
import eu.kanade.tachiyomi.data.track.hikka.dto.HKUser
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

class HikkaApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: HikkaInterceptor,
) {
    suspend fun getCurrentUser(): HKUser {
        return withIOContext {
            val request = Request.Builder()
                .url("${BASE_API_URL}/user/me")
                .get()
                .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs<HKUser>()
            }
        }
    }

    suspend fun accessToken(reference: String): HKOAuth {
        return withIOContext {
            with(json) {
                client.newCall(authTokenCreate(reference))
                    .awaitSuccess()
                    .parseAs<HKOAuth>()
            }
        }
    }

    suspend fun searchManga(query: String): List<MangaTrackSearch> {
        return withIOContext {
            val url = "$BASE_API_URL/manga".toUri().buildUpon()
                .appendQueryParameter("page", "1")
                .appendQueryParameter("size", "50")
                .build()

            val payload = buildJsonObject {
                put("media_type", buildJsonArray { add("manga") })
                put("status", buildJsonArray { })
                put("only_translated", false)
                put("magazines", buildJsonArray { })
                put("genres", buildJsonArray { })
                put(
                    "score",
                    buildJsonArray {
                        add(0)
                        add(10)
                    },
                )
                put("query", query)
                put(
                    "sort",
                    buildJsonArray {
                        add("score:desc")
                        add("scored_by:desc")
                    },
                )
            }

            with(json) {
                authClient.newCall(POST(url.toString(), body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .parseAs<HKMangaPagination>()
                    .list
                    .map { it.toTrack(trackId) }
            }
        }
    }

    suspend fun searchAnime(query: String): List<AnimeTrackSearch> {
        return withIOContext {
            val url = "$BASE_API_URL/anime".toUri().buildUpon()
                .appendQueryParameter("page", "1")
                .appendQueryParameter("size", "50")
                .build()

            val payload = buildJsonObject {
                put("media_type", buildJsonArray { add("anime") })
                put("status", buildJsonArray { })
                put("only_translated", false)
                put("magazines", buildJsonArray { })
                put("genres", buildJsonArray { })
                put(
                    "score",
                    buildJsonArray {
                        add(0)
                        add(10)
                    },
                )
                put("query", query)
                put(
                    "sort",
                    buildJsonArray {
                        add("score:desc")
                        add("scored_by:desc")
                    },
                )
            }

            with(json) {
                authClient.newCall(POST(url.toString(), body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .parseAs<HKMangaPagination>()
                    .list
                    .map { it.toAnimeTrack(trackId) }
            }
        }
    }

    suspend fun getRead(track: MangaTrack): HKRead? {
        return withIOContext {
            val slug = track.tracking_url.split("/")[4]
            val url = "$BASE_API_URL/read/manga/$slug".toUri().buildUpon().build()
            with(json) {
                try {
                    authClient.newCall(GET(url.toString()))
                        .awaitSuccess()
                        .parseAs<HKRead>()
                } catch (e: HttpException) {
                    if (e.code == 404) {
                        null
                    } else {
                        throw e
                    }
                }
            }
        }
    }

    suspend fun getWatch(track: AnimeTrack): HKRead? {
        return withIOContext {
            val slug = track.tracking_url.split("/")[4]
            val url = "$BASE_API_URL/read/anime/$slug".toUri().buildUpon().build()
            with(json) {
                try {
                    authClient.newCall(GET(url.toString()))
                        .awaitSuccess()
                        .parseAs<HKRead>()
                } catch (e: HttpException) {
                    if (e.code == 404) {
                        null
                    } else {
                        throw e
                    }
                }
            }
        }
    }

    suspend fun getManga(track: MangaTrack): MangaTrackSearch {
        return withIOContext {
            val slug = track.tracking_url.split("/")[4]
            val url = "$BASE_API_URL/manga/$slug".toUri().buildUpon()
                .build()

            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<HKManga>()
                    .toTrack(trackId)
            }
        }
    }

    suspend fun getAnime(track: AnimeTrack): AnimeTrackSearch {
        return withIOContext {
            val slug = track.tracking_url.split("/")[4]
            val url = "$BASE_API_URL/anime/$slug".toUri().buildUpon()
                .build()

            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<HKManga>()
                    .toAnimeTrack(trackId)
            }
        }
    }

    suspend fun deleteUserManga(track: DomainMangaTrack) {
        return withIOContext {
            val slug = track.remoteUrl.split("/")[4]

            val url = "$BASE_API_URL/read/manga/$slug".toUri().buildUpon()
                .build()

            authClient.newCall(DELETE(url.toString()))
                .awaitSuccess()
        }
    }

    suspend fun deleteUserAnime(track: DomainAnimeTrack) {
        return withIOContext {
            val slug = track.remoteUrl.split("/")[4]

            val url = "$BASE_API_URL/read/anime/$slug".toUri().buildUpon()
                .build()

            authClient.newCall(DELETE(url.toString()))
                .awaitSuccess()
        }
    }

    suspend fun addUserManga(track: MangaTrack): MangaTrack {
        return withIOContext {
            val slug = track.tracking_url.split("/")[4]

            val url = "$BASE_API_URL/read/manga/$slug".toUri().buildUpon()
                .build()

            var rereads = getRead(track)?.rereads ?: 0
            if (track.status == Hikka.REREADING && rereads == 0) {
                rereads = 1
            }

            val payload = buildJsonObject {
                put("note", "")
                put("chapters", track.last_chapter_read.toInt())
                put("volumes", 0)
                put("rereads", rereads)
                put("score", track.score.toInt())
                put("status", track.toApiStatus())
                put("start_date", if (track.started_reading_date > 0L) track.started_reading_date / 1000 else null)
                put("end_date", if (track.finished_reading_date > 0L) track.finished_reading_date / 1000 else null)
            }

            with(json) {
                authClient.newCall(PUT(url.toString(), body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .parseAs<HKRead>()
                    .toTrack(trackId)
            }
        }
    }

    suspend fun addUserAnime(track: AnimeTrack): AnimeTrack {
        return withIOContext {
            val slug = track.tracking_url.split("/")[4]

            val url = "$BASE_API_URL/read/anime/$slug".toUri().buildUpon()
                .build()

            var rereads = getWatch(track)?.rereads ?: 0
            if (track.status == Hikka.REWATCHING && rereads == 0) {
                rereads = 1
            }

            val payload = buildJsonObject {
                put("note", "")
                put("chapters", track.last_episode_seen.toInt())
                put("volumes", 0)
                put("rereads", rereads)
                put("score", track.score.toInt())
                put("status", track.toApiStatus())
                put("start_date", if (track.started_watching_date > 0L) track.started_watching_date / 1000 else null)
                put("end_date", if (track.finished_watching_date > 0L) track.finished_watching_date / 1000 else null)
            }

            with(json) {
                authClient.newCall(PUT(url.toString(), body = payload.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .parseAs<HKRead>()
                    .toAnimeTrack(trackId)
            }
        }
    }

    suspend fun updateUserManga(track: MangaTrack): MangaTrack = addUserManga(track)

    suspend fun updateUserAnime(track: AnimeTrack): AnimeTrack = addUserAnime(track)

    private val json: Json by injectLazy()
    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    companion object {
        const val BASE_API_URL = "https://api.hikka.io"
        const val BASE_URL = "https://hikka.io"
        private const val SCOPE = "readlist,read:user-details"
        private const val CLIENT_REFERENCE = "56bd4306-e440-4307-864a-8c702d580cd2"
        private const val CLIENT_SECRET = "pSg-GAnsKRdZS-EZZmV-dk27Vp5Yes3P9Lrd9a2qzWut" +
            "jNJTWIHr4Ll2sD8kzOKcFJFl" +
            "_KhFg5C0Tjq9pU4dqWo4vKkWsZpaue71ZOXt" +
            "-nAx1mrytoPOdG6nVV1pCJWf"

        fun authUrl(): Uri = "$BASE_URL/oauth".toUri().buildUpon()
            .appendQueryParameter("reference", CLIENT_REFERENCE)
            .appendQueryParameter("scope", SCOPE)
            .build()

        fun refreshTokenRequest(accessToken: String): Request {
            val headers = Headers.Builder()
                .add("auth", accessToken)
                .build()

            return GET("$BASE_API_URL/user/me", headers = headers) // Any request with auth
        }

        fun authTokenCreate(reference: String): Request {
            val payload = buildJsonObject {
                put("request_reference", reference)
                put("client_secret", CLIENT_SECRET)
            }
            return POST("$BASE_API_URL/auth/token", body = payload.toString().toRequestBody(jsonMime))
        }

        fun authTokenInfo(accessToken: String): Request {
            val headers = Headers.Builder()
                .add("auth", accessToken)
                .build()

            return GET("$BASE_API_URL/auth/token/info", headers = headers)
        }
    }
}
