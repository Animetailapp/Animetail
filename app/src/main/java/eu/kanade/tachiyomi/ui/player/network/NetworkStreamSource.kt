package eu.kanade.tachiyomi.ui.player.network

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime

/**
 * Minimal source used when playing manual network streams so player components can rely on a
 * non-null [AnimeSource] instance.
 */
object NetworkStreamSource : AnimeSource {
    override val id: Long = Long.MIN_VALUE + 42
    override val name: String = "Network Stream"

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()
}
