package eu.kanade.tachiyomi.ui.player.cache

/** A single `Range: bytes=start-end` request; [end] is inclusive and null when open-ended. */
data class ByteRange(val start: Long, val end: Long?) {

    val header: String get() = "bytes=$start-${end ?: ""}"

    companion object {
        private val RANGE = Regex("""bytes=(\d+)-(\d*)""")
        private val CONTENT_RANGE_TOTAL = Regex("""bytes\s+\d+-\d+/(\d+)""")

        /** Returns null for headers other than a single range with a start, e.g. suffix ranges. */
        fun parse(header: String): ByteRange? {
            val match = RANGE.matchEntire(header.trim()) ?: return null
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val end = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLongOrNull()
            if (end != null && end < start) return null
            return ByteRange(start, end)
        }

        /** Total size from a `Content-Range: bytes 0-99/1000` header, null when it's unknown. */
        fun totalLength(contentRange: String?): Long? =
            contentRange?.let { CONTENT_RANGE_TOTAL.find(it) }?.groupValues?.get(1)?.toLongOrNull()
    }
}

/** Last byte to serve of a resource of [length] bytes. */
fun ByteRange?.endFor(length: Long): Long = minOf(this?.end ?: Long.MAX_VALUE, length - 1)
