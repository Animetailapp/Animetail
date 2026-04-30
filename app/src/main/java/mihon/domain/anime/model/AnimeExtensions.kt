package mihon.domain.anime.model

import eu.kanade.tachiyomi.animesource.model.SAnime
import tachiyomi.domain.entries.anime.model.Anime

fun SAnime.toDomainAnime(sourceId: Long): Anime {
    return Anime.create().copy(
        url = this.url,
        ogTitle = this.title,
        ogArtist = this.artist,
        ogAuthor = this.author,
        ogDescription = this.description,
        ogGenre = this.genre?.split(", "),
        ogStatus = this.status.toLong(),
        thumbnailUrl = this.thumbnail_url,
        updateStrategy = update_strategy,
        initialized = initialized,
        source = sourceId,
    )
}
