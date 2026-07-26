package eu.kanade.tachiyomi.ui.home

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.home.HomeItemData
import eu.kanade.presentation.home.MediaType
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
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
) : StateScreenModel<HomeFeedScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val heroList: List<HomeItemData> = emptyList(),
        val continueList: List<HomeItemData> = emptyList(),
        val recommendedList: List<HomeItemData> = emptyList(),
        val animeList: List<HomeItemData> = emptyList(),
        val mangaList: List<HomeItemData> = emptyList(),
    )

    init {
        observeHomeData()
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

                // Buscar el objeto Anime para obtener géneros y sinopsis para clasificar si es Película, Serie o Anime
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
                    episodeId = relation.episodeId,
                    title = relation.title,
                    subtitle = subtitleText,
                    coverData = relation.coverData,
                    mediaType = classifiedType,
                    progress = progressRatio,
                    remainingInfo = timeFormatted,
                    synopsis = libAnime?.description ?: relation.title,
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
                    chapterId = relation.chapterId,
                    title = relation.title,
                    subtitle = "Capítulo $chNum",
                    coverData = relation.coverData,
                    mediaType = MediaType.MANGA,
                    progress = 0.8f,
                    remainingInfo = pageFormatted,
                    synopsis = relation.title,
                )
            }

            // Unificar lista de Continuar viendo (Anime/Películas/Series) y leyendo (Manga)
            val unifiedContinue = (continueAnime + continueManga).take(15)

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
                
                // Obtener rating real de tracking si existe (MAL, AniList, etc.), sin inventar notas
                val tracks = getAnimeTracks.await(anime.id)
                val realScore = tracks.firstOrNull { it.score > 0 }?.score ?: 0.0
                val ratingStr = if (realScore > 0) String.format("%.1f", realScore) else ""

                HomeItemData(
                    id = anime.id,
                    isAnime = true,
                    title = anime.title,
                    subtitle = anime.genre?.firstOrNull() ?: mediaType.name,
                    coverData = anime.asAnimeCover(),
                    mediaType = mediaType,
                    rating = ratingStr,
                    synopsis = anime.description ?: anime.title,
                )
            }

            val mangaItems = sourceMangaList.map { lib ->
                val manga = lib.manga
                val tracks = getMangaTracks.await(manga.id)
                val realScore = tracks.firstOrNull { it.score > 0 }?.score ?: 0.0
                val ratingStr = if (realScore > 0) String.format("%.1f", realScore) else ""

                HomeItemData(
                    id = manga.id,
                    isAnime = false,
                    title = manga.title,
                    subtitle = manga.genre?.firstOrNull() ?: "Manga",
                    coverData = manga.asMangaCover(),
                    mediaType = MediaType.MANGA,
                    rating = ratingStr,
                    synopsis = manga.description ?: manga.title,
                )
            }

            val unifiedRecommended = (animeItems + mangaItems).shuffled()
            
            // Garantizar que el carrusel Destacados tome de las extensiones ancladas y omita locales
            val carouselFeatured = (animeItems + mangaItems)
                .distinctBy { it.id }
                .take(7)

            State(
                isLoading = false,
                heroList = carouselFeatured,
                continueList = unifiedContinue,
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
