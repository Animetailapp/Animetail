package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

// normal search
@Serializable
data class KitsuSearchByIdResult(
    val data: KitsuSearchByIdData,
)

@Serializable
data class KitsuSearchByIdData(
    val findMangaById: KitsuManga?,
)

// findLibManga (on tracker sheet refresh & when checking for remote track on binding)
@Serializable
data class KitsuSearchByIdWithLibraryResult(
    val data: KitsuSearchByIdWithLibraryData,
)

@Serializable
data class KitsuSearchByIdWithLibraryData(
    val findMangaById: KitsuMangaWithLibraryEntry?,
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
