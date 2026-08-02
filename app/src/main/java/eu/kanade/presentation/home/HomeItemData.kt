package eu.kanade.presentation.home

/**
 * Modelo de item para representar animes, mangas, películas y series en la Home.
 */
data class HomeItemData(
    val id: Long,
    val isAnime: Boolean = true,
    val inLibrary: Boolean = false,
    val episodeId: Long? = null,
    val chapterId: Long? = null,
    val title: String,
    val subtitle: String,
    val coverUrl: String? = null,
    val coverData: Any? = null,
    val mediaType: MediaType,
    val rating: String = "",
    val progress: Float = 0f,
    val remainingInfo: String = "",
    val synopsis: String = "",
    val genres: String = "",
    val lastUpdatedTimestamp: Long = 0L,
)
