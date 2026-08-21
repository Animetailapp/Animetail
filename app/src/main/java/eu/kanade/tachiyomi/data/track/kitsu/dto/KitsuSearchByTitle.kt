package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuSearchByTitleResult(
    val data: KitsuSearchByTitleData,
)

@Serializable
data class KitsuSearchByTitleData(
    val searchMangaByTitle: KitsuSearchNodes,
)

@Serializable
data class KitsuSearchNodes(
    val nodes: List<KitsuManga>,
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
