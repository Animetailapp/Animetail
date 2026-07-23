package eu.kanade.tachiyomi.ui.library.manga

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.manga.LibraryManga

class MangaLibraryItem(
    val libraryManga: LibraryManga,
    val downloadCount: Int,
    val unreadCount: Long,
    val isLocal: Boolean,
    val sourceName: String,
    val sourceLanguage: String,
    val badges: Badges,
) {
    val manga: Manga get() = libraryManga.manga
    val id: Long get() = libraryManga.id

    data class Badges(
        val downloadCount: Int,
        val unreadCount: Long,
        val isLocal: Boolean,
        val sourceLanguage: String,
    )
}
