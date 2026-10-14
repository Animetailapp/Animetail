package eu.kanade.tachiyomi.data.track.shikimori.dto

import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SMAnimeSearchResult(
    val data: SMAnimeResults,
)

@Serializable
data class SMAnimeResults(
    val animes: List<SMAnime>,
)

@Serializable
data class SMAnime(
    val id: Long,
    val name: String,
    val episodes: Long,
    val score: Double?,
    val url: String,
    val status: String?,
    val poster: SMPoster?,
    val airedOn: SMAiredDate?,
    val description: String?,
    val kind: String?,
    val personRoles: List<SMPersonRole>?,
) {
    fun toTrack(trackId: Long): AnimeTrackSearch {
        return AnimeTrackSearch.create(trackId).apply {
            remote_id = this@SMAnime.id
            title = name
            total_episodes = episodes
            cover_url = poster?.mainUrl.orEmpty()
            summary = description.orEmpty()
            score = this@SMAnime.score?.takeIf { it > 0.0 } ?: -1.0
            tracking_url = url
            publishing_status = this@SMAnime.status.orEmpty()
            publishing_type = kind.orEmpty()
            start_date = airedOn?.date.orEmpty()
            personRoles?.forEach { personRole ->
                personRole.roles.forEach { role ->
                    if ("Story" in role) authors += personRole.person.name
                    if ("Art" in role) artists += personRole.person.name
                }
            }
        }
    }
}

@Serializable
data class SMPoster(
    val mainUrl: String,
)

@Serializable
data class SMAiredDate(
    val date: String,
)

@Serializable
data class SMPersonRole(
    val person: SMPerson,
    @SerialName("rolesEn")
    val roles: List<String>,
)

@Serializable
data class SMPerson(
    val name: String,
)

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
    fun toTrack(trackId: Long): eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack {
        return eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack.create(trackId).apply {
            title = name
            total_episodes = totalEpisodes
            tracking_url = url
            if (userRate != null) {
                remote_id = userRate.rateId.toLong()
                library_id = userRate.rateId.toLong()
                last_episode_seen = userRate.episodes.toDouble()
                score = userRate.score
                status = eu.kanade.tachiyomi.data.track.shikimori.toTrackStatus(userRate.status)
            }
        }
    }
}

@Serializable
data class SMAnimeUserRate(
    @SerialName("id")
    val rateId: String,
    val episodes: Long,
    val status: String,
    val score: Double,
)
