package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.core.common.storage.extension

object ArchiveAnime {

    private val SUPPORTED_ARCHIVE_TYPES =
        listOf("avi", "flv", "mkv", "mov", "mp4", "webm", "wmv", "torrent", "m3u", "m3u8")

    fun isSupported(file: UniFile): Boolean {
        if (file.isDirectory) return false
        val ext = file.name?.substringAfterLast('.', "")?.lowercase()
        return !ext.isNullOrEmpty() && ext in SUPPORTED_ARCHIVE_TYPES
    }
}

object ArchiveManga {

    private val SUPPORTED_ARCHIVE_TYPES = listOf("zip", "cbz", "rar", "cbr", "7z", "cb7", "tar", "cbt")

    fun isSupported(file: UniFile): Boolean {
        if (file.isDirectory) return false
        val ext = file.name?.substringAfterLast('.', "")?.lowercase()
        return !ext.isNullOrEmpty() && ext in SUPPORTED_ARCHIVE_TYPES
    }
}
