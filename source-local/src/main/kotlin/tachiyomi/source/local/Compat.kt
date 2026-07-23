package tachiyomi.source.local

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.source.local.entries.manga.LocalMangaSource

typealias LocalSource = LocalMangaSource

fun Manga.isLocal(): Boolean = source == LocalMangaSource.ID
