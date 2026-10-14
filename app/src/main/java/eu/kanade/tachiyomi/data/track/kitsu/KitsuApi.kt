package eu.kanade.tachiyomi.data.track.kitsu

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuUser
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.dataOrElse
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import mihon.graphql.kitsu.KitsuAddLibAnimeMutation
import mihon.graphql.kitsu.KitsuAddLibMangaMutation
import mihon.graphql.kitsu.KitsuDeleteLibEntryMutation
import mihon.graphql.kitsu.KitsuFindLibAnimeQuery
import mihon.graphql.kitsu.KitsuFindLibMangaQuery
import mihon.graphql.kitsu.KitsuGetAnimeDetailsByIdQuery
import mihon.graphql.kitsu.KitsuGetAnimeDetailsBySlugQuery
import mihon.graphql.kitsu.KitsuGetCurrentAccountQuery
import mihon.graphql.kitsu.KitsuGetLibraryEntryMetadataQuery
import mihon.graphql.kitsu.KitsuGetMangaDetailsByIdQuery
import mihon.graphql.kitsu.KitsuGetMangaDetailsBySlugQuery
import mihon.graphql.kitsu.KitsuSearchAnimeByTitleQuery
import mihon.graphql.kitsu.KitsuSearchMangaByTitleQuery
import mihon.graphql.kitsu.KitsuUpdateLibMangaMutation
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
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
        return graphQlClient
            .mutation(
                KitsuAddLibAnimeMutation(
                    media_id = track.remote_id.toString(),
                    status = track.toKitsuStatus(),
                    progress = track.last_episode_seen.toInt(),
                    private = track.private,
                    rating = Optional.present(track.score.toInt().takeIf { it > 0 }),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to add anime",
                default = { null },
            ) {
                it.libraryEntry.create?.libraryEntry?.id?.let { libraryId ->
                    track.library_id = libraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to add anime")
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
        val libraryId = track.library_id
        requireNotNull(libraryId) { "Kitsu cannot update track with null library_id" }

        return graphQlClient
            .mutation(
                KitsuUpdateLibMangaMutation(
                    library_id = libraryId.toString(),
                    status = track.toKitsuStatus(),
                    progress = track.last_episode_seen.toInt(),
                    private = track.private,
                    rating = Optional.present(track.score.toInt().takeIf { it > 0 }),
                    startedAt = Optional.present(
                        track.started_watching_date
                            .takeIf { it > 0 }
                            ?.let { Instant.fromEpochMilliseconds(it) },
                    ),
                    finishedAt = Optional.present(
                        track.finished_watching_date
                            .takeIf { it > 0 }
                            ?.let { Instant.fromEpochMilliseconds(it) },
                    ),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to update anime",
                default = { null },
            ) {
                it.libraryEntry.update?.libraryEntry?.id?.let { updatedLibraryId ->
                    logcat { "Kitsu: Updated library entry $updatedLibraryId" }
                    track.library_id = updatedLibraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to update anime")
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
                errorLog = "Kitsu: Failed to delete anime",
                default = {},
                onException = { e ->
                    val body = e.body?.use { it.readUtf8() }
                    if (
                        e.statusCode == 500 &&
                        body?.contains("Couldn't find LibraryEntry with 'id'=") == true
                    ) {
                        return@dataOrElse
                    } else {
                        throw HttpException(e.statusCode).apply { stackTrace = e.stackTrace }
                    }
                },
            ) {
                logcat { "Kitsu: Deleted library entry ${it.libraryEntry.delete?.libraryEntry?.id}" }
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
        return graphQlClient
            .query(
                KitsuSearchAnimeByTitleQuery(
                    query = search,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Search anime failed",
                default = { emptyList() },
            ) {
                it.searchAnimeByTitle.nodes
                    ?.mapNotNull { node -> node?.toTrackSearch(trackerId) }
            }
            ?: emptyList()
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
        return graphQlClient
            .query(
                KitsuFindLibAnimeQuery(
                    remote_id = track.remote_id.toString(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to find anime in library",
                default = { null },
            ) {
                it.findAnimeById?.toTrackSearch(trackerId)
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

        return if (isSearchById) {
            getAnimeDetailsById(search)
        } else {
            getAnimeDetailsBySlug(search)
        }
    }

    private suspend fun getAnimeDetailsById(id: String): AnimeTrackSearch? {
        return graphQlClient
            .query(
                KitsuGetAnimeDetailsByIdQuery(
                    id = id,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Search anime by ID failed",
                default = { null },
            ) {
                it.findAnimeById?.toTrackSearch(trackerId)
            }
    }

    private suspend fun getAnimeDetailsBySlug(slug: String): AnimeTrackSearch? {
        return graphQlClient
            .query(
                KitsuGetAnimeDetailsBySlugQuery(
                    slug = slug,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Search anime by Slug failed",
                default = { null },
            ) {
                it.findAnimeBySlug?.toTrackSearch(trackerId)
            }
    }

    suspend fun getMangaMetadata(track: DomainMangaTrack): TrackMangaMetadata {
        return graphQlClient
            .query(
                KitsuGetLibraryEntryMetadataQuery(
                    libraryId = track.remoteId.toString(),
                    staffCount = Optional.present(25),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to get manga metadata",
                default = { null },
            ) {
                val manga = it.findLibraryEntryById?.media ?: return@dataOrElse null
                TrackMangaMetadata(
                    remoteId = manga.id.toLong(),
                    title = manga.titles.preferred,
                    thumbnailUrl = manga.posterImage?.original?.url ?: "",
                    description = (manga.description["en"] as? String)?.htmlDecode()?.ifEmpty { null },
                    authors = manga.staff.nodes
                        ?.filterNotNull()
                        ?.filter { it.role == "Story" || it.role == "Story & Art" }
                        ?.map { it.person.name }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                    artists = manga.staff.nodes
                        ?.filterNotNull()
                        ?.filter { it.role == "Art" || it.role == "Story & Art" }
                        ?.map { it.person.name }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                )
            }
            ?: throw Exception("Failed to get manga metadata")
    }

    suspend fun getAnimeMetadata(track: DomainAnimeTrack): TrackAnimeMetadata {
        return graphQlClient
            .query(
                KitsuGetLibraryEntryMetadataQuery(
                    libraryId = track.remoteId.toString(),
                    staffCount = Optional.present(25),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Kitsu: Failed to get anime metadata",
                default = { null },
            ) {
                val anime = it.findLibraryEntryById?.media ?: return@dataOrElse null
                TrackAnimeMetadata(
                    remoteId = anime.id.toLong(),
                    title = anime.titles.preferred,
                    thumbnailUrl = anime.posterImage?.original?.url ?: "",
                    description = (anime.description["en"] as? String)?.htmlDecode()?.ifEmpty { null },
                    authors = anime.staff.nodes
                        ?.filterNotNull()
                        ?.filter { it.role.contains("Director") || it.role.contains("Creator") }
                        ?.map { it.person.name }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                    artists = anime.staff.nodes
                        ?.filterNotNull()
                        ?.filter { it.role.contains("Animation") || it.role.contains("Character") }
                        ?.map { it.person.name }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                )
            }
            ?: throw Exception("Failed to get anime metadata")
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
    }
}
