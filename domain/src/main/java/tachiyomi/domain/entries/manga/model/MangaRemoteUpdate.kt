package tachiyomi.domain.entries.manga.model

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.JsonObject

data class MangaRemoteUpdate(
    val id: Long,
    val title: String?,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Long,
    val thumbnailUrl: String?,
    val updateStrategy: UpdateStrategy,
    val memo: JsonObject,
    val initialized: Boolean,
    val coverLastModified: Long?,
)

fun Manga.toMangaRemoteUpdate(): MangaRemoteUpdate = MangaRemoteUpdate(
    id = id,
    title = title,
    author = author,
    artist = artist,
    description = description,
    genre = genre,
    status = status,
    thumbnailUrl = thumbnailUrl,
    updateStrategy = updateStrategy,
    memo = memo,
    initialized = initialized,
    coverLastModified = coverLastModified,
)
