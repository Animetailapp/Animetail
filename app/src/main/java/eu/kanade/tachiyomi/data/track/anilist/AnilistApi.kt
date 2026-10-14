package eu.kanade.tachiyomi.data.track.anilist

import android.net.Uri
import androidx.core.net.toUri
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.animesource.model.Credit
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUser
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.network.dataOrElse
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import mihon.graphql.anilist.AniListAddAnimeMutation
import mihon.graphql.anilist.AniListAddMangaMutation
import mihon.graphql.anilist.AniListDeleteMangaMutation
import mihon.graphql.anilist.AniListGetAnimeDetailsQuery
import mihon.graphql.anilist.AniListGetAnimeMetadataQuery
import mihon.graphql.anilist.AniListGetCurrentUserQuery
import mihon.graphql.anilist.AniListGetLibAnimeQuery
import mihon.graphql.anilist.AniListGetLibMangaQuery
import mihon.graphql.anilist.AniListGetMangaDetailsQuery
import mihon.graphql.anilist.AniListGetMangaMetadataQuery
import mihon.graphql.anilist.AniListGetPopularAnimeQuery
import mihon.graphql.anilist.AniListGetPopularMangaQuery
import mihon.graphql.anilist.AniListSearchAnimeQuery
import mihon.graphql.anilist.AniListSearchCastQuery
import mihon.graphql.anilist.AniListSearchMangaQuery
import mihon.graphql.anilist.AniListUpdateAnimeMutation
import mihon.graphql.anilist.AniListUpdateMangaMutation
import mihon.graphql.anilist.type.FuzzyDateInput
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

