package eu.kanade.tachiyomi.data.track.kitsu.dto

import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.serialization.Serializable

@Serializable
data class KitsuAnime(
    val id: String,
    val titles: KitsuMangaTitles,
    val episodeCount: Long?,
    val posterImage: KitsuMangaPosters,
    val description: Map<String, String>,
    val status: String,
    val subtype: String,
    val startDate: String?,
    val endDate: String?,
    val slug: String,
    val averageRating: Double?,
) {
    fun toTrackSearch(trackId: Long): AnimeTrackSearch {
        return AnimeTrackSearch.create(trackId).apply {
            remote_id = this@KitsuAnime.id.toLong()
            title = titles.preferred
            total_episodes = episodeCount ?: 0
            cover_url = posterImage.getPosterUrl()
            summary = description["en"] ?: ""
            tracking_url = "https://kitsu.app/anime/$slug"
            score = averageRating ?: -1.0
            publishing_status = when (this@KitsuAnime.status) {
                "TBA" -> "TBA"
                "CURRENT" -> "Publishing"
                else -> this@KitsuAnime.status.lowercase().replaceFirstChar { it.uppercase() }
            }
            publishing_type = if (subtype != "OEL") {
                subtype.lowercase().replaceFirstChar { it.uppercase() }
            } else {
                subtype
            }
            start_date = startDate ?: ""
        }
    }
}
