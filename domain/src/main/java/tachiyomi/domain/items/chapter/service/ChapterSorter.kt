package tachiyomi.domain.items.chapter.service

import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

fun getChapterSort(manga: Manga, sortDescending: Boolean = manga.sortDescending()): (
    Chapter,
    Chapter,
) -> Int {
    return when (manga.sorting) {
        Manga.CHAPTER_SORTING_SOURCE -> when (sortDescending) {
            true -> { c1, c2 -> c1.sourceOrder.compareTo(c2.sourceOrder) }
            false -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
        }

        Manga.CHAPTER_SORTING_NUMBER -> when (sortDescending) {
            true -> { c1, c2 -> c2.chapterNumber.compareTo(c1.chapterNumber) }
            false -> { c1, c2 -> c1.chapterNumber.compareTo(c2.chapterNumber) }
        }

        Manga.CHAPTER_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { c1, c2 ->
                val d1 = c1.dateUploadOverride.takeIf { it > 0 } ?: c1.dateUpload
                val d2 = c2.dateUploadOverride.takeIf { it > 0 } ?: c2.dateUpload
                d2.compareTo(d1)
            }
            false -> { c1, c2 ->
                val d1 = c1.dateUploadOverride.takeIf { it > 0 } ?: c1.dateUpload
                val d2 = c2.dateUploadOverride.takeIf { it > 0 } ?: c2.dateUpload
                d1.compareTo(d2)
            }
        }

        Manga.CHAPTER_SORTING_ALPHABET -> when (sortDescending) {
            true -> { c1, c2 -> c2.name.compareToWithCollator(c1.name) }
            false -> { c1, c2 -> c1.name.compareToWithCollator(c2.name) }
        }

        else -> throw NotImplementedError("Invalid chapter sorting method: ${manga.sorting}")
    }
}
