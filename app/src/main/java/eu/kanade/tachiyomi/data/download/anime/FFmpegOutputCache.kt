package eu.kanade.tachiyomi.data.download.anime

import java.io.File
import java.io.IOException

/**
 * Local files FFmpeg writes a download to when the download directory is a document tree, which
 * FFmpeg can't write to directly. The finished video is then copied into the episode directory.
 *
 * A finished video is kept here until it is saved in the episode directory: that copy fails when
 * the storage is too full for a second copy of the video, and some document providers fail it or
 * the rename that follows. The next attempt then only copies it again instead of downloading the
 * whole episode again.
 */
internal class FFmpegOutputCache(private val dir: File) {

    /** The file FFmpeg writes the episode's video to. */
    fun partialFile(episodeId: Long): File {
        dir.mkdirs()
        return File(dir, "$EPISODE_PREFIX$episodeId$PARTIAL_EXTENSION")
    }

    /** The episode's video FFmpeg finished, if it exists. */
    fun finishedFile(episodeId: Long): File? = File(dir, "$EPISODE_PREFIX$episodeId.mkv").takeIf(File::isFile)

    /** Marks the episode's [partialFile] as finished. */
    fun finish(episodeId: Long): File {
        val finished = File(dir, "$EPISODE_PREFIX$episodeId.mkv")
        if (!partialFile(episodeId).renameTo(finished)) throw IOException("Could not keep the downloaded video")
        return finished
    }

    /**
     * Deletes the files of downloads that are not running: everything except the finished videos
     * of [keepEpisodeIds], the downloads still queued.
     */
    fun clean(keepEpisodeIds: Set<Long>) {
        dir.listFiles().orEmpty().forEach { file ->
            val episodeId = file.name.removePrefix(EPISODE_PREFIX).removeSuffix(".mkv").toLongOrNull()
            val keep = file.name.startsWith(EPISODE_PREFIX) && episodeId != null && episodeId in keepEpisodeIds
            if (!keep) file.deleteRecursively()
        }
    }

    private companion object {
        const val EPISODE_PREFIX = "episode_"
        const val PARTIAL_EXTENSION = ".part"
    }
}
