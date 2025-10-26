package eu.kanade.tachiyomi.data.track.trakt.dto

import kotlinx.serialization.Serializable

@Serializable
data class TraktSyncRequest(
    val shows: List<TraktSyncShow>? = null,
    val movies: List<TraktSyncMovie>? = null
)

@Serializable
data class TraktSyncShow(
    val ids: TraktIds,
    val progress: Int,
    val status: String // "watching", "completed", etc.
)

@Serializable
data class TraktSyncMovie(
    val ids: TraktIds,
    val watched: Boolean
)
