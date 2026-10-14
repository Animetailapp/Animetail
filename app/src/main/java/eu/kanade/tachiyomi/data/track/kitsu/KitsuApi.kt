package eu.kanade.tachiyomi.data.track.kitsu

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuAddMangaResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuDeleteMangaResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuMangaMetadata
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchAnimeByIdResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchAnimeByIdWithLibraryResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchAnimeBySlugResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchAnimeByTitleResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuUpdateMangaResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuUser
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.dataOrElse
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import mihon.graphql.kitsu.KitsuAddLibMangaMutation
import mihon.graphql.kitsu.KitsuDeleteLibEntryMutation
import mihon.graphql.kitsu.KitsuFindLibMangaQuery
import mihon.graphql.kitsu.KitsuGetCurrentAccountQuery
import mihon.graphql.kitsu.KitsuGetMangaDetailsByIdQuery
import mihon.graphql.kitsu.KitsuGetMangaDetailsBySlugQuery
import mihon.graphql.kitsu.KitsuSearchMangaByTitleQuery
import mihon.graphql.kitsu.KitsuUpdateLibMangaMutation
import okhttp3.FormBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import kotlin.time.Instant
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

class KitsuApi(
    private val trackerId: Long,
    private val client: OkHttpClient,
    interceptor: KitsuInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    private val graphQlClient by lazy {
        ApolloClient.Builder()
            .serverUrl(GRAPHQL_API_URL)
            .okHttpClient(authClient)
            .dispatcher(Dispatchers.IO)
            // required to log the error body in dataOrElse, which also properly closes it
            .httpExposeErrorBody(true)
            .build()
    }

    suspend fun addLibManga(track: MangaTrack): MangaTrack {
        return graphQlClient
            .mutation(
                KitsuAddLibMangaMutation(
                    media_id = track.remote_id.toString(),
                    status = track.toKitsuStatus(),
                    progress = track.last_chapter_read.toInt(),
                    private = track.private,
                    rating = Optional.present(track.score.toInt().takeIf { it > 0 }),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to add manga",
                default = { null },
            ) {
                it.libraryEntry.create?.libraryEntry?.id?.let { libraryId ->
                    track.library_id = libraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to add manga")
    }

    suspend fun addLibAnime(track: AnimeTrack): AnimeTrack {
        return withIOContext {
            val query = $$"""
                |mutation AddAnime(
                  |$media_id: ID!
                  |$status: LibraryEntryStatusEnum!
                  |$progress: Int!
                  |$private: Boolean!
                  |$rating: Int
                |) {
                  |libraryEntry {
                    |create(
                      |input: {
                        |mediaId: $media_id
                        |mediaType: ANIME
                        |status: $status
                        |progress: $progress
                        |private: $private
                        |rating: $rating
                      |}
                    |) {
                      |errors {
                        |message
                      |}
                      |libraryEntry {
                        |id
                      |}
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("media_id", track.remote_id)
                    put("status", track.toKitsuApiStatus())
                    put("progress", track.last_episode_seen.toInt())
                    put("private", track.private)
                    put("rating", track.score.toInt().takeIf { it > 0 })
                }
            }

            with(json) {
                val parsed = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuAddMangaResult>()

                if (parsed.error != null) {
                    logcat(LogPriority.ERROR) { "Failed to add: ${parsed.error.message ?: "(none)"}" }
                    throw Exception("Failed to add anime")
                } else if (parsed.errors != null) {
                    parsed.errors.forEach {
                        logcat(LogPriority.ERROR) { "Failed to add: ${it.message ?: "(none)"}" }
                    }
                    throw Exception("Failed to add anime")
                } else if (parsed.data == null) {
                    logcat(LogPriority.ERROR) { "Kitsu error, errors, and data null?" }
                    throw Exception("Encountered unexpected error while adding anime")
                }

                parsed.data.libraryEntry.create.libraryEntry.id.let {
                    track.library_id = it.toLong()
                    track
                }
            }
        }
    }

    suspend fun updateLibManga(track: MangaTrack): MangaTrack {
        val libraryId = track.library_id
        requireNotNull(libraryId) { "Kitsu cannot update track with null library_id" }

        return graphQlClient
            .mutation(
                KitsuUpdateLibMangaMutation(
                    library_id = libraryId.toString(),
                    status = track.toKitsuStatus(),
                    progress = track.last_chapter_read.toInt(),
                    private = track.private,
                    rating = Optional.present(track.score.toInt().takeIf { it > 0 }),
                    startedAt = Optional.present(
                        track.started_reading_date
                            .takeIf { it > 0 }
                            ?.let { Instant.fromEpochMilliseconds(it) },
                    ),
                    finishedAt = Optional.present(
                        track.finished_reading_date
                            .takeIf { it > 0 }
                            ?.let { Instant.fromEpochMilliseconds(it) },
                    ),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to update manga",
                default = { null },
            ) {
                it.libraryEntry.update?.libraryEntry?.id?.let { updatedLibraryId ->
                    logcat { "Kitsu: Updated library entry $updatedLibraryId" }
                    track.library_id = updatedLibraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to update manga")
    }

    suspend fun updateLibAnime(track: AnimeTrack): AnimeTrack {
        return withIOContext {
            val query = $$"""
                |mutation UpdateEntry(
                  |$library_id: ID!
                  |$status: LibraryEntryStatusEnum!
                  |$progress: Int!
                  |$private: Boolean!
                  |$rating: Int
                |) {
                  |libraryEntry {
                    |update(
                      |input: {
                        |id: $library_id
                        |status: $status
                        |progress: $progress
                        |private: $private
                        |rating: $rating
                      |}
                    |) {
                      |errors {
                        |message
                      |}
                      |libraryEntry {
                        |id
                      |}
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("library_id", track.library_id)
                    put("status", track.toKitsuApiStatus())
                    put("progress", track.last_episode_seen.toInt())
                    put("private", track.private)
                    put("rating", track.score.toInt().takeIf { it > 0 })
                }
            }

            with(json) {
                val parsed = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuUpdateMangaResult>()

                if (parsed.error != null) {
                    logcat(LogPriority.ERROR) { "Failed to update: ${parsed.error.message ?: "(none)"}" }
                    throw Exception("Failed to update anime")
                } else if (parsed.errors != null) {
                    parsed.errors.forEach {
                        logcat(LogPriority.ERROR) { "Failed to update: ${it.message ?: "(none)"}" }
                    }
                    throw Exception("Failed to update anime")
                } else if (parsed.data == null) {
                    logcat(LogPriority.ERROR) { "Kitsu error, errors, and data null?" }
                    throw Exception("Encountered unexpected error while updating anime")
                }

                track
            }
        }
    }

    suspend fun removeLibManga(track: DomainMangaTrack) {
        val libraryId = track.libraryId
        requireNotNull(libraryId) { "Kitsu cannot delete track with null library_id" }

        graphQlClient
            .mutation(
                KitsuDeleteLibEntryMutation(
                    library_id = libraryId.toString(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to delete manga",
                default = {},
                onException = { e ->
                    val body = e.body?.use { it.readUtf8() }
                    if (
                        e.statusCode == 500 &&
                        body?.contains("Couldn't find LibraryEntry with 'id'=") == true
                    ) {
                        // Deleting something not in the library (currently as of 2026-09-20) returns a 500 with a
                        // "Couldn't find LibraryEntry" error message at the error.message key --
                        // but the user gets their wish of "title not in library" so ignore it
                        return@dataOrElse
                    } else {
                        throw HttpException(e.statusCode).apply { stackTrace = e.stackTrace }
                    }
                },
            ) {
                logcat { "Kitsu: Deleted library entry ${it.libraryEntry.delete?.libraryEntry?.id}" }
            }
    }

    suspend fun removeLibAnime(track: DomainAnimeTrack) {
        return withIOContext {
            val query = $$"""
                |mutation DeleteEntry($library_id: ID!) {
                  |libraryEntry {
                    |delete(input: { id: $library_id }) {
                      |errors {
                        |message
                      |}
                      |libraryEntry {
                        |id
                      |}
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("library_id", track.libraryId)
                }
            }

            with(json) {
                val parsed = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .await()
                    .parseAs<KitsuDeleteMangaResult>()

                if (parsed.error != null) {
                    logcat(LogPriority.ERROR) { "Failed to delete: ${parsed.error.message ?: "(none)"}" }
                    if (parsed.error.message != null && parsed.error.message.startsWith("Couldn't find")) {
                        return@with
                    }
                    throw Exception("Failed to delete anime")
                } else if (parsed.errors != null) {
                    parsed.errors.forEach {
                        logcat(LogPriority.ERROR) { "Failed to delete: ${it.message ?: "(none)"}" }
                    }
                    throw Exception("Failed to delete anime")
                } else if (parsed.data == null) {
                    logcat(LogPriority.ERROR) { "Kitsu error, errors, and data null?" }
                    throw Exception("Encountered unexpected error while deleting anime")
                }
            }
        }
    }

    suspend fun search(search: String): List<MangaTrackSearch> {
        return graphQlClient
            .query(
                KitsuSearchMangaByTitleQuery(
                    query = search,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Search failed",
                default = { emptyList() },
            ) {
                it.searchMangaByTitle.nodes
                    ?.mapNotNull { node -> node?.toTrackSearch(trackerId) }
            }
            ?: emptyList()
    }

    suspend fun searchAnime(search: String): List<AnimeTrackSearch> {
        return withIOContext {
            val query = $$"""
                |query Query($query: String!) {
                  |searchAnimeByTitle(title: $query, first: 20) {
                    |nodes {
                      $$COMMON_ANIME_DATA
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("query", search)
                }
            }

            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuSearchAnimeByTitleResult>()
                    .data.searchAnimeByTitle.nodes
                    .map { it.toTrackSearch(trackerId) }
            }
        }
    }

    suspend fun findLibManga(track: MangaTrack): MangaTrack? {
        return graphQlClient
            .query(
                KitsuFindLibMangaQuery(
                    remote_id = track.remote_id.toString(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to find manga in library",
                default = { null },
            ) {
                it.findMangaById?.toTrackSearch(trackerId)
            }
    }

    suspend fun findLibAnime(track: AnimeTrack): AnimeTrack? {
        return withIOContext {
            val query = $$"""
                |query Query($remote_id: ID!) {
                  |findAnimeById(id: $remote_id) {
                    |$$COMMON_ANIME_DATA
                    |myLibraryEntry {
                      |id
                      |private
                      |progress
                      |rating
                      |reconsuming
                      |status
                      |startedAt
                      |finishedAt
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("remote_id", track.remote_id)
                }
            }

            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuSearchAnimeByIdWithLibraryResult>()
                    .data.findAnimeById
                    ?.toTrackSearch(trackerId)
            }
        }
    }

    suspend fun login(username: String, password: String): KitsuOAuth {
        return withIOContext {
            val formBody: RequestBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("grant_type", "password")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build()
            with(json) {
                client.newCall(POST(LOGIN_URL, body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    suspend fun getCurrentUser(): KitsuUser {
        return graphQlClient
            .query(
                KitsuGetCurrentAccountQuery(),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to get current user",
                default = { null },
            ) {
                it.currentAccount?.toKitsuUser()
            }
            ?: throw Exception("Failed to get Kitsu user data")
    }

    suspend fun getMangaDetails(search: String): MangaTrackSearch? {
        val isSearchById = search.matches(Regex("\\d+"))

        return if (isSearchById) {
            getMangaDetailsById(search)
        } else {
            getMangaDetailsBySlug(search)
        }
    }

    private suspend fun getMangaDetailsById(id: String): MangaTrackSearch? {
        return graphQlClient
            .query(
                KitsuGetMangaDetailsByIdQuery(
                    id = id,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Search by ID failed",
                default = { null },
            ) {
                it.findMangaById?.toTrackSearch(trackerId)
            }
    }

    private suspend fun getMangaDetailsBySlug(slug: String): MangaTrackSearch? {
        return graphQlClient
            .query(
                KitsuGetMangaDetailsBySlugQuery(
                    slug = slug,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Search by Slug failed",
                default = { null },
            ) {
                it.findMangaBySlug?.toTrackSearch(trackerId)
            }
    }

    suspend fun getAnimeDetails(search: String): AnimeTrackSearch? {
        val isSearchById = search.matches(Regex("\\d+"))

        val query = if (isSearchById) {
            $$"""
                |query Query($query: ID!) {
                  |findAnimeById(id: $query) {
                    |$$COMMON_ANIME_DATA
                  |}
                |}
            """
        } else {
            $$"""
                |query Query($query: String!) {
                  |findAnimeBySlug(slug: $query) {
                    |$$COMMON_ANIME_DATA
                  |}
                |}
            """
        }

        val payload = buildJsonObject {
            put("query", query.trimMargin())
            putJsonObject("variables") {
                put("query", search)
            }
        }

        return withIOContext {
            with(json) {
                val response = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()

                val kitsuAnime = if (isSearchById) {
                    response
                        .parseAs<KitsuSearchAnimeByIdResult>()
                        .data.findAnimeById
                } else {
                    response
                        .parseAs<KitsuSearchAnimeBySlugResult>()
                        .data.findAnimeBySlug
                }

                kitsuAnime?.toTrackSearch(trackerId)
            }
        }
    }

    suspend fun getMangaMetadata(track: DomainMangaTrack): TrackMangaMetadata {
        return withIOContext {
            val query = """
            |query(${'$'}libraryId: ID!, ${'$'}staffCount: Int) {
                |findLibraryEntryById(id: ${'$'}libraryId) {
                    |media {
                        |id
                        |titles {
                            |preferred
                        |}
                        |posterImage {
                            |original {
                                |url
                            |}
                        |}
                        |description
                        |staff(first: ${'$'}staffCount) {
                            |nodes {
                                |role
                                |person {
                                    |name
                                |}
                            |}
                        |}
                    |}
                |}
            |}
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("libraryId", track.remoteId)
                    put("staffCount", 25)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        headers = headersOf("Accept-Language", "en"),
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuMangaMetadata>()
                    .let {
                        val manga = it.data.findLibraryEntryById.media
                        TrackMangaMetadata(
                            remoteId = manga.id.toLong(),
                            title = manga.titles.preferred,
                            thumbnailUrl = manga.posterImage.original.url,
                            description = manga.description.en?.htmlDecode()?.let { if (it.isEmpty()) null else it },
                            authors = manga.staff.nodes
                                .filter { it.role == "Story" || it.role == "Story & Art" }
                                .joinToString(", ") { it.person.name }
                                .let { if (it.isEmpty()) null else it },
                            artists = manga.staff.nodes
                                .filter { it.role == "Art" || it.role == "Story & Art" }
                                .joinToString(", ") { it.person.name }
                                .let { if (it.isEmpty()) null else it },
                        )
                    }
            }
        }
    }

    suspend fun getAnimeMetadata(track: DomainAnimeTrack): TrackAnimeMetadata {
        return withIOContext {
            val query = """
            |query(${'$'}libraryId: ID!, ${'$'}staffCount: Int) {
                |findLibraryEntryById(id: ${'$'}libraryId) {
                    |media {
                        |id
                        |titles {
                            |preferred
                        |}
                        |posterImage {
                            |original {
                                |url
                            |}
                        |}
                        |description
                        |staff(first: ${'$'}staffCount) {
                            |nodes {
                                |role
                                |person {
                                    |name
                                |}
                            |}
                        |}
                    |}
                |}
            |}
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("libraryId", track.remoteId)
                    put("staffCount", 25)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        headers = headersOf("Accept-Language", "en"),
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuMangaMetadata>()
                    .let {
                        val anime = it.data.findLibraryEntryById.media
                        TrackAnimeMetadata(
                            remoteId = anime.id.toLong(),
                            title = anime.titles.preferred,
                            thumbnailUrl = anime.posterImage.original.url,
                            description = anime.description.en?.htmlDecode()?.let { if (it.isEmpty()) null else it },
                            authors = anime.staff.nodes
                                .filter { it.role == "Story" || it.role == "Story & Art" }
                                .joinToString(", ") { it.person.name }
                                .let { if (it.isEmpty()) null else it },
                            artists = anime.staff.nodes
                                .filter { it.role == "Art" || it.role == "Story & Art" }
                                .joinToString(", ") { it.person.name }
                                .let { if (it.isEmpty()) null else it },
                        )
                    }
            }
        }
    }

    companion object {
        private const val CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private const val CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

        private const val GRAPHQL_API_URL = "https://kitsu.app/api/graphql"
        private const val LOGIN_URL = "https://kitsu.app/api/oauth/token"

        fun refreshTokenRequest(token: String) = POST(
            LOGIN_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", token)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build(),
        )

        private val COMMON_ANIME_DATA = """
            |id
            |titles {
              |preferred
            |}
            |episodeCount
            |posterImage {
              |views(names: "small") {
                |name
                |url
              |}
              |original {
                |name
                |url
              |}
            |}
            |description(locales: "en")
            |status
            |subtype
            |startDate
            |endDate
            |slug
            |averageRating
        """.trimMargin()
    }
}
