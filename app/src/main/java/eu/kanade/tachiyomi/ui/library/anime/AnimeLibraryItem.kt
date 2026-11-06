package eu.kanade.tachiyomi.ui.library.anime

import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.anime.LibraryAnime

class AnimeLibraryItem(
    val libraryAnime: LibraryAnime,
    val downloadCount: Int,
    val unseenCount: Long,
    val isLocal: Boolean,
    val sourceName: String,
    val sourceLanguage: String,
    val badges: Badges,
) {
    val anime: Anime get() = libraryAnime.anime
    val id: Long get() = libraryAnime.id

    data class Badges(
        val downloadCount: Int,
        val unseenCount: Long,
        val isLocal: Boolean,
        val sourceLanguage: String,
    )
}
