package eu.kanade.tachiyomi.ui.home

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.home.HomeItemData
import eu.kanade.presentation.home.MediaType
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.source.local.entries.anime.isLocal
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

enum class HomeMediaFilter {
    ALL, VIDEO_ONLY, MANGA_ONLY
}

enum class HeroSource {
    BOTH, LIBRARY_ONLY, TRACKERS_ONLY
}

class HomeFeedScreenModel(
    private val getAnimeHistory: GetAnimeHistory = Injekt.get(),
    private val getMangaHistory: GetMangaHistory = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getEpisode: GetEpisode = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val getAnimeTracks: GetAnimeTracks = Injekt.get(),
    private val getMangaTracks: GetMangaTracks = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) : StateScreenModel<HomeFeedScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val heroList: List<HomeItemData> = emptyList(),
        val continueList: List<HomeItemData> = emptyList(),
        val becauseYouWatchedTitle: String? = null,
        val becauseYouWatchedIsAnime: Boolean = true,
        val becauseYouWatchedList: List<HomeItemData> = emptyList(),
        val recommendedList: List<HomeItemData> = emptyList(),
        val animeList: List<HomeItemData> = emptyList(),
        val mangaList: List<HomeItemData> = emptyList(),
        val showFeatured: Boolean = true,
        val showContinue: Boolean = true,
        val showBecauseYouWatched: Boolean = true,
        val showRecommended: Boolean = true,
        val showPopularAnime: Boolean = true,
        val showPopularManga: Boolean = true,
        val mediaFilter: HomeMediaFilter = HomeMediaFilter.ALL,
        val autoScrollHero: Boolean = true,
        val heroSource: HeroSource = HeroSource.BOTH,
        val itemsPerSection: Int = 12,
        val hideCompletedInRecommended: Boolean = false,
    )

    private data class HomeDataPayload(
        val animeHistories: List<AnimeHistoryWithRelations>,
        val mangaHistories: List<MangaHistoryWithRelations>,
        val libraryAnimeList: List<LibraryAnime>,
        val libraryMangaList: List<LibraryManga>,
    )

    private data class HomePrefsPayload(
        val mediaFilterOrdinal: Int,
        val autoScrollHero: Boolean,
        val heroSourceOrdinal: Int,
        val limit: Int,
        val hideCompleted: Boolean,
    )

    private data class HomeVisibilityPayload(
        val showFeatured: Boolean,
        val showContinue: Boolean,
        val showBecauseYouWatched: Boolean,
        val showRecommended: Boolean,
        val showPopularAnime: Boolean,
        val showPopularManga: Boolean,
    )

    init {
        observeHomeData()
        fetchRemoteTrendsAsync()
    }

    fun refresh() {
        screenModelScope.launch {
            mutableState.update { current ->
                val newShuffledRecs = current.recommendedList.shuffled()
                current.copy(
                    isRefreshing = true,
                    recommendedList = newShuffledRecs,
                )
            }
            fetchRemoteTrendsAsync()
            delay(600L)
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun toggleSection(key: String) {
        when (key) {
            "featured" -> uiPreferences.homeShowFeatured.set(!uiPreferences.homeShowFeatured.get())
            "continue" -> uiPreferences.homeShowContinue.set(!uiPreferences.homeShowContinue.get())
            "because_you_watched" -> uiPreferences.homeShowBecauseYouWatched.set(!uiPreferences.homeShowBecauseYouWatched.get())
            "recommended" -> uiPreferences.homeShowRecommended.set(!uiPreferences.homeShowRecommended.get())
            "popular_anime" -> uiPreferences.homeShowPopularAnime.set(!uiPreferences.homeShowPopularAnime.get())
            "popular_manga" -> uiPreferences.homeShowPopularManga.set(!uiPreferences.homeShowPopularManga.get())
        }
    }

    fun setMediaFilter(filter: HomeMediaFilter) {
        uiPreferences.homeMediaFilter.set(filter.ordinal)
    }

    fun toggleAutoScrollHero() {
        uiPreferences.homeAutoScrollHero.set(!uiPreferences.homeAutoScrollHero.get())
    }

    fun setHeroSource(source: HeroSource) {
        uiPreferences.homeHeroSource.set(source.ordinal)
        fetchRemoteTrendsAsync()
    }

    fun setItemsPerSection(count: Int) {
        uiPreferences.homeItemsPerSection.set(count)
    }

    fun toggleHideCompletedInRecommended() {
        uiPreferences.homeHideCompleted.set(!uiPreferences.homeHideCompleted.get())
    }

    private fun fetchRemoteTrendsAsync() {
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val currentSource = HeroSource.entries.getOrElse(uiPreferences.homeHeroSource.get()) { HeroSource.BOTH }
                if (currentSource == HeroSource.LIBRARY_ONLY) {
                    return@launch
                }

                val remoteItems = mutableListOf<HomeItemData>()

                // 1. Tendencias de AniList (Anime)
                if (trackerManager.aniList.isLoggedIn) {
                    try {
                        val popularAnime = trackerManager.aniList.getPopularAnime()
                        remoteItems += popularAnime.map { track ->
                            val classified = classifyMediaHybrid(
                                title = track.title,
                                description = track.summary,
                            )
                            HomeItemData(
                                id = track.remote_id,
                                isAnime = true,
                                inLibrary = false,
                                title = track.title,
                                subtitle = classified.name,
                                coverUrl = track.cover_url,
                                mediaType = classified,
                                rating = if (track.score > 0) String.format("%.1f", track.score / 10.0) else "",
                                synopsis = track.summary,
                            )
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "Failed to fetch AniList trends" }
                    }
                }

                // 2. Tendencias de TMDB (Películas y Series)
                if (trackerManager.tmdb.isLoggedIn) {
                    try {
                        val movies = trackerManager.tmdb.getTrendingMovies().map { track ->
                            HomeItemData(
                                id = track.remote_id,
                                isAnime = true,
                                inLibrary = false,
                                title = track.title,
                                subtitle = "Película",
                                coverUrl = track.cover_url,
                                mediaType = MediaType.MOVIES,
                                rating = if (track.score > 0) String.format("%.1f", track.score) else "",
                                synopsis = track.summary,
                            )
                        }
                        val series = trackerManager.tmdb.getTrendingTv().map { track ->
                            HomeItemData(
                                id = track.remote_id,
                                isAnime = true,
                                inLibrary = false,
                                title = track.title,
                                subtitle = "Serie",
                                coverUrl = track.cover_url,
                                mediaType = MediaType.SERIES,
                                rating = if (track.score > 0) String.format("%.1f", track.score) else "",
                                synopsis = track.summary,
                            )
                        }
                        remoteItems += (movies + series)
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "Failed to fetch TMDB trends" }
                    }
                }

                if (remoteItems.isNotEmpty()) {
                    mutableState.update { current ->
                        current.copy(heroList = remoteItems.shuffled())
                    }
                } else {
                    mutableState.update { current ->
                        val videoOnly = current.animeList.filter { it.mediaType != MediaType.MANGA }
                        if (videoOnly.isNotEmpty()) {
                            current.copy(heroList = videoOnly.shuffled().take(7))
                        } else current
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to fetch remote trends" }
            }
        }
    }

    private fun observeHomeData() {
        val databaseFlow = combine(
            getAnimeHistory.subscribe("").onStart { emit(emptyList()) },
            getMangaHistory.subscribe("").onStart { emit(emptyList()) },
            getLibraryAnime.subscribe().onStart { emit(emptyList()) },
            getLibraryManga.subscribe().onStart { emit(emptyList()) },
        ) { animeHistories, mangaHistories, libraryAnimeList, libraryMangaList ->
            HomeDataPayload(animeHistories, mangaHistories, libraryAnimeList, libraryMangaList)
        }

        val prefsFlow = combine(
            uiPreferences.homeMediaFilter.changes(),
            uiPreferences.homeAutoScrollHero.changes(),
            uiPreferences.homeHeroSource.changes(),
            uiPreferences.homeItemsPerSection.changes(),
            uiPreferences.homeHideCompleted.changes(),
        ) { mediaFilterOrdinal, autoScrollHero, heroSourceOrdinal, limit, hideCompleted ->
            HomePrefsPayload(mediaFilterOrdinal, autoScrollHero, heroSourceOrdinal, limit, hideCompleted)
        }

        val visibilityFlow = combine(
            uiPreferences.homeShowFeatured.changes(),
            uiPreferences.homeShowContinue.changes(),
            uiPreferences.homeShowBecauseYouWatched.changes(),
            uiPreferences.homeShowRecommended.changes(),
            uiPreferences.homeShowPopularAnime.changes(),
            uiPreferences.homeShowPopularManga.changes(),
        ) { values: Array<Boolean> ->
            HomeVisibilityPayload(
                showFeatured = values[0],
                showContinue = values[1],
                showBecauseYouWatched = values[2],
                showRecommended = values[3],
                showPopularAnime = values[4],
                showPopularManga = values[5],
            )
        }

        combine(databaseFlow, prefsFlow, visibilityFlow) { dataPayload, prefsPayload, visPayload ->
            val animeHistories = dataPayload.animeHistories
            val mangaHistories = dataPayload.mangaHistories
            val libraryAnimeList = dataPayload.libraryAnimeList
            val libraryMangaList = dataPayload.libraryMangaList

            val filter = HomeMediaFilter.entries.getOrElse(prefsPayload.mediaFilterOrdinal) { HomeMediaFilter.ALL }
            val heroSource = HeroSource.entries.getOrElse(prefsPayload.heroSourceOrdinal) { HeroSource.BOTH }
            val limit = prefsPayload.limit
            val hideCompleted = prefsPayload.hideCompleted
            val autoScrollHero = prefsPayload.autoScrollHero

            val showFeatured = visPayload.showFeatured
            val showContinue = visPayload.showContinue
            val showBecauseYouWatched = visPayload.showBecauseYouWatched
            val showRecommended = visPayload.showRecommended
            val showPopularAnime = visPayload.showPopularAnime
            val showPopularManga = visPayload.showPopularManga

            val pinnedAnimeSources = sourcePreferences.pinnedAnimeSources.get()
            val pinnedMangaSources = sourcePreferences.pinnedMangaSources.get()
            val hasLoggedInTrackers = trackerManager.loggedInTrackers().isNotEmpty()

            val animeMap = libraryAnimeList.associateBy { it.anime.id }

            // 1. Tarjetas de Continuar viendo (Anime, Series y Películas)
            val continueAnime = if (filter != HomeMediaFilter.MANGA_ONLY) {
                animeHistories.map { relation ->
                    val epNum = if (relation.episodeNumber % 1.0 == 0.0) {
                        relation.episodeNumber.toInt().toString()
                    } else {
                        relation.episodeNumber.toString()
                    }

                    val ep = getEpisode.await(relation.episodeId)
                    val lastSecond = ep?.lastSecondSeen ?: 0L
                    val totalSeconds = ep?.totalSeconds ?: 0L
                    val progressRatio = if (totalSeconds > 0L) {
                        (lastSecond.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0.5f
                    }

                    val timeFormatted = if (lastSecond > 0L) {
                        if (totalSeconds > 0L) {
                            "${formatTime(lastSecond)} / ${formatTime(totalSeconds)}"
                        } else {
                            "min ${formatTime(lastSecond)}"
                        }
                    } else {
                        "Ep. $epNum"
                    }

                    val libAnime = animeMap[relation.animeId]?.anime ?: getAnime.await(relation.animeId)
                    val realSourceName = libAnime?.source?.let { sourceManager.getOrStub(it).name } ?: ""

                    val classifiedType = classifyMediaHybrid(
                        animeId = relation.animeId,
                        title = relation.title,
                        genre = libAnime?.genre,
                        description = libAnime?.description,
                        sourceName = realSourceName,
                    )

                    val subtitleText = when (classifiedType) {
                        MediaType.MOVIES -> "Película"
                        MediaType.SERIES -> "Serie • Ep. $epNum"
                        else -> "Episodio $epNum"
                    }

                    HomeItemData(
                        id = relation.animeId,
                        isAnime = true,
                        inLibrary = true,
                        episodeId = relation.episodeId,
                        title = relation.title,
                        subtitle = subtitleText,
                        coverData = relation.coverData,
                        mediaType = classifiedType,
                        progress = progressRatio,
                        remainingInfo = timeFormatted,
                        synopsis = libAnime?.description ?: relation.title,
                        genres = libAnime?.genre?.joinToString(", ") ?: "",
                        lastUpdatedTimestamp = relation.seenAt?.time ?: 0L,
                    )
                }
            } else {
                emptyList()
            }

            // 2. Tarjetas de Continuar leyendo (Manga)
            val continueManga = if (filter != HomeMediaFilter.VIDEO_ONLY) {
                mangaHistories.map { relation ->
                    val chNum = if (relation.chapterNumber % 1.0 == 0.0) {
                        relation.chapterNumber.toInt().toString()
                    } else {
                        relation.chapterNumber.toString()
                    }

                    val ch = getChapter.await(relation.chapterId)
                    val pageRead = ch?.lastPageRead ?: relation.lastPageRead
                    val pageFormatted = if (pageRead > 0) "Pág $pageRead" else "Cap. $chNum"

                    HomeItemData(
                        id = relation.mangaId,
                        isAnime = false,
                        inLibrary = true,
                        chapterId = relation.chapterId,
                        title = relation.title,
                        subtitle = "Capítulo $chNum",
                        coverData = relation.coverData,
                        mediaType = MediaType.MANGA,
                        progress = 0.8f,
                        remainingInfo = pageFormatted,
                        synopsis = relation.title,
                        lastUpdatedTimestamp = relation.readAt?.time ?: 0L,
                    )
                }
            } else {
                emptyList()
            }

            val unifiedContinue = (continueAnime + continueManga)
                .sortedByDescending { it.lastUpdatedTimestamp }
                .take(20)

            // 3. Listas de Biblioteca (Online vs Local)
            val nonLocalAnime = if (filter != HomeMediaFilter.MANGA_ONLY) libraryAnimeList.filterNot { it.anime.isLocal() } else emptyList()
            val nonLocalManga = if (filter != HomeMediaFilter.VIDEO_ONLY) libraryMangaList.filterNot { it.manga.isLocal() } else emptyList()

            val pinnedAnimeList = nonLocalAnime.filter { "${it.anime.source}" in pinnedAnimeSources }
            val sourceAnimeList = if (pinnedAnimeList.isNotEmpty()) pinnedAnimeList else nonLocalAnime

            val pinnedMangaList = nonLocalManga.filter { "${it.manga.source}" in pinnedMangaSources }
            val sourceMangaList = if (pinnedMangaList.isNotEmpty()) pinnedMangaList else nonLocalManga

            val animeItems = sourceAnimeList.map { lib ->
                val anime = lib.anime
                val realSourceName = sourceManager.getOrStub(anime.source).name

                val mediaType = classifyMediaHybrid(
                    animeId = anime.id,
                    title = anime.title,
                    genre = anime.genre,
                    description = anime.description,
                    sourceName = realSourceName,
                )

                val ratingStr = if (hasLoggedInTrackers) {
                    val tracks = getAnimeTracks.await(anime.id)
                    val realScore = tracks.firstOrNull { it.score > 0 }?.score ?: 0.0
                    if (realScore > 0) String.format("%.1f", realScore) else ""
                } else {
                    ""
                }

                HomeItemData(
                    id = anime.id,
                    isAnime = true,
                    inLibrary = true,
                    title = anime.title,
                    subtitle = anime.genre?.firstOrNull() ?: mediaType.name,
                    coverData = anime.asAnimeCover(),
                    mediaType = mediaType,
                    rating = ratingStr,
                    synopsis = anime.description ?: anime.title,
                    genres = anime.genre?.joinToString(", ") ?: "",
                )
            }

            val mangaItems = sourceMangaList.map { lib ->
                val manga = lib.manga
                val ratingStr = if (hasLoggedInTrackers) {
                    val tracks = getMangaTracks.await(manga.id)
                    val realScore = tracks.firstOrNull { it.score > 0 }?.score ?: 0.0
                    if (realScore > 0) String.format("%.1f", realScore) else ""
                } else {
                    ""
                }

                HomeItemData(
                    id = manga.id,
                    isAnime = false,
                    inLibrary = true,
                    title = manga.title,
                    subtitle = manga.genre?.firstOrNull() ?: "Manga",
                    coverData = manga.asMangaCover(),
                    mediaType = MediaType.MANGA,
                    rating = ratingStr,
                    synopsis = manga.description ?: manga.title,
                    genres = manga.genre?.joinToString(", ") ?: "",
                )
            }

            // 4. Cálculo de la sección inteligente "Porque viste / leíste..."
            val lastInteractedItem = unifiedContinue.firstOrNull()
            val targetGenres = lastInteractedItem?.genres?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotBlank() } ?: emptyList()

            val becauseYouWatchedList = if (lastInteractedItem != null && targetGenres.isNotEmpty()) {
                (animeItems + mangaItems)
                    .filter { item ->
                        item.id != lastInteractedItem.id &&
                            item.genres.split(",").any { g -> g.trim().lowercase() in targetGenres } &&
                            (!hideCompleted || item.progress < 1.0f)
                    }
                    .distinctBy { it.id }
                    .take(limit)
            } else {
                emptyList()
            }

            val filteredRecs = (animeItems + mangaItems)
                .filter { !hideCompleted || it.progress < 1.0f }
            val unifiedRecommended = filteredRecs.shuffled()

            val videoItems = animeItems.filter { it.mediaType != MediaType.MANGA }
            val fallbackCarousel = if (videoItems.isNotEmpty()) {
                videoItems.distinctBy { it.id }.take(7)
            } else {
                animeItems.distinctBy { it.id }.take(7)
            }

            val currentHeroList = when (heroSource) {
                HeroSource.LIBRARY_ONLY -> fallbackCarousel
                HeroSource.TRACKERS_ONLY -> mutableState.value.heroList
                HeroSource.BOTH -> mutableState.value.heroList.ifEmpty { fallbackCarousel }
            }

            State(
                isLoading = false,
                isRefreshing = false,
                heroList = currentHeroList,
                continueList = unifiedContinue,
                becauseYouWatchedTitle = lastInteractedItem?.title,
                becauseYouWatchedIsAnime = lastInteractedItem?.isAnime ?: true,
                becauseYouWatchedList = becauseYouWatchedList,
                recommendedList = unifiedRecommended.take(limit),
                animeList = animeItems.take(limit),
                mangaList = mangaItems.take(limit),
                showFeatured = showFeatured,
                showContinue = showContinue,
                showBecauseYouWatched = showBecauseYouWatched,
                showRecommended = showRecommended,
                showPopularAnime = showPopularAnime,
                showPopularManga = showPopularManga,
                mediaFilter = filter,
                autoScrollHero = autoScrollHero,
                heroSource = heroSource,
                itemsPerSection = limit,
                hideCompletedInRecommended = hideCompleted,
            )
        }
            .catch { logcat(LogPriority.ERROR, it) }
            .onEach { newState -> mutableState.value = newState }
            .launchIn(screenModelScope)
    }

    /**
     * Motor Híbrido por Capas para clasificar contenido en:
     * - MediaType.MOVIES (Películas)
     * - MediaType.SERIES (Series Live Action / Doramas / TV Shows)
     * - MediaType.ANIME (Animación Japonesa / Donghua / Anime Series)
     * - MediaType.MANGA (Manga / Manhwa / Manhua)
     */
    private suspend fun classifyMediaHybrid(
        animeId: Long? = null,
        title: String,
        genre: List<String>? = null,
        description: String? = null,
        totalEpisodes: Long? = null,
        sourceName: String? = null,
    ): MediaType {
        val titleClean = title.lowercase()
        val genreClean = genre?.joinToString(" ")?.lowercase() ?: ""
        val descClean = description?.lowercase() ?: ""
        val sourceClean = sourceName?.lowercase() ?: ""
        val combinedText = "$titleClean $genreClean $descClean $sourceClean"

        // CAPA 1: Metadatos de Trackers vinculados (TMDB / AniList)
        if (animeId != null) {
            try {
                val tracks = getAnimeTracks.await(animeId)
                val tmdbTrack = tracks.firstOrNull { trackerManager.get(it.trackerId) is eu.kanade.tachiyomi.data.track.tmdb.Tmdb }
                if (tmdbTrack != null) {
                    if (titleClean.contains("película") || titleClean.contains("movie") || titleClean.contains("film") || genreClean.contains("película") || genreClean.contains("movie")) {
                        return MediaType.MOVIES
                    }
                    return MediaType.SERIES
                }
            } catch (_: Exception) {}
        }

        // CAPA 2: Análisis por Fuente/Extensión de Aniyomi
        if (sourceClean.isNotEmpty()) {
            when {
                // Fuentes dedicadas exclusivamente a Cine y Series Live-Action
                sourceClean.contains("tmdb") || sourceClean.contains("cuevana") ||
                    sourceClean.contains("pelis") || sourceClean.contains("cine") ||
                    sourceClean.contains("filmaffinity") || sourceClean.contains("movie") -> {
                    return if (titleClean.contains("película") || titleClean.contains("movie") || titleClean.contains("film") || totalEpisodes == 1L) {
                        MediaType.MOVIES
                    } else {
                        MediaType.SERIES
                    }
                }
                // Fuentes dedicadas a Doramas / K-Dramas
                sourceClean.contains("dorama") || sourceClean.contains("kdrama") || sourceClean.contains("drama") -> {
                    return MediaType.SERIES
                }
            }
        }

        // CAPA 3: Expresiones regulares de Películas y Películas Anime (Gekijouban / Movie)
        val movieKeywordsRegex = Regex("""\b(movie|película|pelicula|film|gekijouban|劇場版|the movie|eiga)\b""", RegexOption.IGNORE_CASE)
        if (movieKeywordsRegex.containsMatchIn(titleClean) || (totalEpisodes == 1L && movieKeywordsRegex.containsMatchIn(combinedText))) {
            return MediaType.MOVIES
        }

        // CAPA 4: Expresiones regulares de Series Live Action / Doramas
        val seriesKeywordsRegex = Regex("""\b(dorama|kdrama|jdrama|live action|live-action|tv show|tv series|serie|temporada|season)\b""", RegexOption.IGNORE_CASE)
        if (seriesKeywordsRegex.containsMatchIn(combinedText)) {
            return MediaType.SERIES
        }

        // CAPA 5: Si no es Película ni Serie Live-Action, se clasifica como ANIME
        return MediaType.ANIME
    }

    private fun formatTime(milliseconds: Long): String {
        if (milliseconds <= 0L) return "0:00"
        return if (milliseconds > 3600000L) {
            String.format(
                "%d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(milliseconds),
                TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                    TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
                TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
            )
        } else {
            String.format(
                "%d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(milliseconds),
                TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
            )
        }
    }
}
