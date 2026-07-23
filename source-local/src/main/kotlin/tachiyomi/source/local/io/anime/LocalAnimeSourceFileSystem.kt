package tachiyomi.source.local.io.anime

import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager

class LocalAnimeSourceFileSystem(
    private val storageManager: StorageManager,
) {

    fun getBaseDirectory(): UniFile? {
        return storageManager.getLocalAnimeSourceDirectory()
    }

    fun getFilesInBaseDirectory(): List<UniFile> {
        return getBaseDirectory()?.listFiles().orEmpty().toList()
    }

    fun getAnimeDirectory(name: String): UniFile? {
        return name
            .split('/', '\\')
            .filter { it.isNotBlank() }
            .fold(getBaseDirectory()) { directory, part ->
                directory
                    ?.findFile(part)
                    ?.takeIf { it.isDirectory }
            }
    }

    fun getFilesInAnimeDirectory(name: String): List<UniFile> {
        return getAnimeDirectory(name)?.listFiles().orEmpty().toList()
    }
}
