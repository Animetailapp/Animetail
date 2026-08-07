package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import rx.Observable
import eu.kanade.tachiyomi.animesource.model.SAnime as SManga

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc...
 */
interface AnimeSource {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): AnimeFilterList = AnimeFilterList()

    /**
     * Get a page with a list of anime.
     *
     * @since extensions-lib 17
     * @param page the page number to retrieve.
     */
    suspend fun getPopularAnime(page: Int): AnimesPage

    /**
     * Get a page with a list of latest anime updates.
     *
     * @since extensions-lib 17
     * @param page the page number to retrieve.
     */
    suspend fun getLatestUpdates(page: Int): AnimesPage

    /**
     * Get a page with a list of anime.
     *
     * @since extensions-lib 17
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage

    /**
     * Fetches updated information for an anime.
     *
     * Depending on the provided flags or source availability, this may include
     * updated anime metadata, available episodes, or both.
     *
     * If a value is not requested, the existing provided value can be returned as-is.
     * The host app may apply any returned updates regardless of the flags,
     * so care should be taken to only return accurate and intentional changes.
     *
     * @since extensions-lib 17
     * @param anime The anime to fetch updates for.
     * @param episodes Existing episodes of the anime.
     * @param fetchDetails Whether to fetch updated anime details.
     * @param fetchEpisodes Whether to fetch available episodes.
     */
    suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate {
        val updatedAnime = if (fetchDetails) getAnimeDetails(anime) else anime
        val updatedEpisodes = if (fetchEpisodes) getEpisodeList(anime) else episodes
        return SAnimeEpisodeUpdate(updatedAnime, updatedEpisodes)
    }

    /**
     * Fetches updated information for an anime.
     *
     * Depending on the provided flags or source availability, this may include
     * updated anime metadata, available seasons, or both.
     *
     * If a value is not requested, the existing provided value can be returned as-is.
     * The host app may apply any returned updates regardless of the flags,
     * so care should be taken to only return accurate and intentional changes.
     *
     * @since extensions-lib 17
     * @param anime The anime to fetch updates for.
     * @param seasons Existing seasons of the anime.
     * @param fetchDetails Whether to fetch updated anime details.
     * @param fetchSeasons Whether to fetch available seasons.
     */
    suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate {
        val updatedAnime = if (fetchDetails) getAnimeDetails(anime) else anime
        val updatedSeasons = if (fetchSeasons) getSeasonList(anime) else seasons
        return SAnimeSeasonUpdate(updatedAnime, updatedSeasons)
    }

    /**
     * Get the list of hoster for an episode. The first hoster in the list should
     * be the preferred hoster.
     *
     * @since extensions-lib 16
     * @param episode the episode.
     * @return the hosters for the episode.
     */
    suspend fun getHosterList(episode: SEpisode): List<Hoster> = throw IllegalStateException("Not used")

    /**
     * Get the list of videos for a hoster.
     *
     * @since extensions-lib 16
     * @param hoster the hoster.
     * @return the videos for the hoster.
     */
    suspend fun getVideoList(hoster: Hoster): List<Video> = throw IllegalStateException("Not used")

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getAnimeSeasonUpdate"))
    suspend fun getSeasonList(anime: SAnime): List<SAnime> = throw UnsupportedOperationException()

    @Deprecated("Use the hoster version instead")
    suspend fun getVideoList(episode: SEpisode): List<Video> = throw UnsupportedOperationException()

    @Deprecated(
        "Use the combined suspend API instead",
        ReplaceWith("getAnimeEpisodeUpdate"),
    )
    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> = throw UnsupportedOperationException()

    @Deprecated(
        "Use the combined suspend API instead",
        ReplaceWith("getAnimeEpisodeUpdate"),
    )
    suspend fun getAnimeDetails(anime: SAnime): SAnime = throw UnsupportedOperationException()

    @Deprecated(
        "Use the combined suspend API instead",
        ReplaceWith("getAnimeEpisodeUpdate"),
    )
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = throw UnsupportedOperationException()

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getEpisodeList"),
    )
    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> = throw UnsupportedOperationException()

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getVideoList"),
    )
    fun fetchVideoList(episode: SEpisode): Observable<List<Video>> =
        throw IllegalStateException("Not used")

    // KMK -->

    /**
     * Get all the available related animes for a anime.
     *
     * @since komikku/extensions-lib 1.6
     * @param anime the current anime to get related animes.
     * @return a list of <keyword, related animes>
     */
    suspend fun getRelatedAnimeList(
        anime: SAnime,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ): Unit = throw UnsupportedOperationException()
    suspend fun getRelatedMangaList(
        manga: SManga,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
    ) = getRelatedAnimeList(manga, exceptionHandler, pushResults)
    // KMK <--
}
