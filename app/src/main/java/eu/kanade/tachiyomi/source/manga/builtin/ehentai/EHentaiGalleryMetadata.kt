package eu.kanade.tachiyomi.source.manga.builtin.ehentai

import kotlinx.serialization.Serializable

/**
 * Rich metadata for an E-Hentai/ExHentai gallery.
 * Stored as JSON in [SManga.description] after a special marker prefix,
 * so it survives through the standard manga data pipeline without requiring
 * new database columns or interfaces.
 *
 * Mirrors the fields shown in Komikku's metadata detail screen.
 */
@Serializable
data class EHentaiGalleryMetadata(
    val gId: String = "",
    val gToken: String = "",
    val exh: Boolean = false,
    val thumbnailUrl: String? = null,
    val title: String? = null,
    val altTitle: String? = null,
    val genre: String? = null,
    val datePosted: String? = null,
    val visible: String? = null,
    val language: String? = null,
    val translated: Boolean = false,
    val fileSize: String? = null,
    val length: Int? = null,
    val favorites: Int? = null,
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val uploader: String? = null,
    val parent: String? = null,
    val lastUpdateCheck: Long = 0L,
) {
    companion object {
        /** Marker prefix in SManga.description to identify serialized metadata. */
        const val MARKER = "【EH_META】"

        /** Encode metadata + human-readable description into SManga.description. */
        fun encode(metadata: EHentaiGalleryMetadata, humanDescription: String): String {
            val json = kotlinx.serialization.json.Json.encodeToString(
                serializer(),
                metadata,
            )
            return "$MARKER$json\n$humanDescription"
        }

        /** Extract metadata from SManga.description, or null if not present. */
        fun decode(description: String?): EHentaiGalleryMetadata? {
            if (description == null || !description.startsWith(MARKER)) return null
            val jsonEnd = description.indexOf('\n')
            val jsonStr = if (jsonEnd >= 0) {
                description.substring(MARKER.length, jsonEnd)
            } else {
                description.substring(MARKER.length)
            }
            return try {
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }.decodeFromString(serializer(), jsonStr)
            } catch (_: Exception) {
                null
            }
        }

        /** Strip the metadata marker from description for display purposes. */
        fun stripMarker(description: String?): String? {
            if (description == null) return null
            if (!description.startsWith(MARKER)) return description
            val newlineIdx = description.indexOf('\n')
            return if (newlineIdx >= 0) {
                description.substring(newlineIdx + 1)
            } else {
                null
            }
        }
    }
}
