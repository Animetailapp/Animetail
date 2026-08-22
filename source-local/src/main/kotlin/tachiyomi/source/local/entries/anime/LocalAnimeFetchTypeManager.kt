package tachiyomi.source.local.entries.anime

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.model.FetchType
import tachiyomi.source.local.io.ArchiveAnime
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem

@Inject
@SingleIn(AppScope::class)
class LocalAnimeFetchTypeManager(
    private val context: Context,
    private val fileSystem: LocalAnimeSourceFileSystem,
) {
    fun find(animeUrl: String): FetchType {
        val files = fileSystem.getFilesInAnimeDirectory(animeUrl)

        return when {
            files.any { ArchiveAnime.isSupported(it) } -> FetchType.Episodes
            files.any { it.isDirectory && !it.name.orEmpty().startsWith('.') } -> FetchType.Seasons
            else -> FetchType.Episodes
        }
    }
}
