package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.items.chapter.service.getChapterSort as getNewChapterSort

fun getChapterSort(manga: Manga, sortDescending: Boolean = manga.sortDescending()): (Chapter, Chapter) -> Int {
    return getNewChapterSort(manga, sortDescending)
}
