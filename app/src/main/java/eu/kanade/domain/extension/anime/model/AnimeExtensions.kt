package eu.kanade.domain.extension.anime.model

import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension

data class AnimeExtensions(
    val updates: List<AnimeExtension.Loaded>,
    val loaded: List<AnimeExtension.Loaded>,
    val available: List<AnimeExtension.Available>,
    val notLoaded: List<AnimeExtension.NotLoaded>,
)
