package tachiyomi.source.local.image.anime

import android.content.Context
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import java.io.InputStream

private const val DEFAULT_BACKGROUND_NAME = "background.jpg"

@Inject
@SingleIn(AppScope::class)
class LocalAnimeBackgroundManager(
    private val context: Context,
    private val fileSystem: LocalAnimeSourceFileSystem,
) {

    fun find(animeUrl: String): UniFile? {
        return fileSystem.getFilesInAnimeDirectory(animeUrl)
            .filter { file ->
                file.isFile && (
                    file.nameWithoutExtension.equals("background", ignoreCase = true) ||
                        file.nameWithoutExtension.equals("fanart", ignoreCase = true) ||
                        file.nameWithoutExtension.equals("backdrop", ignoreCase = true) ||
                        file.nameWithoutExtension.equals("banner", ignoreCase = true)
                    )
            }
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    fun update(anime: SAnime, inputStream: InputStream): UniFile? {
        val directory = fileSystem.getAnimeDirectory(anime.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        val targetFile = find(anime.url) ?: directory.createFile(DEFAULT_BACKGROUND_NAME)!!

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.createNoMediaFile(directory, context)

        anime.background_url = targetFile.uri.toString()
        return targetFile
    }
}
