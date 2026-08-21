package eu.kanade.tachiyomi.data.track.kitsu.dto

import eu.kanade.tachiyomi.data.track.kitsu.toKitsuLocalStatusAnime
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class KitsuAnimeWithLibraryEntry(
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
    val myLibraryEntry: KitsuLibraryEntryData?,
) {
    fun toTrackSearch(trackId: Long): AnimeTrackSearch? {
        if (myLibraryEntry == null) return null

        return AnimeTrackSearch.create(trackId).apply {
            remote_id = this@KitsuAnimeWithLibraryEntry.id.toLong()
            library_id = myLibraryEntry.id.toLong()
            title = titles.preferred
            total_episodes = episodeCount ?: 0
            cover_url = posterImage.getPosterUrl()
            summary = description["en"] ?: ""
            tracking_url = "https://kitsu.app/anime/$slug"
            publishing_status = when (this@KitsuAnimeWithLibraryEntry.status) {
                "TBA" -> "TBA"
                "CURRENT" -> "Publishing"
                else -> this@KitsuAnimeWithLibraryEntry.status.lowercase().replaceFirstChar { it.uppercase() }
            }
            publishing_type = if (subtype != "OEL") {
                subtype.lowercase().replaceFirstChar { it.uppercase() }
            } else {
                subtype
            }
            start_date = startDate ?: ""

            started_watching_date = myLibraryEntry.startedAt?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0
            finished_watching_date = myLibraryEntry.finishedAt?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0
            status = myLibraryEntry.status.toKitsuLocalStatusAnime()
            score = myLibraryEntry.rating?.toDouble() ?: 0.0
            last_episode_seen = myLibraryEntry.progress.toDouble()
            private = myLibraryEntry.private
        }
    }
}
