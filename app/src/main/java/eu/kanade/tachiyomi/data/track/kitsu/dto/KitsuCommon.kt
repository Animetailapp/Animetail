package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuErrorMessage(
    val message: String?,
)

@Serializable
data class KitsuLibraryEntryResult(
    val libraryEntry: KitsuSparseLibraryEntry,
)

@Serializable
data class KitsuSparseLibraryEntry(
    val id: String,
)

@Serializable
data class KitsuMangaTitles(
    val preferred: String,
)

@Serializable
data class KitsuMangaStaffData(
    val nodes: List<KitsuMangaStaff>,
)

@Serializable
data class KitsuMangaStaff(
    val role: String,
    val person: KitsuMangaStaffPerson,
)

@Serializable
data class KitsuMangaStaffPerson(
    val name: String,
)

@Serializable
data class KitsuMangaPosters(
    val views: List<KitsuMangaPoster>,
    val original: KitsuMangaPoster,
) {
    // we only ask for the "small" poster in the query
    fun getPosterUrl(): String = views.firstOrNull()?.url ?: original.url
}

@Serializable
data class KitsuMangaPoster(
    val name: String,
    val url: String,
)

@Serializable
data class KitsuLibraryEntryData(
    val id: String,
    val startedAt: String?,
    val finishedAt: String?,
    val status: String,
    val rating: Long?,
    val progress: Long,
    val private: Boolean,
)

@Serializable
data class KitsuSearchAnimeByIdResult(
    val data: KitsuSearchAnimeByIdData,
)

@Serializable
data class KitsuSearchAnimeByIdData(
    val findAnimeById: KitsuAnime?,
)

@Serializable
data class KitsuSearchAnimeByIdWithLibraryResult(
    val data: KitsuSearchAnimeByIdWithLibraryData,
)

@Serializable
data class KitsuSearchAnimeByIdWithLibraryData(
    val findAnimeById: KitsuAnimeWithLibraryEntry?,
)

@Serializable
data class KitsuSearchAnimeBySlugResult(
    val data: KitsuSearchAnimeBySlugData,
)

@Serializable
data class KitsuSearchAnimeBySlugData(
    val findAnimeBySlug: KitsuAnime?,
)

@Serializable
data class KitsuSearchAnimeByTitleResult(
    val data: KitsuSearchAnimeByTitleData,
)

@Serializable
data class KitsuSearchAnimeByTitleData(
    val searchAnimeByTitle: KitsuSearchAnimeNodes,
)

@Serializable
data class KitsuSearchAnimeNodes(
    val nodes: List<KitsuAnime>,
)
