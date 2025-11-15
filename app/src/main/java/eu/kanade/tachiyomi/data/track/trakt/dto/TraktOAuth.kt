package eu.kanade.tachiyomi.data.track.trakt.dto

import kotlinx.serialization.Serializable

@Serializable
data class TraktOAuth(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
    val created_at: Long,
    val token_type: String,
)
