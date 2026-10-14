package tachiyomi.domain.items.chapter.model

import kotlinx.serialization.json.JsonObject

data class ChapterRemoteUpdate(
    val id: Long,
    val name: String,
    val scanlator: String?,
    val chapterNumber: Double,
    val dateUpload: Long?,
    val sourceOrder: Long,
    val memo: JsonObject,
)
