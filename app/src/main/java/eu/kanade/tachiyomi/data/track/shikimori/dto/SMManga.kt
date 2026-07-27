package eu.kanade.tachiyomi.data.track.shikimori.dto

import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SMSearchResult(
    val data: SMMangaResults,
)

@Serializable
data class SMMangaResults(
    val mangas: List<SMManga>,
)

@Serializable
data class SMManga(
    val id: Long,
    val name: String,
    val chapters: Long,
    val score: Double?,
    val url: String,
    val status: String?,
    val poster: SMPoster?,
    val airedOn: SMAiredDate?,
    val description: String?,
    val kind: String?,
    val personRoles: List<SMPersonRole>?,
) {
    fun toTrack(trackId: Long): MangaTrackSearch {
        return MangaTrackSearch.create(trackId).apply {
            remote_id = this@SMManga.id
            title = name
            total_chapters = chapters
            cover_url = poster?.mainUrl.orEmpty()
            summary = description.orEmpty()
            score = this@SMManga.score?.takeIf { it > 0.0 } ?: -1.0
            tracking_url = url
            publishing_status = this@SMManga.status.orEmpty()
            publishing_type = kind?.replace("one_shot", "oneshot").orEmpty()
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
    val date: String?,
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
