package mihon.domain.manga.model

import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.entries.manga.model.Manga

fun SManga.toDomainManga(sourceId: Long): Manga {
    return Manga.create().copy(
        url = this.url,
        ogTitle = this.title,
        ogArtist = this.artist,
        ogAuthor = this.author,
        ogDescription = this.description,
        ogGenre = this.genre?.split(", "),
        ogStatus = this.status.toLong(),
        thumbnailUrl = this.thumbnail_url,
        source = sourceId,
    )
}
