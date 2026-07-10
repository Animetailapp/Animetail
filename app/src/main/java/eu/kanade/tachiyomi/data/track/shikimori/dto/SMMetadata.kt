package eu.kanade.tachiyomi.data.track.shikimori.dto

import kotlinx.serialization.Serializable

@Serializable
data class SMMetadata(
    val data: SMMetadataData,
)

@Serializable
data class SMMetadataData(
    val mangas: List<SMMetadataResult> = emptyList(),
    val animes: List<SMMetadataResult> = emptyList(),
)

@Serializable
data class SMMetadataResult(
    val id: String,
    val name: String,
    val description: String?,
    val poster: SMMangaPoster,
    val personRoles: List<SMMangaPersonRoles>? = emptyList(),
)

@Serializable
data class SMMangaPoster(
    val originalUrl: String,
)

@Serializable
data class SMMangaPersonRoles(
    val person: SMPerson,
    val rolesEn: List<String>,
)
