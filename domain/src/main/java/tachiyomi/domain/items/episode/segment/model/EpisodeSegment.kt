package tachiyomi.domain.items.episode.segment.model

/**
 * An opening, ending or recap of a downloaded episode that the app keeps itself: the source's
 * timestamps saved when the episode was downloaded (the file doesn't carry them), or found by
 * opening/ending detection.
 */
data class EpisodeSegment(
    val episodeId: Long,
    val type: Type,
    val source: Source,
    val startMs: Long,
    val endMs: Long,
    /** Size of the downloaded file the segment belongs to. */
    val fileSize: Long,
    val updatedAt: Long,
) {
    enum class Type(val dbValue: String) {
        Opening("opening"),
        Ending("ending"),
        Recap("recap"),

        /** An opening mixed into the episode's story. */
        MixedOpening("mixed_opening"),
        ;

        companion object {
            fun fromDbValue(value: String): Type? = entries.find { it.dbValue == value }
        }
    }

    enum class Source(val dbValue: String) {
        /** Audio shared with other downloaded episodes of the same show. */
        Detected("detected"),

        /** The source's timestamps for the video that was downloaded. */
        Source("source"),
        ;

        companion object {
            fun fromDbValue(value: String): Source? = entries.find { it.dbValue == value }
        }
    }
}
