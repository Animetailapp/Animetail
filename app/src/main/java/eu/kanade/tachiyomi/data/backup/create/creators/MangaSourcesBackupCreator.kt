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

    suspend operator fun invoke(mangas: List<BackupManga>): List<BackupSource> {
        return mangas
            .map(BackupManga::source)
            .distinct()
            .map { mangaSourceManager.getOrStub(it).toBackupSource() }
    }
}

private fun MangaSource.toBackupSource() =
    BackupSource(
        name = this.name,
        sourceId = this.id,
    )
