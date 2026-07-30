package eu.kanade.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.home.HomeFeedScreenModel
import eu.kanade.tachiyomi.ui.home.HomeTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Modelo de item para representar animes, mangas, películas y series en la Home.
 */
data class HomeItemData(
    val id: Long,
    val isAnime: Boolean = true,
    val inLibrary: Boolean = false,
    val episodeId: Long? = null,
    val chapterId: Long? = null,
    val title: String,
    val subtitle: String,
    val coverUrl: String? = null,
    val coverData: Any? = null,
    val mediaType: MediaType,
    val rating: String = "",
    val progress: Float = 0f,
    val remainingInfo: String = "",
    val synopsis: String = "",
    val genres: String = "",
    val lastUpdatedTimestamp: Long = 0L,
)

/**
 * Pantalla completa de Inicio (Home Feed) conectada a datos reales de la base de datos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    screenModel: HomeFeedScreenModel? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current
    val model = screenModel ?: remember { HomeFeedScreenModel() }
    val state by model.state.collectAsState()
    var selectedMediaType by remember { mutableStateOf(MediaType.ALL) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        HomeTab.openSettingsSheetEvent.receiveAsFlow().collectLatest {
            showSettingsDialog = true
        }
    }

    val onItemClick: (HomeItemData) -> Unit = { item ->
        if (item.inLibrary) {
            if (item.isAnime) {
                navigator?.push(AnimeScreen(item.id))
            } else {
                navigator?.push(MangaScreen(item.id))
            }
        } else {
            if (item.isAnime) {
                navigator?.push(GlobalAnimeSearchScreen(item.title))
            } else {
                navigator?.push(GlobalMangaSearchScreen(item.title))
            }
        }
    }

    // Acción directa de reproductor / lector al tocar "Continuar viendo y leyendo"
    val onContinueItemClick: (HomeItemData) -> Unit = { item ->
        if (item.isAnime && item.episodeId != null) {
            scope.launch {
                MainActivity.startPlayerActivity(context, item.id, item.episodeId, false)
            }
        } else if (!item.isAnime && item.chapterId != null) {
            context.startActivity(ReaderActivity.newIntent(context, item.id, item.chapterId))
        } else {
            onItemClick(item)
        }
    }

    if (showSettingsDialog) {
        HomeFeedSettingsDialog(
            state = state,
            onToggleSection = { model.toggleSection(it) },
            onSetMediaFilter = { model.setMediaFilter(it) },
            onToggleAutoScrollHero = { model.toggleAutoScrollHero() },
            onSetHeroSource = { model.setHeroSource(it) },
            onSetItemsPerSection = { model.setItemsPerSection(it) },
            onToggleHideCompleted = { model.toggleHideCompletedInRecommended() },
            onToggleEnableTmdb = { model.toggleEnableTmdb() },
            onToggleEnableAnilist = { model.toggleEnableAnilist() },
            onDismissRequest = { showSettingsDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(MR.strings.label_home),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = { navigator?.push(BrowseTab) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(MR.strings.action_search),
                        )
                    }
                    IconButton(onClick = { navigator?.push(UpdatesTab) }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = stringResource(MR.strings.label_notifications),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        tachiyomi.presentation.core.components.material.PullRefresh(
            refreshing = state.isRefreshing,
            onRefresh = { model.refresh() },
            enabled = true,
            indicatorPadding = padding,
        ) {
            if (state.isLoading) {
                LoadingScreen(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            } else {
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. Chips de Filtro Horizontal (Todo, Películas, Series, Anime, Manga)
                    item {
                        MediaFormatFilterChips(
                            selectedMediaType = selectedMediaType,
                            onMediaTypeSelected = { selectedMediaType = it },
                        )
                    }

                    // 2. Banner Destacado (Hero Carousel con avance automático de 7+ ítems)
                    val filteredHeroList = if (selectedMediaType == MediaType.ALL) {
                        state.heroList
                    } else {
                        state.heroList.filter { it.mediaType == selectedMediaType }
                    }
                    if (state.showFeatured && filteredHeroList.isNotEmpty()) {
                        item {
                            HeroMediaCarousel(
                                heroList = filteredHeroList,
                                onItemClick = onContinueItemClick,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                autoScrollHero = state.autoScrollHero,
                            )
                        }
                    }

                    // 3. Sección "Continuar viendo y leyendo"
                    if (state.showContinue && state.continueList.isNotEmpty()) {
                        item {
                            SectionHeader(title = stringResource(MR.strings.label_continue_watching_reading))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val filteredContinue = if (selectedMediaType == MediaType.ALL) {
                                    state.continueList
                                } else {
                                    state.continueList.filter { it.mediaType == selectedMediaType }
                                }

                                items(filteredContinue) { item ->
                                    ContinueWatchingReadingCard(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        coverUrl = item.coverUrl,
                                        coverData = item.coverData,
                                        mediaType = item.mediaType,
                                        progress = item.progress,
                                        remainingInfo = item.remainingInfo,
                                        onClick = { onContinueItemClick(item) },
                                    )
                                }
                            }
                        }
                    }

                    // 4. Sección Inteligente "Porque viste / leíste [Título]..."
                    if (state.showBecauseYouWatched && state.becauseYouWatchedTitle != null &&
                        state.becauseYouWatchedList.isNotEmpty()
                    ) {
                        item {
                            val headerText = if (state.becauseYouWatchedIsAnime) {
                                stringResource(MR.strings.because_you_watched, state.becauseYouWatchedTitle!!)
                            } else {
                                stringResource(MR.strings.because_you_read, state.becauseYouWatchedTitle!!)
                            }
                            SectionHeader(title = headerText)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val filteredBecause = if (selectedMediaType == MediaType.ALL) {
                                    state.becauseYouWatchedList
                                } else {
                                    state.becauseYouWatchedList.filter { it.mediaType == selectedMediaType }
                                }

                                items(filteredBecause) { item ->
                                    MediaPosterCard(
                                        item = item,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }

                    // 5. Sección "Recomendados para ti"
                    if (state.showRecommended && state.recommendedList.isNotEmpty()) {
                        item {
                            SectionHeader(title = stringResource(MR.strings.label_recommended_for_you))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val filteredRecs = if (selectedMediaType == MediaType.ALL) {
                                    state.recommendedList
                                } else {
                                    state.recommendedList.filter { it.mediaType == selectedMediaType }
                                }

                                items(filteredRecs) { item ->
                                    MediaPosterCard(
                                        item = item,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }

                    // 6. Sección "Películas populares" (TMDB)
                    if (state.showPopularMovies && state.movieList.isNotEmpty() &&
                        (selectedMediaType == MediaType.ALL || selectedMediaType == MediaType.MOVIES)
                    ) {
                        item {
                            SectionHeader(title = stringResource(MR.strings.label_popular_movies))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.movieList) { item ->
                                    MediaPosterCard(
                                        item = item,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }

                    // 7. Sección "Series populares" (TMDB)
                    if (state.showPopularSeries && state.seriesList.isNotEmpty() &&
                        (selectedMediaType == MediaType.ALL || selectedMediaType == MediaType.SERIES)
                    ) {
                        item {
                            SectionHeader(title = stringResource(MR.strings.label_popular_series))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.seriesList) { item ->
                                    MediaPosterCard(
                                        item = item,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }

                    // 8. Sección "Anime populares"
                    if (state.showPopularAnime && state.animeList.isNotEmpty() &&
                        (selectedMediaType == MediaType.ALL || selectedMediaType == MediaType.ANIME)
                    ) {
                        item {
                            SectionHeader(title = stringResource(MR.strings.label_popular_anime))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.animeList) { item ->
                                    MediaPosterCard(
                                        item = item,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }

                    // 9. Sección "Manga populares"
                    if (state.showPopularManga && state.mangaList.isNotEmpty() &&
                        (selectedMediaType == MediaType.ALL || selectedMediaType == MediaType.MANGA)
                    ) {
                        item {
                            SectionHeader(title = stringResource(MR.strings.label_popular_manga))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.mangaList) { item ->
                                    MediaPosterCard(
                                        item = item,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            } // end else (not loading)
        }
    }
}

/**
 * Encabezado de sección reutilizable.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        ),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * Insignia de calificación de estrellas.
 */
@Composable
fun RatingBadge(
    rating: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xCC1A1A1A), // Fondo oscuro semitransparente
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFC107), // Color dorado
                modifier = Modifier.size(10.dp),
            )
            Text(
                text = rating,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Tarjeta de Póster vertical con ratio 2:3 y badge de formato.
 */
@Composable
fun MediaPosterCard(
    item: HomeItemData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Surface(
            modifier = Modifier
                .width(110.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp)),
            shadowElevation = 4.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                eu.kanade.presentation.entries.components.ItemCover.Book(
                    data = item.coverData ?: item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                )

                // Badge de tipo de medio en la esquina superior izquierda
                MediaFormatBadge(
                    mediaType = item.mediaType,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )

                // Badge de calificación en la esquina inferior derecha
                if (item.rating.isNotBlank()) {
                    RatingBadge(
                        rating = item.rating,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
