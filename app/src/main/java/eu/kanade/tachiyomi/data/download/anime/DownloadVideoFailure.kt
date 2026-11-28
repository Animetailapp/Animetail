package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.network.HttpException
import java.io.IOException

/** FFmpeg exited with an error; [reason] is the first error it logged. */
class FFmpegFailedException(val reason: String) : IOException("Error in ffmpeg: $reason")

/** FFmpeg finished, but the file it wrote is empty or much shorter than the source. */
class IncompleteVideoException(message: String) : IOException(message)

/**
 * Whether this failure comes from the video's server (it refused the link, stopped sending, or sent
 * something that can't be read), so another video is more likely to work than retrying this one.
 * Local failures, such as a full disk or a folder that can't be written, are not.
 */
fun Throwable.isVideoFailure(): Boolean {
    return when (this) {
        is HttpException, is DownloadStalledException, is IncompleteVideoException -> true
        is FFmpegFailedException -> LOCAL_FFMPEG_ERRORS.none { reason.contains(it, ignoreCase = true) }
        else -> false
    }
}

/** What the download queue shows as the reason of a failure. */
fun Throwable.failureReason(): String = when (this) {
    is FFmpegFailedException -> reason
    else -> message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}

// FFmpeg errors about writing the output rather than reading the video
private val LOCAL_FFMPEG_ERRORS = listOf(
    "No space left on device",
    "Error opening output",
    "Read-only file system",
    "Permission denied",
)

/** How a video is named in the download queue: its hoster and its title, without repeating either. */
fun videoLabel(hosterName: String, videoTitle: String): String {
    val title = videoTitle.trim()
    val hoster = hosterName.trim().takeUnless { it.isEmpty() || it == Hoster.NO_HOSTER_LIST }
    return when {
        hoster == null -> title
        title.isEmpty() || title.contains(hoster, ignoreCase = true) -> title.ifEmpty { hoster }
        else -> "$hoster - $title"
    }
}
