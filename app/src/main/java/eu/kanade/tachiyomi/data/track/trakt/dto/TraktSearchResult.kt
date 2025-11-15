package eu.kanade.tachiyomi.data.track.trakt.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TraktSearchResult(
    val type: String, // "show", "movie", etc.
    val score: Double?,
    val show: TraktShow? = null,
    val movie: TraktMovie? = null,
)

@Serializable
data class TraktShow(
    val title: String,
    val year: Int?,
    val ids: TraktIds,
    val overview: String? = null,
    val images: TraktImages? = null,
)

@Serializable
data class TraktMovie(
    val title: String,
    val year: Int?,
    val ids: TraktIds,
    val overview: String? = null,
    val images: TraktImages? = null,
)

@Serializable
data class TraktIds(
    val trakt: Long,
    val slug: String,
    val imdb: String? = null,
    val tmdb: Long? = null,
)

@Serializable
data class TraktImages(
    val poster: JsonElement? = null,
)
