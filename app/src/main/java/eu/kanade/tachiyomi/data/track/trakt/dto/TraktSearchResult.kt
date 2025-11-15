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
    // Trakt returns poster either as an array of URLs or as an object with sized keys (full, medium, thumb).
    // Keep as JsonElement and normalize at usage sites.
    val poster: JsonElement? = null,
)
