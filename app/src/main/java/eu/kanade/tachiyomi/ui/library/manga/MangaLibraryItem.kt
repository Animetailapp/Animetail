package eu.kanade.tachiyomi.ui.library.manga

import tachiyomi.domain.library.manga.model.LibraryManga

class MangaLibraryItem(
    val libraryManga: LibraryManga,
    val downloadCount: Int,
    val unreadCount: Long,
    val isLocal: Boolean,
    val sourceName: String,
    val sourceLanguage: String,
    val badges: Badges,
) {
    val id: Long = libraryManga.id

    data class Badges(
        val downloadCount: Int,
        val unreadCount: Long,
        val isLocal: Boolean,
        val sourceLanguage: String,
    )
}
