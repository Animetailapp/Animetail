package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@Inject
class AnimeSourcesBackupCreator(
    private val animeSourceManager: AnimeSourceManager,
) {

    operator fun invoke(sourceIds: Set<Long>): List<BackupAnimeSource> {
        return sourceIds.map { animeSourceManager.getOrStub(it).toBackupSource() }
    }

    operator fun invoke(animes: List<BackupAnime>): List<BackupAnimeSource> {
        return invoke(animes.map { it.source }.toSet())
    }
}

private fun AnimeSource.toBackupSource() =
    BackupAnimeSource(
        name = this.name,
        sourceId = this.id,
    )
