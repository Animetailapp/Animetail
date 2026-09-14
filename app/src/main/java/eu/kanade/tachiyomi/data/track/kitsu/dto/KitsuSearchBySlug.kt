package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuSearchBySlugResult(
    val data: KitsuSearchBySlugData,
)

@Serializable
data class KitsuSearchBySlugData(
    val findMangaBySlug: KitsuManga?,
)

@Serializable
data class KitsuSearchAnimeBySlugResult(
    val data: KitsuSearchAnimeBySlugData,
)

@Serializable
data class KitsuSearchAnimeBySlugData(
    val findAnimeBySlug: KitsuAnime?,
)
