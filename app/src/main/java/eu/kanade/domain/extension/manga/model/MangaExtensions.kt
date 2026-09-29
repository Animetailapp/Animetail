package eu.kanade.domain.extension.manga.model

import eu.kanade.tachiyomi.extension.manga.model.MangaExtension

data class MangaExtensions(
    val updates: List<MangaExtension.Loaded>,
    val loaded: List<MangaExtension.Loaded>,
    val available: List<MangaExtension.Available>,
    val notLoaded: List<MangaExtension.NotLoaded>,
)
