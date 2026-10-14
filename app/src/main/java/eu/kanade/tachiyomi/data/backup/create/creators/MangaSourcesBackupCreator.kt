package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.source.MangaSource
import tachiyomi.domain.source.manga.service.MangaSourceManager

@Inject
class MangaSourcesBackupCreator(
    private val mangaSourceManager: MangaSourceManager,
) {

    suspend operator fun invoke(sourceIds: Set<Long>): List<BackupSource> {
        return sourceIds.map { mangaSourceManager.getOrStub(it).toBackupSource() }
    }

    suspend operator fun invoke(mangas: List<BackupManga>): List<BackupSource> {
        return invoke(mangas.map { it.source }.toSet())
    }
}

private fun MangaSource.toBackupSource() =
    BackupSource(
        name = this.name,
        sourceId = this.id,
    )
