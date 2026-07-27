package tachiyomi.domain.chapter.model

import tachiyomi.domain.items.chapter.model.Chapter as NewChapter
import tachiyomi.domain.items.chapter.model.toChapterUpdate as toNewChapterUpdate

typealias Chapter = tachiyomi.domain.items.chapter.model.Chapter
typealias ChapterUpdate = tachiyomi.domain.items.chapter.model.ChapterUpdate
typealias NoChaptersException = tachiyomi.domain.items.chapter.model.NoChaptersException

fun NewChapter.toChapterUpdate(): ChapterUpdate = toNewChapterUpdate()
