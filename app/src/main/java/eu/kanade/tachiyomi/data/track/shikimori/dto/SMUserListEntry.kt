package eu.kanade.tachiyomi.data.track.shikimori.dto

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.shikimori.toTrackStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SMUserListEntry(
    val id: Long,
    val chapters: Double,
    val episodes: Double,
    val score: Int,
    val status: String,
)

@Serializable
data class SMUserListResult(
    val data: SMUserListEntries,
)

@Serializable
data class SMUserListEntries(
    val mangas: List<SMUserListManga>,
)

@Serializable
data class SMUserListManga(
    val id: String,
    val url: String,
    val name: String,
    @SerialName("chapters")
    val totalChapters: Long,
    val userRate: SMUserRate?,
) {
    fun toTrack(trackId: Long): MangaTrack {
        return MangaTrack.create(trackId).apply {
            title = name
            total_chapters = totalChapters
            tracking_url = url
            if (userRate != null) {
                remote_id = userRate.rateId.toLong()
                library_id = userRate.rateId.toLong()
                last_chapter_read = userRate.chapters.toDouble()
                score = userRate.score
                status = toTrackStatus(userRate.status)
            }
        }
    }
}

@Serializable
data class SMAnimeUserListResult(
    val data: SMAnimeUserListEntries,
)

@Serializable
data class SMAnimeUserListEntries(
    val animes: List<SMAnimeUserListAnime>,
)

@Serializable
data class SMAnimeUserListAnime(
    val id: String,
    val url: String,
    val name: String,
    @SerialName("episodes")
    val totalEpisodes: Long,
    val userRate: SMAnimeUserRate?,
) {
    fun toTrack(trackId: Long): AnimeTrack {
        return AnimeTrack.create(trackId).apply {
            title = name
            total_episodes = totalEpisodes
            tracking_url = url
            if (userRate != null) {
                remote_id = userRate.rateId.toLong()
                library_id = userRate.rateId.toLong()
                last_episode_seen = userRate.episodes.toDouble()
                score = userRate.score
                status = toTrackStatus(userRate.status)
            }
        }
    }
}

@Serializable
data class SMUserRate(
    @SerialName("id")
    val rateId: String,
    val chapters: Long,
    val status: String,
    val score: Double,
)

@Serializable
data class SMAnimeUserRate(
    @SerialName("id")
    val rateId: String,
    val episodes: Long,
    val status: String,
    val score: Double,
)
