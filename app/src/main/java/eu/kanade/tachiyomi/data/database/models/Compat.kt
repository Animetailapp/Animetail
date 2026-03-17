package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.data.database.models.manga.Chapter
import eu.kanade.tachiyomi.data.database.models.manga.toDomainChapter as toNewDomainChapter
import tachiyomi.domain.items.chapter.model.Chapter as DomainChapter

fun Chapter.toDomainChapter(): DomainChapter? = toNewDomainChapter()
