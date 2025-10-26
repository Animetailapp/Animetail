package eu.kanade.tachiyomi.data.track.trakt.dto

import kotlinx.serialization.Serializable

@Serializable
data class TraktLibraryItem(
    val traktId: Long,
    val title: String? = null,
    val progress: Int = 0
)