class AnilistApi(
    val trackerId: Long,
    val client: OkHttpClient,
    interceptor: AnilistInterceptor,
) {

    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        .rateLimit(permits = 25, period = 1.minutes)
        .build()

    private val publicClient = client.newBuilder()
        .rateLimit(permits = 85, period = 1.minutes)
        .build()

    private val graphQlClient by lazy {
        ApolloClient.Builder()
            .serverUrl("https://graphql.anilist.co")
            .okHttpClient(authClient)
            .dispatcher(Dispatchers.IO)
            // required to log the error body in dataOrElse, which also properly closes it
            .httpExposeErrorBody(true)
            .build()
    }

    suspend fun addLibManga(track: MangaTrack): MangaTrack {
        return graphQlClient
            .mutation(
                AniListAddMangaMutation(
                    manga_id = track.remote_id.toInt(),
                    progress = track.last_chapter_read.toInt(),
                    status = track.toApiStatus(),
                    private = track.private,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to add manga",
                default = { null },
            ) {
                it.SaveMediaListEntry?.id?.let { libraryId ->
                    track.library_id = libraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to add manga")
    }

    suspend fun updateLibManga(track: MangaTrack): MangaTrack {
        val libraryId = track.library_id
        requireNotNull(libraryId) { "AniList cannot update track with null library_id" }

        return graphQlClient
            .mutation(
                AniListUpdateMangaMutation(
                    library_id = libraryId.toInt(),
                    progress = track.last_chapter_read.toInt(),
                    status = track.toApiStatus(),
                    private = track.private,
                    score = track.score.toInt(),
                    startedAt = createFuzzyDate(track.started_reading_date),
                    completedAt = createFuzzyDate(track.finished_reading_date),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to update manga",
                default = { null },
            ) {
                it.SaveMediaListEntry?.id?.let { remoteLibraryId ->
                    track.library_id = remoteLibraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to update manga")
    }

    suspend fun deleteLibManga(track: DomainMangaTrack) {
        val libraryId = track.libraryId
        requireNotNull(libraryId) { "AniList cannot delete track with null library_id" }

        graphQlClient
            .mutation(
                AniListDeleteMangaMutation(library_id = libraryId.toInt()),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to delete manga",
                default = { null },
            ) {
                it.DeleteMediaListEntry?.deleted?.let { deleted ->
                    if (deleted) {
                        logcat { "AniList: Deleted manga ${track.libraryId} successfully" }
                    }
                }
            }
            ?: throw Exception("Failed to delete manga")
    }

    suspend fun addLibAnime(track: AnimeTrack): AnimeTrack {
        return graphQlClient
            .mutation(
                AniListAddAnimeMutation(
                    anime_id = track.remote_id.toInt(),
                    progress = track.last_episode_seen.toInt(),
                    status = track.toApiStatus(),
                    private = track.private,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to add anime",
                default = { null },
            ) {
                it.SaveMediaListEntry?.id?.let { libraryId ->
                    track.library_id = libraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to add anime")
    }

    suspend fun updateLibAnime(track: AnimeTrack): AnimeTrack {
        val libraryId = track.library_id
        requireNotNull(libraryId) { "AniList cannot update track with null library_id" }

        return graphQlClient
            .mutation(
                AniListUpdateAnimeMutation(
                    library_id = libraryId.toInt(),
                    progress = track.last_episode_seen.toInt(),
                    status = track.toApiStatus(),
                    private = track.private,
                    score = track.score.toInt(),
                    startedAt = createFuzzyDate(track.started_watching_date),
                    completedAt = createFuzzyDate(track.finished_watching_date),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to update anime",
                default = { null },
            ) {
                it.SaveMediaListEntry?.id?.let { remoteLibraryId ->
                    track.library_id = remoteLibraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to update anime")
    }

    suspend fun deleteLibAnime(track: DomainAnimeTrack) {
        val libraryId = track.libraryId
        requireNotNull(libraryId) { "AniList cannot delete track with null library_id" }

        graphQlClient
            .mutation(
                AniListDeleteMangaMutation(library_id = libraryId.toInt()),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to delete anime",
                default = { null },
            ) {
                it.DeleteMediaListEntry?.deleted?.let { deleted ->
                    if (deleted) {
                        logcat { "AniList: Deleted anime ${track.libraryId} successfully" }
                    }
                }
            }
            ?: throw Exception("Failed to delete anime")
    }

    suspend fun search(search: String): List<MangaTrackSearch> {
        return graphQlClient
            .query(
                AniListSearchMangaQuery(search = search),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Search failed",
                default = { emptyList() },
            ) {
                it.Page?.media
                    ?.mapNotNull { alManga -> alManga?.toTrackSearch(trackerId) }
                    ?: emptyList()
            }
    }

    suspend fun searchAnime(search: String): List<AnimeTrackSearch> {
        return graphQlClient
            .query(
                AniListSearchAnimeQuery(search = search),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Search anime failed",
                default = { emptyList() },
            ) {
                it.Page?.media
                    ?.mapNotNull { alAnime -> alAnime?.toTrackSearch(trackerId) }
                    ?: emptyList()
            }
    }

    suspend fun getMangaDetails(id: Int): MangaTrackSearch? {
        return graphQlClient
            .query(
                AniListGetMangaDetailsQuery(manga_id = id),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get manga details",
                default = { null },
            ) {
                it.Page?.media
                    ?.firstOrNull()
                    ?.toTrackSearch(trackerId)
            }
    }

    suspend fun getAnimeDetails(id: Int): AnimeTrackSearch? {
        return graphQlClient
            .query(
                AniListGetAnimeDetailsQuery(anime_id = id),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get anime details",
                default = { null },
            ) {
                it.Page?.media
                    ?.firstOrNull()
                    ?.toTrackSearch(trackerId)
            }
    }

    suspend fun getPopularAnime(): List<AnimeTrackSearch> {
        return graphQlClient
            .query(
                AniListGetPopularAnimeQuery(),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get popular anime",
                default = { emptyList() },
            ) {
                it.Page?.media
                    ?.mapNotNull { item -> item?.toTrackSearch(trackerId) }
                    ?: emptyList()
            }
    }

    suspend fun getPopularManga(): List<MangaTrackSearch> {
        return graphQlClient
            .query(
                AniListGetPopularMangaQuery(),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get popular manga",
                default = { emptyList() },
            ) {
                it.Page?.media
                    ?.mapNotNull { item -> item?.toTrackSearch(trackerId) }
                    ?: emptyList()
            }
    }

    suspend fun findLibManga(track: MangaTrack, userId: Int): MangaTrack? {
        return graphQlClient
            .query(
                AniListGetLibMangaQuery(
                    user_id = userId,
                    manga_id = track.remote_id.toInt(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to find manga in library",
                default = { null },
            ) {
                it.Page?.mediaList
                    ?.firstOrNull()
                    ?.toTrack(trackerId)
            }
    }

    suspend fun findLibAnime(track: AnimeTrack, userid: Int): AnimeTrack? {
        return graphQlClient
            .query(
                AniListGetLibAnimeQuery(
                    user_id = userid,
                    anime_id = track.remote_id.toInt(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to find anime in library",
                default = { null },
            ) {
                it.Page?.mediaList
                    ?.firstOrNull()
                    ?.toTrack(trackerId)
            }
    }

    suspend fun getLibAnime(track: AnimeTrack, userId: Int): AnimeTrack =
        findLibAnime(track, userId) ?: throw Exception("Could not find anime")

    suspend fun getCurrentUser(): ALUser {
        return graphQlClient
            .query(AniListGetCurrentUserQuery())
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get current user",
                default = { null },
            ) {
                it.Viewer?.toALUser()
            }
            ?: throw Exception("Failed to get AniList user data")
    }

    suspend fun getAnimeMetadata(track: DomainAnimeTrack): TrackAnimeMetadata {
        return graphQlClient
            .query(
                AniListGetAnimeMetadataQuery(anime_id = track.remoteId.toInt()),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get anime metadata",
                default = { null },
            ) {
                val media = it.Media ?: return@dataOrElse null
                TrackAnimeMetadata(
                    remoteId = media.id.toLong(),
                    title = media.title?.userPreferred ?: "",
                    thumbnailUrl = media.coverImage?.large ?: "",
                    description = media.description?.htmlDecode()?.ifEmpty { null },
                    authors = media.staff?.edges
                        ?.filter { it?.role == "Original Creator" }
                        ?.mapNotNull { it?.node?.name?.userPreferred }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                    artists = media.studios?.edges
                        ?.filter { it?.isMain == true }
                        ?.mapNotNull { it?.node?.name }
                        ?.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ")
                        ?: media.studios?.edges?.mapNotNull { it?.node?.name }?.joinToString(", ") ?: "",
                )
            }
            ?: throw Exception("Failed to get anime metadata")
    }

    suspend fun getMangaMetadata(track: DomainMangaTrack): TrackMangaMetadata {
        return graphQlClient
            .query(
                AniListGetMangaMetadataQuery(manga_id = track.remoteId.toInt()),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get manga metadata",
                default = { null },
            ) {
                val media = it.Media ?: return@dataOrElse null
                TrackMangaMetadata(
                    remoteId = media.id.toLong(),
                    title = media.title?.userPreferred ?: "",
                    thumbnailUrl = media.coverImage?.large ?: "",
                    description = media.description?.htmlDecode()?.ifEmpty { null },
                    authors = media.staff?.edges
                        ?.filter { it?.role == "Story" || it?.role == "Story & Art" }
                        ?.mapNotNull { it?.node?.name?.userPreferred }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                    artists = media.staff?.edges
                        ?.filter { it?.role == "Art" || it?.role == "Story & Art" }
                        ?.mapNotNull { it?.node?.name?.userPreferred }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                )
            }
            ?: throw Exception("Failed to get manga metadata")
    }

    /**
     * Fetch cast (characters with voice actors and staff) from AniList by title search.
     * Returns null on error or empty results.
     */
    suspend fun fetchCastByTitle(title: String?): List<Credit>? {
        if (title.isNullOrBlank()) return null

        return graphQlClient
            .query(
                AniListSearchCastQuery(query = title),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to fetch cast",
                default = { null },
            ) {
                val first = it.Page?.media?.firstOrNull() ?: return@dataOrElse null
                val credits = mutableListOf<Credit>()

                first.characters?.edges?.forEach { edge ->
                    val charName = edge?.node?.name?.userPreferred
                    val charImage = edge?.node?.image?.large
                    val vaNames = mutableListOf<String>()
                    var vaImage: String? = null
                    edge?.voiceActors?.forEach { va ->
                        val vaName = va?.name?.userPreferred
                        val vaLang = va?.languageV2
                        if (!vaName.isNullOrBlank()) {
                            vaNames.add(if (!vaLang.isNullOrBlank()) "$vaName ($vaLang)" else vaName)
                        }
                        if (vaImage == null) {
                            vaImage = va?.image?.large
                        }
                    }
                    val roleText = vaNames.joinToString(", ")
                    var finalImage = charImage ?: vaImage
                    if (!finalImage.isNullOrBlank() && finalImage.startsWith("//")) {
                        finalImage = "https:$finalImage"
                    }
                    if (!charName.isNullOrBlank()) {
                        credits.add(
                            Credit(
                                name = charName,
                                role = roleText.ifBlank { null },
                                character = charName,
                                image_url = finalImage,
                            ),
                        )
                    }
                }

                first.staff?.edges?.forEach { sedge ->
                    val srole = sedge?.role
                    val sname = sedge?.node?.name?.userPreferred
                    var sImage = sedge?.node?.image?.large
                    if (!sImage.isNullOrBlank() && sImage.startsWith("//")) sImage = "https:$sImage"
                    if (!sname.isNullOrBlank()) {
                        credits.add(Credit(name = sname, role = srole, image_url = sImage))
                    }
                }

                credits.ifEmpty { null }
            }
    }

    private fun createFuzzyDate(dateValue: Long): FuzzyDateInput {
        // all absent/null
        if (dateValue == 0L) return FuzzyDateInput()

        val dateTime = Instant.fromEpochMilliseconds(dateValue).toLocalDateTime(TimeZone.currentSystemDefault())
        return FuzzyDateInput(
            year = Optional.present(dateTime.year),
            month = Optional.present(dateTime.month.number),
            day = Optional.present(dateTime.day),
        )
    }

    companion object {
        private const val CLIENT_ID = "18376"
        private const val API_URL = "https://graphql.anilist.co/"
        private const val BASE_URL = "https://anilist.co/api/v2/"
        private const val BASE_MANGA_URL = "https://anilist.co/manga/"
        private const val BASE_ANIME_URL = "https://anilist.co/anime/"

        fun mangaUrl(mediaId: Long): String {
            return BASE_MANGA_URL + mediaId
        }

        fun animeUrl(mediaId: Long): String {
            return BASE_ANIME_URL + mediaId
        }

        fun authUrl(): Uri = "${BASE_URL}oauth/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .build()
    }
}
