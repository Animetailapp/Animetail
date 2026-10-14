package eu.kanade.tachiyomi.data.track.shikimori

import android.net.Uri
import androidx.core.net.toUri
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMLibraryIdResponse
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMOAuth
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUser
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.dataOrElse
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mihon.graphql.shikimori.ShikimoriGetAnimeDetailsQuery
import mihon.graphql.shikimori.ShikimoriGetAnimeMetadataQuery
import mihon.graphql.shikimori.ShikimoriGetCurrentUserQuery
import mihon.graphql.shikimori.ShikimoriGetLibAnimeQuery
import mihon.graphql.shikimori.ShikimoriGetLibMangaQuery
import mihon.graphql.shikimori.ShikimoriGetMangaDetailsQuery
import mihon.graphql.shikimori.ShikimoriGetMangaMetadataQuery
import mihon.graphql.shikimori.ShikimoriSearchAnimeQuery
import mihon.graphql.shikimori.ShikimoriSearchMangaQuery
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

class ShikimoriApi(
    private val trackerId: Long,
    private val client: OkHttpClient,
    interceptor: ShikimoriInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    private val graphQlClient by lazy {
        ApolloClient.Builder()
            .serverUrl("$API_URL/graphql")
            .okHttpClient(authClient)
            .dispatcher(Dispatchers.IO)
            .build()
    }

    suspend fun addLibManga(track: MangaTrack, userId: String): MangaTrack {
        return withIOContext {
            with(json) {
                val payload = buildJsonObject {
                    putJsonObject("user_rate") {
                        put("user_id", userId)
                        put("target_id", track.remote_id)
                        put("target_type", "Manga")
                        put("chapters", track.last_chapter_read.toInt())
                        put("score", track.score.toInt())
                        put("status", track.toShikimoriStatus())
                    }
                }
                authClient.newCall(
                    POST(
                        "$API_URL/v2/user_rates",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                ).awaitSuccess()
                    .parseAs<SMLibraryIdResponse>()
                    .let {
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun updateLibManga(track: MangaTrack): MangaTrack {
        return withIOContext {
            val payload = buildJsonObject {
                putJsonObject("user_rate") {
                    put("chapters", track.last_chapter_read.toInt())
                    put("score", track.score.toInt())
                    put("status", track.toShikimoriStatus())
                }
            }

            with(json) {
                authClient.newCall(
                    PUT(
                        "$API_URL/v2/user_rates/${track.library_id}",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<SMLibraryIdResponse>()
                    .let {
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun deleteLibManga(track: DomainMangaTrack) {
        withIOContext {
            authClient
                .newCall(DELETE("$API_URL/v2/user_rates/${track.libraryId}"))
                .awaitSuccess()
        }
    }

    suspend fun addLibAnime(track: AnimeTrack, userId: String): AnimeTrack {
        return withIOContext {
            with(json) {
                val payload = buildJsonObject {
                    putJsonObject("user_rate") {
                        put("user_id", userId)
                        put("target_id", track.remote_id)
                        put("target_type", "Anime")
                        put("episodes", track.last_episode_seen.toInt())
                        put("score", track.score.toInt())
                        put("status", track.toShikimoriStatus())
                    }
                }
                authClient.newCall(
                    POST(
                        "$API_URL/v2/user_rates",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                ).awaitSuccess()
                    .parseAs<SMLibraryIdResponse>()
                    .let {
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun updateLibAnime(track: AnimeTrack): AnimeTrack {
        return withIOContext {
            val payload = buildJsonObject {
                putJsonObject("user_rate") {
                    put("episodes", track.last_episode_seen.toInt())
                    put("score", track.score.toInt())
                    put("status", track.toShikimoriStatus())
                }
            }

            with(json) {
                authClient.newCall(
                    PUT(
                        "$API_URL/v2/user_rates/${track.library_id}",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<SMLibraryIdResponse>()
                    .let {
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun deleteLibAnime(track: DomainAnimeTrack) {
        withIOContext {
            authClient
                .newCall(DELETE("$API_URL/v2/user_rates/${track.libraryId}"))
                .awaitSuccess()
        }
    }

    suspend fun search(search: String): List<MangaTrackSearch> {
        return graphQlClient
            .query(
                ShikimoriSearchMangaQuery(search = search),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Search failed",
                default = { emptyList() },
            ) {
                it.mangas.map { manga ->
                    manga.toTrackSearch(trackerId)
                }
            }
    }

    suspend fun getMangaDetails(id: Int): MangaTrackSearch? {
        return graphQlClient
            .query(
                ShikimoriGetMangaDetailsQuery(query = "$id"),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Failed to get manga details",
                default = { null },
            ) {
                it.mangas
                    .firstOrNull()
                    ?.toTrackSearch(trackerId)
            }
    }

    suspend fun searchAnime(search: String): List<AnimeTrackSearch> {
        return graphQlClient
            .query(
                ShikimoriSearchAnimeQuery(search = search),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Anime search failed",
                default = { emptyList() },
            ) {
                it.animes.map { anime ->
                    anime.toTrackSearch(trackerId)
                }
            }
    }

    suspend fun getAnimeDetails(id: Int): AnimeTrackSearch? {
        return graphQlClient
            .query(
                ShikimoriGetAnimeDetailsQuery(query = "$id"),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Failed to get anime details",
                default = { null },
            ) {
                it.animes
                    .firstOrNull()
                    ?.toTrackSearch(trackerId)
            }
    }

    suspend fun findLibManga(track: MangaTrack): MangaTrack? {
        return graphQlClient
            .query(
                ShikimoriGetLibMangaQuery(
                    remote_id = track.remote_id.toString(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Failed to find manga in library",
                default = { null },
            ) {
                val mangaResult = it.mangas.firstOrNull()

                // Shikimori has no user list query that allows query by ID, so we go via the "mangas" query & include
                // userRate data which will be null if the title is not in the user's list.
                // If it was removed on Shikimori and is still linked in the app, notify user via returning null here
                // which throws an exception at the Shikimori.refresh call
                if (mangaResult?.userRate == null) {
                    null
                } else {
                    mangaResult.toTrack(trackerId)
                }
            }
    }

    suspend fun findLibAnime(track: AnimeTrack): AnimeTrack? {
        return graphQlClient
            .query(
                ShikimoriGetLibAnimeQuery(
                    remote_id = track.remote_id.toString(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Failed to find anime in library",
                default = { null },
            ) {
                val animeResult = it.animes.firstOrNull()

                if (animeResult?.userRate == null) {
                    null
                } else {
                    animeResult.toTrack(trackerId)
                }
            }
    }

    suspend fun getCurrentUser(): SMUser {
        return graphQlClient
            .query(
                ShikimoriGetCurrentUserQuery(),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Failed to get current user",
                default = { null },
            ) {
                it.currentUser?.let { currentUser ->
                    SMUser(id = currentUser.id, nickname = currentUser.nickname)
                }
            }
            ?: throw Exception("Failed to get Shikimori user data")
    }

    suspend fun getMangaMetadata(track: DomainMangaTrack): TrackMangaMetadata {
        return graphQlClient
            .query(
                ShikimoriGetMangaMetadataQuery(ids = "${track.remoteId}"),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Failed to get manga metadata",
                default = { null },
            ) {
                val manga = it.mangas.firstOrNull() ?: return@dataOrElse null
                TrackMangaMetadata(
                    remoteId = manga.id.toLong(),
                    title = manga.name,
                    thumbnailUrl = manga.poster?.originalUrl,
                    description = manga.description,
                    authors = manga.personRoles
                        ?.filter { role -> role.rolesEn.contains("Story") || role.rolesEn.contains("Story & Art") }
                        ?.map { role -> role.person.name }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                    artists = manga.personRoles
                        ?.filter { role -> role.rolesEn.contains("Art") || role.rolesEn.contains("Story & Art") }
                        ?.map { role -> role.person.name }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                )
            } ?: throw Exception("Could not get metadata from Shikimori")
    }

    suspend fun getAnimeMetadata(track: DomainAnimeTrack): TrackAnimeMetadata {
        return graphQlClient
            .query(
                ShikimoriGetAnimeMetadataQuery(ids = "${track.remoteId}"),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Failed to get anime metadata",
                default = { null },
            ) {
                val anime = it.animes.firstOrNull() ?: return@dataOrElse null
                TrackAnimeMetadata(
                    remoteId = anime.id.toLong(),
                    title = anime.name,
                    thumbnailUrl = anime.poster?.originalUrl,
                    description = anime.description,
                    authors = anime.personRoles
                        ?.filter { role -> role.rolesEn.contains("Story") || role.rolesEn.contains("Story & Art") }
                        ?.map { role -> role.person.name }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                    artists = anime.personRoles
                        ?.filter { role -> role.rolesEn.contains("Art") || role.rolesEn.contains("Story & Art") }
                        ?.map { role -> role.person.name }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                )
            } ?: throw Exception("Could not get metadata from Shikimori")
    }

    suspend fun accessToken(code: String): SMOAuth {
        return withIOContext {
            with(json) {
                client.newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun accessTokenRequest(code: String) = POST(
        OAUTH_URL,
        body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("code", code)
            .add("redirect_uri", REDIRECT_URL)
            .build(),
    )

    companion object {
        const val BASE_URL = "https://shikimori.one"
        private const val API_URL = "$BASE_URL/api"
        private const val OAUTH_URL = "$BASE_URL/oauth/token"
        private const val LOGIN_URL = "$BASE_URL/oauth/authorize"

        private const val REDIRECT_URL = "animetail://shikimori-auth"

        private const val CLIENT_ID = "aOAYRqOLwxpA8skpcQIXetNy4cw2rn2fRzScawlcQ5U"
        private const val CLIENT_SECRET = "jqjmORn6bh2046ulkm4lHEwJ3OA1RmO3FD2sR9f6Clw"

        fun authUrl(): Uri = LOGIN_URL.toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URL)
            .appendQueryParameter("response_type", "code")
            .build()

        fun refreshTokenRequest(token: String) = POST(
            OAUTH_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", token)
                .build(),
        )
    }
}
