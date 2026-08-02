package eu.kanade.tachiyomi.ui.home

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.home.HomeItemData
import eu.kanade.presentation.home.MediaType
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
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
    ALL,
    VIDEO_ONLY,
    MANGA_ONLY,
}

enum class HeroSource {
    BOTH,
    LIBRARY_ONLY,
    TRACKERS_ONLY,
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
    private val trackPreferences: TrackPreferences = Injekt.get(),
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
        val movieList: List<HomeItemData> = emptyList(),
        val seriesList: List<HomeItemData> = emptyList(),
        val showFeatured: Boolean = true,
        val showContinue: Boolean = true,
        val showBecauseYouWatched: Boolean = true,
        val showRecommended: Boolean = true,
        val showPopularAnime: Boolean = true,
        val showPopularManga: Boolean = true,
        val showPopularMovies: Boolean = true,
        val showPopularSeries: Boolean = true,
        val mediaFilter: HomeMediaFilter = HomeMediaFilter.ALL,
        val autoScrollHero: Boolean = true,
        val heroSource: HeroSource = HeroSource.BOTH,
        val itemsPerSection: Int = 12,
        val hideCompletedInRecommended: Boolean = false,
        val enableTmdb: Boolean = true,
        val enableAnilist: Boolean = true,
    )

    // In-memory caches updated by individual observers
    private var cachedLibraryAnime: List<LibraryAnime> = emptyList()
    private var cachedLibraryManga: List<LibraryManga> = emptyList()
    private var cachedAnimeHistory: List<AnimeHistoryWithRelations> = emptyList()
    private var cachedMangaHistory: List<MangaHistoryWithRelations> = emptyList()
    private var cachedRemoteAnime: List<HomeItemData> = emptyList()
    private var cachedRemoteManga: List<HomeItemData> = emptyList()
    private var cachedRemoteMovieSeries: List<HomeItemData> = emptyList()

    private val remoteAnimeState = MutableStateFlow<List<HomeItemData>>(emptyList())
    private val remoteMangaState = MutableStateFlow<List<HomeItemData>>(emptyList())
    private val remoteMovieSeriesState = MutableStateFlow<List<HomeItemData>>(emptyList())

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

            "because_you_watched" -> uiPreferences.homeShowBecauseYouWatched.set(
                !uiPreferences.homeShowBecauseYouWatched.get(),
            )

            "recommended" -> uiPreferences.homeShowRecommended.set(!uiPreferences.homeShowRecommended.get())

            "popular_anime" -> uiPreferences.homeShowPopularAnime.set(!uiPreferences.homeShowPopularAnime.get())

            "popular_manga" -> uiPreferences.homeShowPopularManga.set(!uiPreferences.homeShowPopularManga.get())

            "popular_movies" -> uiPreferences.homeShowPopularMovies.set(!uiPreferences.homeShowPopularMovies.get())

            "popular_series" -> uiPreferences.homeShowPopularSeries.set(!uiPreferences.homeShowPopularSeries.get())
        }
        updateStateWithData()
    }

    fun setMediaFilter(filter: HomeMediaFilter) {
        uiPreferences.homeMediaFilter.set(filter.ordinal)
        updateStateWithData()
    }

    fun toggleAutoScrollHero() {
        uiPreferences.homeAutoScrollHero.set(!uiPreferences.homeAutoScrollHero.get())
        updateStateWithData()
    }

    fun setHeroSource(source: HeroSource) {
        uiPreferences.homeHeroSource.set(source.ordinal)
        updateStateWithData()
        fetchRemoteTrendsAsync()
    }

    fun setItemsPerSection(count: Int) {
        uiPreferences.homeItemsPerSection.set(count)
        updateStateWithData()
    }

    fun toggleHideCompletedInRecommended() {
        uiPreferences.homeHideCompleted.set(!uiPreferences.homeHideCompleted.get())
        updateStateWithData()
    }

    fun toggleEnableTmdb() {
        uiPreferences.homeEnableTmdb.set(!uiPreferences.homeEnableTmdb.get())
        updateStateWithData()
        fetchRemoteTrendsAsync()
    }

    fun toggleEnableAnilist() {
        uiPreferences.homeEnableAnilist.set(!uiPreferences.homeEnableAnilist.get())
        updateStateWithData()
        fetchRemoteTrendsAsync()
    }

    private fun fetchRemoteTrendsAsync() {
        // 1. Tendencias de AniList (Anime)
        screenModelScope.launch(Dispatchers.IO) {
            try {
                if (!uiPreferences.homeEnableAnilist.get()) {
                    remoteAnimeState.value = emptyList()
                    return@launch
                }
                android.util.Log.d("HomeFeedDebug", "Fetching AniList popular anime...")
                val popularAnime = trackerManager.aniList.getPopularAnime()
                android.util.Log.d("HomeFeedDebug", "AniList popular anime returned ${popularAnime.size} items")
                val animeItems = popularAnime.map { track ->
                    val classified = classifyMedia(
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
                        coverData = track.cover_url,
                        mediaType = classified,
                        rating = if (track.score > 0) String.format("%.1f", track.score / 10.0) else "",
                        synopsis = track.summary,
                    )
                }
                remoteAnimeState.value = animeItems
                android.util.Log.d("HomeFeedDebug", "remoteAnimeState updated with ${animeItems.size} items")
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Failed to fetch AniList popular anime", e)
            }
        }

        // 2. Tendencias de AniList (Manga)
        screenModelScope.launch(Dispatchers.IO) {
            try {
                if (!uiPreferences.homeEnableAnilist.get()) {
                    remoteMangaState.value = emptyList()
                    return@launch
                }
                android.util.Log.d("HomeFeedDebug", "Fetching AniList popular manga...")
                val popularManga = trackerManager.aniList.getPopularManga()
                android.util.Log.d("HomeFeedDebug", "AniList popular manga returned ${popularManga.size} items")
                val mangaItems = popularManga.map { track ->
                    HomeItemData(
                        id = track.remote_id,
                        isAnime = false,
                        inLibrary = false,
                        title = track.title,
                        subtitle = "Manga",
                        coverUrl = track.cover_url,
                        coverData = track.cover_url,
                        mediaType = MediaType.MANGA,
                        rating = if (track.score > 0) String.format("%.1f", track.score / 10.0) else "",
                        synopsis = track.summary,
                    )
                }
                remoteMangaState.value = mangaItems
                android.util.Log.d("HomeFeedDebug", "remoteMangaState updated with ${mangaItems.size} items")
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Failed to fetch AniList popular manga", e)
            }
        }

        // 3. Tendencias de TMDB (Películas y Series)
        screenModelScope.launch(Dispatchers.IO) {
            try {
                if (!uiPreferences.homeEnableTmdb.get()) {
                    remoteMovieSeriesState.value = emptyList()
                    return@launch
                }
                val tmdbAvailable = trackerManager.tmdb.isAvailableForUse()
                val tmdbLoggedIn = trackerManager.tmdb.isLoggedIn
                android.util.Log.d(
                    "HomeFeedDebug",
                    "TMDB check: isAvailableForUse=$tmdbAvailable, isLoggedIn=$tmdbLoggedIn",
                )
                if (tmdbLoggedIn || tmdbAvailable) {
                    val movies = try {
                        trackerManager.tmdb.getTrendingMovies().also {
                            android.util.Log.d("HomeFeedDebug", "TMDB getTrendingMovies returned ${it.size} items")
                        }.map { track ->
                            HomeItemData(
                                id = track.remote_id,
                                isAnime = true,
                                inLibrary = false,
                                title = track.title,
                                subtitle = "Película",
                                coverUrl = track.cover_url,
                                coverData = track.cover_url,
                                mediaType = MediaType.MOVIES,
                                rating = if (track.score > 0) String.format("%.1f", track.score) else "",
                                synopsis = track.summary,
                                genres = "Película, Cine, Movie",
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HomeFeedDebug", "Failed to fetch TMDB movies", e)
                        emptyList()
                    }

                    val series = try {
                        trackerManager.tmdb.getTrendingTv().also {
                            android.util.Log.d("HomeFeedDebug", "TMDB getTrendingTv returned ${it.size} items")
                        }.map { track ->
                            HomeItemData(
                                id = track.remote_id,
                                isAnime = true,
                                inLibrary = false,
                                title = track.title,
                                subtitle = "Serie",
                                coverUrl = track.cover_url,
                                coverData = track.cover_url,
                                mediaType = MediaType.SERIES,
                                rating = if (track.score > 0) String.format("%.1f", track.score) else "",
                                synopsis = track.summary,
                                genres = "Serie, TV, Show",
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HomeFeedDebug", "Failed to fetch TMDB series", e)
                        emptyList()
                    }

                    remoteMovieSeriesState.value = movies + series
                    android.util.Log.d(
                        "HomeFeedDebug",
                        "remoteMovieSeriesState updated with ${movies.size + series.size} items",
                    )
                } else {
                    android.util.Log.w("HomeFeedDebug", "TMDB skipped: not logged in and not available for use")
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Critical error in TMDB fetch coroutine", e)
            }
        }
    }

    private fun observeHomeData() {
        android.util.Log.d("HomeFeedDebug", "observeHomeData: initializing observers...")
        // Set loading false immediately — UI is always interactive
        mutableState.update { it.copy(isLoading = false) }

        // Observe uiPreferences changes so UI state updates instantly in real time
        screenModelScope.launch {
            try {
                merge(
                    uiPreferences.homeShowFeatured.changes(),
                    uiPreferences.homeShowContinue.changes(),
                    uiPreferences.homeShowBecauseYouWatched.changes(),
                    uiPreferences.homeShowRecommended.changes(),
                    uiPreferences.homeShowPopularAnime.changes(),
                    uiPreferences.homeShowPopularManga.changes(),
                    uiPreferences.homeShowPopularMovies.changes(),
                    uiPreferences.homeShowPopularSeries.changes(),
                    uiPreferences.homeMediaFilter.changes(),
                    uiPreferences.homeAutoScrollHero.changes(),
                    uiPreferences.homeHeroSource.changes(),
                    uiPreferences.homeItemsPerSection.changes(),
                    uiPreferences.homeHideCompleted.changes(),
                    uiPreferences.homeEnableTmdb.changes(),
                    uiPreferences.homeEnableAnilist.changes(),
                )
                    .catch { e -> android.util.Log.e("HomeFeedDebug", "Error observing uiPreferences", e) }
                    .collect {
                        android.util.Log.d("HomeFeedDebug", "uiPreferences changed, updating state")
                        updateStateWithData()
                    }
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Fatal error in uiPreferences observer", e)
            }
        }

        // Observe TMDB API key changes to auto-refetch trends when key is configured
        screenModelScope.launch {
            try {
                trackPreferences.trackApiKey(trackerManager.tmdb as eu.kanade.tachiyomi.data.track.Tracker).changes()
                    .catch { e -> android.util.Log.e("HomeFeedDebug", "Error observing TMDB API key", e) }
                    .collect {
                        android.util.Log.d("HomeFeedDebug", "TMDB key changed, refetching trends")
                        fetchRemoteTrendsAsync()
                    }
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Fatal error observing TMDB API key", e)
            }
        }

        // Observe library anime
        screenModelScope.launch {
            try {
                getLibraryAnime.subscribe()
                    .catch { e -> android.util.Log.e("HomeFeedDebug", "Error observing library anime", e) }
                    .collect { libraryAnimeList ->
                        android.util.Log.d("HomeFeedDebug", "Library anime collected: size=${libraryAnimeList.size}")
                        updateStateWithData(libraryAnime = libraryAnimeList)
                    }
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Fatal error in library anime observer", e)
            }
        }

        // Observe library manga
        screenModelScope.launch {
            try {
                getLibraryManga.subscribe()
                    .catch { e -> android.util.Log.e("HomeFeedDebug", "Error observing library manga", e) }
                    .collect { libraryMangaList ->
                        android.util.Log.d("HomeFeedDebug", "Library manga collected: size=${libraryMangaList.size}")
                        updateStateWithData(libraryManga = libraryMangaList)
                    }
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Fatal error in library manga observer", e)
            }
        }

        // Observe anime history
        screenModelScope.launch {
            try {
                getAnimeHistory.subscribe("")
                    .catch { e -> android.util.Log.e("HomeFeedDebug", "Error observing anime history", e) }
                    .collect { histories ->
                        android.util.Log.d("HomeFeedDebug", "Anime history collected: size=${histories.size}")
                        updateStateWithData(animeHistory = histories)
                    }
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Fatal error in anime history observer", e)
            }
        }

        // Observe manga history
        screenModelScope.launch {
            try {
                getMangaHistory.subscribe("")
                    .catch { e -> android.util.Log.e("HomeFeedDebug", "Error observing manga history", e) }
                    .collect { histories ->
                        android.util.Log.d("HomeFeedDebug", "Manga history collected: size=${histories.size}")
                        updateStateWithData(mangaHistory = histories)
                    }
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Fatal error in manga history observer", e)
            }
        }

        // Observe remote anime
        screenModelScope.launch {
            try {
                remoteAnimeState.collect { items ->
                    android.util.Log.d("HomeFeedDebug", "remoteAnimeState collected: size=${items.size}")
                    updateStateWithData(remoteAnime = items)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Error observing remote anime", e)
            }
        }

        // Observe remote manga
        screenModelScope.launch {
            try {
                remoteMangaState.collect { items ->
                    android.util.Log.d("HomeFeedDebug", "remoteMangaState collected: size=${items.size}")
                    updateStateWithData(remoteManga = items)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Error observing remote manga", e)
            }
        }

        // Observe remote movies and series
        screenModelScope.launch {
            try {
                remoteMovieSeriesState.collect { items ->
                    android.util.Log.d("HomeFeedDebug", "remoteMovieSeriesState collected: size=${items.size}")
                    updateStateWithData(remoteMovieSeries = items)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFeedDebug", "Error observing remote movie series", e)
            }
        }
    }

    @Synchronized
    private fun updateStateWithData(
        libraryAnime: List<LibraryAnime>? = null,
        libraryManga: List<LibraryManga>? = null,
        animeHistory: List<AnimeHistoryWithRelations>? = null,
        mangaHistory: List<MangaHistoryWithRelations>? = null,
        remoteAnime: List<HomeItemData>? = null,
        remoteManga: List<HomeItemData>? = null,
        remoteMovieSeries: List<HomeItemData>? = null,
    ) {
        // Update caches with new data
        if (libraryAnime != null) cachedLibraryAnime = libraryAnime
        if (libraryManga != null) cachedLibraryManga = libraryManga
        if (animeHistory != null) cachedAnimeHistory = animeHistory
        if (mangaHistory != null) cachedMangaHistory = mangaHistory
        if (remoteAnime != null) cachedRemoteAnime = remoteAnime
        if (remoteManga != null) cachedRemoteManga = remoteManga
        if (remoteMovieSeries != null) cachedRemoteMovieSeries = remoteMovieSeries

        try {
            val libraryAnimeList = cachedLibraryAnime
            val libraryMangaList = cachedLibraryManga
            val animeHistories = cachedAnimeHistory
            val mangaHistories = cachedMangaHistory
            val rawRemoteAnime = cachedRemoteAnime
            val rawRemoteManga = cachedRemoteManga
            val movieSeriesList = cachedRemoteMovieSeries

            // Read preferences synchronously
            val filter = HomeMediaFilter.entries.getOrElse(uiPreferences.homeMediaFilter.get()) { HomeMediaFilter.ALL }
            val heroSource = HeroSource.entries.getOrElse(uiPreferences.homeHeroSource.get()) { HeroSource.BOTH }
            val limit = uiPreferences.homeItemsPerSection.get()
            val hideCompleted = uiPreferences.homeHideCompleted.get()
            val autoScrollHero = uiPreferences.homeAutoScrollHero.get()

            val showFeatured = uiPreferences.homeShowFeatured.get()
            val showContinue = uiPreferences.homeShowContinue.get()
            val showBecauseYouWatched = uiPreferences.homeShowBecauseYouWatched.get()
            val showRecommended = uiPreferences.homeShowRecommended.get()
            val showPopularAnime = uiPreferences.homeShowPopularAnime.get()
            val showPopularManga = uiPreferences.homeShowPopularManga.get()
            val showPopularMovies = uiPreferences.homeShowPopularMovies.get()
            val showPopularSeries = uiPreferences.homeShowPopularSeries.get()

            val enableTmdb = uiPreferences.homeEnableTmdb.get()
            val enableAnilist = uiPreferences.homeEnableAnilist.get()

            val pinnedAnimeSources = sourcePreferences.pinnedAnimeSources.get()
            val pinnedMangaSources = sourcePreferences.pinnedMangaSources.get()

            val animeMap = libraryAnimeList.associateBy { it.anime.id }

            logcat(LogPriority.DEBUG) {
                "HomeFeed update: remoteAnime=${rawRemoteAnime.size}, remoteManga=${rawRemoteManga.size}, " +
                    "remoteMovies=${movieSeriesList.size}, animeHistory=${animeHistories.size}, " +
                    "mangaHistory=${mangaHistories.size}, libraryAnime=${libraryAnimeList.size}, " +
                    "libraryManga=${libraryMangaList.size}"
            }

            // 1. Continue Watching (Anime)
            val continueAnime = if (filter != HomeMediaFilter.MANGA_ONLY) {
                animeHistories.map { relation ->
                    val epNum = if (relation.episodeNumber % 1.0 == 0.0) {
                        relation.episodeNumber.toInt().toString()
                    } else {
                        relation.episodeNumber.toString()
                    }

                    val libAnime = animeMap[relation.animeId]?.anime
                    val realSourceName = libAnime?.source?.let { sourceManager.getOrStub(it).name } ?: ""

                    val classifiedType = classifyMedia(
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
                        progress = 0.5f,
                        remainingInfo = "Ep. $epNum",
                        synopsis = libAnime?.description ?: relation.title,
                        genres = libAnime?.genre?.joinToString(", ") ?: "",
                        lastUpdatedTimestamp = relation.seenAt?.time ?: 0L,
                    )
                }
            } else {
                emptyList()
            }

            // 2. Continue Reading (Manga)
            val continueManga = if (filter != HomeMediaFilter.VIDEO_ONLY) {
                mangaHistories.map { relation ->
                    val chNum = if (relation.chapterNumber % 1.0 == 0.0) {
                        relation.chapterNumber.toInt().toString()
                    } else {
                        relation.chapterNumber.toString()
                    }

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
                        remainingInfo = "Cap. $chNum",
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

            // 3. Library lists
            val nonLocalAnime = if (filter != HomeMediaFilter.MANGA_ONLY) {
                libraryAnimeList.filterNot { it.anime.isLocal() }
            } else {
                emptyList()
            }
            val nonLocalManga = if (filter != HomeMediaFilter.VIDEO_ONLY) {
                libraryMangaList.filterNot { it.manga.isLocal() }
            } else {
                emptyList()
            }

            val pinnedAnimeList = nonLocalAnime.filter { "${it.anime.source}" in pinnedAnimeSources }
            val sourceAnimeList = if (pinnedAnimeList.isNotEmpty()) pinnedAnimeList else nonLocalAnime

            val pinnedMangaList = nonLocalManga.filter { "${it.manga.source}" in pinnedMangaSources }
            val sourceMangaList = if (pinnedMangaList.isNotEmpty()) pinnedMangaList else nonLocalManga

            val animeItems = sourceAnimeList.map { lib ->
                val anime = lib.anime
                val realSourceName = sourceManager.getOrStub(anime.source).name

                val mediaType = classifyMedia(
                    title = anime.title,
                    genre = anime.genre,
                    description = anime.description,
                    sourceName = realSourceName,
                )

                HomeItemData(
                    id = anime.id,
                    isAnime = true,
                    inLibrary = true,
                    title = anime.title,
                    subtitle = anime.genre?.firstOrNull() ?: mediaType.name,
                    coverData = anime.asAnimeCover(),
                    mediaType = mediaType,
                    rating = "",
                    synopsis = anime.description ?: anime.title,
                    genres = anime.genre?.joinToString(", ") ?: "",
                )
            }

            val mangaItems = sourceMangaList.map { lib ->
                val manga = lib.manga
                HomeItemData(
                    id = manga.id,
                    isAnime = false,
                    inLibrary = true,
                    title = manga.title,
                    subtitle = manga.genre?.firstOrNull() ?: "Manga",
                    coverData = manga.asMangaCover(),
                    mediaType = MediaType.MANGA,
                    rating = "",
                    synopsis = manga.description ?: manga.title,
                    genres = manga.genre?.joinToString(", ") ?: "",
                )
            }

            // Cross-reference remote items with local library
            val animeTitleMap = libraryAnimeList.associateBy { it.anime.title.lowercase().trim() }
            val processedRemoteAnime = rawRemoteAnime.map { item ->
                val match = animeTitleMap[item.title.lowercase().trim()]
                if (match != null) {
                    item.copy(
                        id = match.anime.id,
                        inLibrary = true,
                        coverData = match.anime.asAnimeCover(),
                        genres = match.anime.genre?.joinToString(", ") ?: item.genres,
                    )
                } else {
                    item
                }
            }

            val mangaTitleMap = libraryMangaList.associateBy { it.manga.title.lowercase().trim() }
            val processedRemoteManga = rawRemoteManga.map { item ->
                val match = mangaTitleMap[item.title.lowercase().trim()]
                if (match != null) {
                    item.copy(
                        id = match.manga.id,
                        inLibrary = true,
                        coverData = match.manga.asMangaCover(),
                        genres = match.manga.genre?.joinToString(", ") ?: item.genres,
                    )
                } else {
                    item
                }
            }

            val finalPopularAnime = when (heroSource) {
                HeroSource.LIBRARY_ONLY -> animeItems.filter { it.mediaType == MediaType.ANIME }

                HeroSource.TRACKERS_ONLY -> processedRemoteAnime

                HeroSource.BOTH -> processedRemoteAnime.ifEmpty {
                    animeItems.filter { it.mediaType == MediaType.ANIME }
                }
            }

            val finalPopularManga = when (heroSource) {
                HeroSource.LIBRARY_ONLY -> mangaItems
                HeroSource.TRACKERS_ONLY -> processedRemoteManga
                HeroSource.BOTH -> processedRemoteManga.ifEmpty { mangaItems }
            }

            val movieItems = when (heroSource) {
                HeroSource.LIBRARY_ONLY -> animeItems.filter { it.mediaType == MediaType.MOVIES }

                HeroSource.TRACKERS_ONLY -> movieSeriesList.filter { it.mediaType == MediaType.MOVIES }

                HeroSource.BOTH -> movieSeriesList.filter { it.mediaType == MediaType.MOVIES }.ifEmpty {
                    animeItems.filter {
                        it.mediaType ==
                            MediaType.MOVIES
                    }
                }
            }

            val seriesItems = when (heroSource) {
                HeroSource.LIBRARY_ONLY -> animeItems.filter { it.mediaType == MediaType.SERIES }

                HeroSource.TRACKERS_ONLY -> movieSeriesList.filter { it.mediaType == MediaType.SERIES }

                HeroSource.BOTH -> movieSeriesList.filter { it.mediaType == MediaType.SERIES }.ifEmpty {
                    animeItems.filter {
                        it.mediaType ==
                            MediaType.SERIES
                    }
                }
            }

            val displayPopularAnime = if (filter != HomeMediaFilter.MANGA_ONLY) finalPopularAnime else emptyList()
            val displayPopularManga = if (filter != HomeMediaFilter.VIDEO_ONLY) finalPopularManga else emptyList()
            val displayPopularMovies = if (filter != HomeMediaFilter.MANGA_ONLY) movieItems else emptyList()
            val displayPopularSeries = if (filter != HomeMediaFilter.MANGA_ONLY) seriesItems else emptyList()

            // 4. "Because you watched"
            val lastInteractedItem = unifiedContinue.firstOrNull()
            val targetGenres =
                lastInteractedItem?.genres?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotBlank() }
                    ?: emptyList()

            val candidatePool = when (heroSource) {
                HeroSource.LIBRARY_ONLY -> (animeItems + mangaItems)

                HeroSource.TRACKERS_ONLY -> (processedRemoteAnime + processedRemoteManga + movieSeriesList)

                HeroSource.BOTH -> (
                    processedRemoteAnime + processedRemoteManga + movieSeriesList + animeItems +
                        mangaItems
                    )
            }
                .distinctBy { "${it.mediaType.name}_${it.id}_${it.title.lowercase().trim()}" }

            val becauseYouWatchedList = if (lastInteractedItem != null && targetGenres.isNotEmpty()) {
                candidatePool
                    .filter { item ->
                        item.id != lastInteractedItem.id &&
                            item.title.lowercase().trim() != lastInteractedItem.title.lowercase().trim() &&
                            item.genres.split(",").any { g -> g.trim().lowercase() in targetGenres } &&
                            (!hideCompleted || item.progress < 1.0f)
                    }
            } else {
                emptyList()
            }

            val filteredRecs = candidatePool
                .filter { !hideCompleted || it.progress < 1.0f }
            val unifiedRecommended = filteredRecs.shuffled()

            val displayBecauseYouWatched = when (filter) {
                HomeMediaFilter.VIDEO_ONLY -> becauseYouWatchedList.filter { it.mediaType != MediaType.MANGA }
                HomeMediaFilter.MANGA_ONLY -> becauseYouWatchedList.filter { it.mediaType == MediaType.MANGA }
                HomeMediaFilter.ALL -> becauseYouWatchedList
            }

            val displayRecommended = when (filter) {
                HomeMediaFilter.VIDEO_ONLY -> unifiedRecommended.filter { it.mediaType != MediaType.MANGA }
                HomeMediaFilter.MANGA_ONLY -> unifiedRecommended.filter { it.mediaType == MediaType.MANGA }
                HomeMediaFilter.ALL -> unifiedRecommended
            }

            val allRemoteHeroItems = if (movieSeriesList.isNotEmpty()) {
                (
                    movieSeriesList.shuffled().take(
                        4,
                    ) + (processedRemoteAnime + processedRemoteManga).shuffled().take(3)
                    ).shuffled()
            } else {
                (processedRemoteAnime + processedRemoteManga).shuffled()
            }
            val videoItems = animeItems.filter { it.mediaType != MediaType.MANGA }
            val fallbackCarousel = if (videoItems.isNotEmpty()) {
                videoItems.distinctBy { it.id }.take(7)
            } else {
                animeItems.distinctBy { it.id }.take(7)
            }

            val currentHeroList = when (heroSource) {
                HeroSource.LIBRARY_ONLY -> fallbackCarousel
                HeroSource.TRACKERS_ONLY -> allRemoteHeroItems
                HeroSource.BOTH -> allRemoteHeroItems.ifEmpty { fallbackCarousel }
            }

            android.util.Log.d(
                "HomeFeedDebug",
                "HomeFeed State build: heroList=${currentHeroList.size}, " +
                    "allRemoteHero=${allRemoteHeroItems.size}, fallback=${fallbackCarousel.size}, " +
                    "remoteAnime=${processedRemoteAnime.size}, remoteManga=${processedRemoteManga.size}, " +
                    "movies=${movieItems.size}, series=${seriesItems.size}, " +
                    "heroSource=$heroSource",
            )

            mutableState.value = State(
                isLoading = false,
                isRefreshing = false,
                heroList = currentHeroList,
                continueList = unifiedContinue,
                becauseYouWatchedTitle = lastInteractedItem?.title,
                becauseYouWatchedIsAnime = lastInteractedItem?.isAnime ?: true,
                becauseYouWatchedList = displayBecauseYouWatched,
                recommendedList = displayRecommended,
                animeList = displayPopularAnime.take(limit),
                mangaList = displayPopularManga.take(limit),
                movieList = displayPopularMovies.take(limit),
                seriesList = displayPopularSeries.take(limit),
                showFeatured = showFeatured,
                showContinue = showContinue,
                showBecauseYouWatched = showBecauseYouWatched,
                showRecommended = showRecommended,
                showPopularAnime = showPopularAnime,
                showPopularManga = showPopularManga,
                showPopularMovies = showPopularMovies,
                showPopularSeries = showPopularSeries,
                mediaFilter = filter,
                autoScrollHero = autoScrollHero,
                heroSource = heroSource,
                itemsPerSection = limit,
                hideCompletedInRecommended = hideCompleted,
                enableTmdb = enableTmdb,
                enableAnilist = enableAnilist,
            )
        } catch (e: Exception) {
            android.util.Log.e("HomeFeedDebug", "Error building home feed state", e)
        }
    }

    private fun classifyMedia(
        title: String,
        genre: List<String>? = null,
        description: String? = null,
        sourceName: String? = null,
    ): MediaType {
        val titleClean = title.lowercase()
        val genreClean = genre?.joinToString(" ")?.lowercase() ?: ""
        val descClean = description?.lowercase() ?: ""
        val sourceClean = sourceName?.lowercase() ?: ""

        return when {
            sourceClean.contains("tmdb") || sourceClean.contains("cuevana") ||
                sourceClean.contains("pelis") || sourceClean.contains("cine") ||
                sourceClean.contains("movie") || sourceClean.contains("dorama") ||
                sourceClean.contains("kdrama") ||
                titleClean.contains("película") || titleClean.contains("movie") ||
                titleClean.contains("film") || genreClean.contains("película") ||
                genreClean.contains("movie") || genreClean.contains("cine") -> {
                if (titleClean.contains("película") || titleClean.contains("movie") ||
                    titleClean.contains("film") || genreClean.contains("película") ||
                    genreClean.contains("movie")
                ) {
                    MediaType.MOVIES
                } else {
                    MediaType.SERIES
                }
            }

            titleClean.contains("serie") || sourceClean.contains("series") ||
                sourceClean.contains("tv") || genreClean.contains("dorama") ||
                genreClean.contains("drama") -> MediaType.SERIES

            else -> MediaType.ANIME
        }
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
                val tmdbTrack = tracks.firstOrNull {
                    trackerManager.get(it.trackerId) is eu.kanade.tachiyomi.data.track.tmdb.Tmdb
                }
                if (tmdbTrack != null) {
                    if (titleClean.contains(
                            "película",
                        ) || titleClean.contains("movie") || titleClean.contains("film") ||
                        genreClean.contains("película") ||
                        genreClean.contains("movie")
                    ) {
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
                    return if (titleClean.contains("película") || titleClean.contains("movie") ||
                        titleClean.contains("film") ||
                        totalEpisodes == 1L
                    ) {
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
        val movieKeywordsRegex =
            Regex("""\b(movie|película|pelicula|film|gekijouban|劇場版|the movie|eiga)\b""", RegexOption.IGNORE_CASE)
        if (movieKeywordsRegex.containsMatchIn(titleClean) ||
            (totalEpisodes == 1L && movieKeywordsRegex.containsMatchIn(combinedText))
        ) {
            return MediaType.MOVIES
        }

        // CAPA 4: Expresiones regulares de Series Live Action / Doramas
        val seriesKeywordsRegex =
            Regex(
                """\b(dorama|kdrama|jdrama|live action|live-action|tv show|tv series|serie|temporada|season)\b""",
                RegexOption.IGNORE_CASE,
            )
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
