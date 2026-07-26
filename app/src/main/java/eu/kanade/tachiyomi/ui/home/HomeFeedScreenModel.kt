package eu.kanade.tachiyomi.ui.home

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
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
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.source.local.entries.anime.isLocal
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

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
        mutableState.update { current ->
            when (key) {
                "featured" -> current.copy(showFeatured = !current.showFeatured)
                "continue" -> current.copy(showContinue = !current.showContinue)
                "because_you_watched" -> current.copy(showBecauseYouWatched = !current.showBecauseYouWatched)
                "recommended" -> current.copy(showRecommended = !current.showRecommended)
                "popular_anime" -> current.copy(showPopularAnime = !current.showPopularAnime)
                "popular_manga" -> current.copy(showPopularManga = !current.showPopularManga)
                else -> current
            }
        }
    }

    private fun fetchRemoteTrendsAsync() {
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val remoteItems = mutableListOf<HomeItemData>()

                // 1. Tendencias de AniList (Anime)
                if (trackerManager.aniList.isLoggedIn) {
                    try {
                        val popularAnime = trackerManager.aniList.getPopularAnime()
                        remoteItems += popularAnime.map { track ->
                            val classified = classifyMedia(track.title, null, track.summary)
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
        combine(
            getAnimeHistory.subscribe("").onStart { emit(emptyList()) },
            getMangaHistory.subscribe("").onStart { emit(emptyList()) },
            getLibraryAnime.subscribe().onStart { emit(emptyList()) },
            getLibraryManga.subscribe().onStart { emit(emptyList()) },
        ) { animeHistories, mangaHistories, libraryAnimeList, libraryMangaList ->
            val pinnedAnimeSources = sourcePreferences.pinnedAnimeSources.get()
            val pinnedMangaSources = sourcePreferences.pinnedMangaSources.get()
            val hasLoggedInTrackers = trackerManager.loggedInTrackers().isNotEmpty()

            val animeMap = libraryAnimeList.associateBy { it.anime.id }

            // 1. Tarjetas de Continuar viendo (Anime, Series y Películas)
            val continueAnime = animeHistories.map { relation ->
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
                val classifiedType = if (libAnime != null) {
                    classifyMedia(libAnime.title, libAnime.genre, libAnime.description)
                } else {
                    classifyMedia(relation.title, null, null)
                }

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

            // 2. Tarjetas de Continuar leyendo (Manga)
            val continueManga = mangaHistories.map { relation ->
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

            // Unificar e intercalar por fecha de actualización más reciente (Anime/Películas/Series + Manga)
            val unifiedContinue = (continueAnime + continueManga)
                .sortedByDescending { it.lastUpdatedTimestamp }
                .take(20)

            // 3. Listas de Biblioteca (Online vs Local)
            val nonLocalAnime = libraryAnimeList.filterNot { it.anime.isLocal() }
            val nonLocalManga = libraryMangaList.filterNot { it.manga.isLocal() }

            // Priorizar ítems pertenecientes a las extensiones ancladas del usuario
            val pinnedAnimeList = nonLocalAnime.filter { "${it.anime.source}" in pinnedAnimeSources }
            val sourceAnimeList = if (pinnedAnimeList.isNotEmpty()) pinnedAnimeList else nonLocalAnime

            val pinnedMangaList = nonLocalManga.filter { "${it.manga.source}" in pinnedMangaSources }
            val sourceMangaList = if (pinnedMangaList.isNotEmpty()) pinnedMangaList else nonLocalManga

            val animeItems = sourceAnimeList.map { lib ->
                val anime = lib.anime
                val mediaType = classifyMedia(anime.title, anime.genre, anime.description)
                
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
                            item.genres.split(",").any { g -> g.trim().lowercase() in targetGenres }
                    }
                    .distinctBy { it.id }
                    .take(10)
            } else {
                emptyList()
            }

            val unifiedRecommended = (animeItems + mangaItems).shuffled()
            val videoItems = animeItems.filter { it.mediaType != MediaType.MANGA }
            val fallbackCarousel = if (videoItems.isNotEmpty()) {
                videoItems.distinctBy { it.id }.take(7)
            } else {
                animeItems.distinctBy { it.id }.take(7)
            }
            val currentHeroList = mutableState.value.heroList.ifEmpty { fallbackCarousel }

            State(
                isLoading = false,
                isRefreshing = false,
                heroList = currentHeroList,
                continueList = unifiedContinue,
                becauseYouWatchedTitle = lastInteractedItem?.title,
                becauseYouWatchedIsAnime = lastInteractedItem?.isAnime ?: true,
                becauseYouWatchedList = becauseYouWatchedList,
                recommendedList = unifiedRecommended.take(12),
                animeList = animeItems.take(12),
                mangaList = mangaItems.take(12),
            )
        }
            .catch { logcat(LogPriority.ERROR, it) }
            .onEach { newState -> mutableState.value = newState }
            .launchIn(screenModelScope)
    }

    private fun classifyMedia(title: String, genre: List<String>?, description: String?): MediaType {
        val genreText = genre?.joinToString(" ")?.lowercase() ?: ""
        val titleText = title.lowercase()
        val descText = description?.lowercase() ?: ""
        val combined = "$genreText $titleText $descText"

        return when {
            combined.contains("movie") || combined.contains("película") || combined.contains("pelicula") || combined.contains("film") || combined.contains("cine") -> MediaType.MOVIES
            combined.contains("series") || combined.contains("serie") || combined.contains("live action") || combined.contains("tv show") || combined.contains("dorama") || combined.contains("kdrama") -> MediaType.SERIES
            else -> MediaType.ANIME
        }
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
