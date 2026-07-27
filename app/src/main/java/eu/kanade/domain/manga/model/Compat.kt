package eu.kanade.domain.manga.model

import tachiyomi.domain.entries.manga.model.Manga
import eu.kanade.domain.entries.manga.model.readerOrientation as newReaderOrientation
import eu.kanade.domain.entries.manga.model.readingMode as newReadingMode

val Manga.readingMode: Long
    get() = newReadingMode

val Manga.readerOrientation: Long
    get() = newReaderOrientation
