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
import androidx.compose.ui.focus.FocusRequester
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

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

    val isAndroidTV = remember { context.packageManager.hasSystemFeature("android.software.leanback") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isAndroidTV) {
        if (isAndroidTV) {
            delay(300)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (state.isLoading) {
        LoadingScreen(modifier = modifier)
        return
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
            enabled = !isAndroidTV,
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
                            focusRequester = focusRequester,
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
                    if (state.showBecauseYouWatched && state.becauseYouWatchedTitle != null) {
                        item {
                            val headerText = if (state.becauseYouWatchedIsAnime) {
                                stringResource(MR.strings.because_you_watched, state.becauseYouWatchedTitle!!)
                            } else {
                                stringResource(MR.strings.because_you_read, state.becauseYouWatchedTitle!!)
                            }
                            HomeFeedSection(
                                title = headerText,
                                items = state.becauseYouWatchedList,
                                selectedMediaType = selectedMediaType,
                                itemsPerSection = state.itemsPerSection,
                                onItemClick = onItemClick,
                            )
                        }
                    }

                    // 5. Sección "Recomendados para ti"
                    if (state.showRecommended) {
                        item {
                            HomeFeedSection(
                                title = stringResource(MR.strings.label_recommended_for_you),
                                items = state.recommendedList,
                                selectedMediaType = selectedMediaType,
                                itemsPerSection = state.itemsPerSection,
                                onItemClick = onItemClick,
                            )
                        }
                    }

                    // 6. Sección "Películas populares" (TMDB)
                    if (state.showPopularMovies) {
                        item {
                            HomeFeedSection(
                                title = stringResource(MR.strings.label_popular_movies),
                                items = state.movieList,
                                selectedMediaType = selectedMediaType,
                                itemsPerSection = state.itemsPerSection,
                                onItemClick = onItemClick,
                            )
                        }
                    }

                    // 7. Sección "Series populares" (TMDB)
                    if (state.showPopularSeries) {
                        item {
                            HomeFeedSection(
                                title = stringResource(MR.strings.label_popular_series),
                                items = state.seriesList,
                                selectedMediaType = selectedMediaType,
                                itemsPerSection = state.itemsPerSection,
                                onItemClick = onItemClick,
                            )
                        }
                    }

                    // 8. Sección "Anime populares"
                    if (state.showPopularAnime) {
                        item {
                            HomeFeedSection(
                                title = stringResource(MR.strings.label_popular_anime),
                                items = state.animeList,
                                selectedMediaType = selectedMediaType,
                                itemsPerSection = state.itemsPerSection,
                                onItemClick = onItemClick,
                            )
                        }
                    }

                    // 9. Sección "Manga populares"
                    if (state.showPopularManga) {
                        item {
                            HomeFeedSection(
                                title = stringResource(MR.strings.label_popular_manga),
                                items = state.mangaList,
                                selectedMediaType = selectedMediaType,
                                itemsPerSection = state.itemsPerSection,
                                onItemClick = onItemClick,
                            )
                        }
                    }
                }
            } // end else (not loading)
        }
    }
}
