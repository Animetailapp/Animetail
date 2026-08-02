package tachiyomi.source.local.io.manga

import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager

class LocalMangaSourceFileSystem(
    private val storageManager: StorageManager,
) {

    fun getBaseDirectory(): UniFile? {
        return storageManager.getLocalMangaSourceDirectory()
    }

    fun getFilesInBaseDirectory(): List<UniFile> {
        return getBaseDirectory()?.listFiles().orEmpty().toList()
    }

    fun getMangaDirectory(name: String): UniFile? {
        return name
            .split('/', '\\')
            .filter { it.isNotBlank() }
            .fold(getBaseDirectory()) { directory, part ->
                directory
                    ?.findFile(part)
                    ?.takeIf { it.isDirectory }
            }
    }

    fun getFilesInMangaDirectory(name: String): List<UniFile> {
        return getMangaDirectory(name)?.listFiles().orEmpty().toList()
    }
}
