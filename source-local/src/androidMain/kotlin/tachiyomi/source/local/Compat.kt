package tachiyomi.source.local

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.source.local.entries.manga.LocalMangaSource
import tachiyomi.source.local.entries.manga.isLocal as isLocalManga

typealias LocalSource = LocalMangaSource

fun Manga.isLocal(): Boolean = isLocalManga()
