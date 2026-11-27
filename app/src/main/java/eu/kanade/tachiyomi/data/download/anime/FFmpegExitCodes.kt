package eu.kanade.tachiyomi.data.download.anime

/**
 * What FFmpeg's exit code [code] (a negative AVERROR) means, for the common ones, or null.
 */
internal fun ffmpegExitCodeText(code: Int): String? = FFMPEG_EXIT_CODES[code]

// FFERRTAG in libavutil/error.h
private fun avErrorTag(a: Int, b: Char, c: Char, d: Char): Int =
    -(a or (b.code shl 8) or (c.code shl 16) or (d.code shl 24))

private val FFMPEG_EXIT_CODES = mapOf(
    avErrorTag('I'.code, 'N', 'D', 'A') to "Invalid data found when processing input",
    avErrorTag('E'.code, 'O', 'F', ' ') to "End of file",
    avErrorTag(0xF8, '4', '0', '0') to "Server returned 400 Bad Request",
    avErrorTag(0xF8, '4', '0', '1') to "Server returned 401 Unauthorized",
    avErrorTag(0xF8, '4', '0', '3') to "Server returned 403 Forbidden",
    avErrorTag(0xF8, '4', '0', '4') to "Server returned 404 Not Found",
    avErrorTag(0xF8, '4', 'X', 'X') to "Server returned 4XX Client Error",
    avErrorTag(0xF8, '5', 'X', 'X') to "Server returned 5XX Server Error",
    // AVERROR(errno)
    -5 to "I/O error",
    -28 to "No space left on device",
    -104 to "Connection reset by peer",
    -110 to "Connection timed out",
    -111 to "Connection refused",
)
