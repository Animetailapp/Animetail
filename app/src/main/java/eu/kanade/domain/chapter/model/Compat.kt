package eu.kanade.domain.chapter.model

import tachiyomi.domain.items.chapter.model.Chapter
import eu.kanade.domain.items.chapter.model.toDbChapter as toNewDbChapter
import eu.kanade.tachiyomi.data.database.models.manga.Chapter as DbChapter

fun Chapter.toDbChapter(): DbChapter = toNewDbChapter()
