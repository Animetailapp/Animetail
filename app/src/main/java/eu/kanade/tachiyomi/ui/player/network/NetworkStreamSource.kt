package eu.kanade.tachiyomi.ui.player.network

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * Minimal source used when playing manual network streams so player components can rely on a
 * non-null [AnimeSource] instance.
 */
object NetworkStreamSource : AnimeSource {
    override val id: Long = Long.MIN_VALUE + 42
    override val name: String = "Network Stream"

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()

    override val supportsLatest: Boolean = false

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        throw UnsupportedOperationException()
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        throw UnsupportedOperationException()
    }

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage {
        throw UnsupportedOperationException()
    }

    override suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate {
        throw UnsupportedOperationException()
    }

    override suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate {
        throw UnsupportedOperationException()
    }
}
