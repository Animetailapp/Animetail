package eu.kanade.presentation.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.home.HeroSource
import eu.kanade.tachiyomi.ui.home.HomeFeedScreenModel
import eu.kanade.tachiyomi.ui.home.HomeMediaFilter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Representa los diferentes formatos de contenido soportados en la plataforma.
 */
enum class MediaType(val icon: ImageVector, val color: Color) {
    ALL(Icons.Default.VideoLibrary, Color(0xFF6366F1)),
    MOVIES(Icons.Default.Movie, Color(0xFFEAB308)),
    SERIES(Icons.Default.Tv, Color(0xFFA855F7)),
    ANIME(Icons.Default.PlayArrow, Color(0xFFEF4444)),
    MANGA(Icons.Default.Book, Color(0xFFF97316)),
}

/**
 * Obtener la etiqueta internacionalizada para cada tipo de formato.
 */
@Composable
fun MediaType.getLabel(): String {
    return when (this) {
        MediaType.ALL -> stringResource(MR.strings.label_all_media)
        MediaType.MOVIES -> stringResource(MR.strings.label_movies)
        MediaType.SERIES -> stringResource(MR.strings.label_series)
        MediaType.ANIME -> stringResource(MR.strings.label_anime)
        MediaType.MANGA -> stringResource(MR.strings.label_manga)
    }
}

/**
 * Chips de filtro horizontal en el header para seleccionar el formato de contenido.
 */
@Composable
fun MediaFormatFilterChips(
    selectedMediaType: MediaType,
    onMediaTypeSelected: (MediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(MediaType.entries.toTypedArray()) { mediaType ->
            val isSelected = selectedMediaType == mediaType
            val label = mediaType.getLabel()
            FilterChip(
                selected = isSelected,
                onClick = { onMediaTypeSelected(mediaType) },
                label = {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = mediaType.icon,
                        contentDescription = label,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else mediaType.color,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mediaType.color,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                ),
                shape = RoundedCornerShape(20.dp),
            )
        }
    }
}

/**
 * Insignia visual (Badge) que identifica el tipo de formato en las portadas.
 */
@Composable
fun MediaFormatBadge(
    mediaType: MediaType,
    modifier: Modifier = Modifier,
    extraText: String? = null,
) {
    val badgeLabel = extraText ?: mediaType.getLabel().uppercase()
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp)),
        color = mediaType.color.copy(alpha = 0.95f),
        contentColor = Color.White,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = mediaType.icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color.White,
            )
            Text(
                text = badgeLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
            )
        }
    }
}

/**
 * Tarjeta mejorada para "Continuar viendo y leyendo" con progreso e información del episodio/capítulo.
 */
@Composable
fun ContinueWatchingReadingCard(
    title: String,
    subtitle: String,
    coverUrl: String? = null,
    coverData: Any? = null,
    mediaType: MediaType,
    progress: Float,
    remainingInfo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
            ) {
                eu.kanade.presentation.entries.components.ItemCover.Book(
                    data = coverData ?: coverUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                )

                // Overlay gradiente en la parte inferior de la imagen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                startY = 50f,
                            ),
                        ),
                )

                // Badge de formato de contenido
                MediaFormatBadge(
                    mediaType = mediaType,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )

                // Botón de reproducción flotante
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(CircleShape),
                    color = Color.Black.copy(alpha = 0.65f),
                    contentColor = Color.White,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (mediaType ==
                                MediaType.MANGA
                            ) {
                                Icons.Default.Book
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = stringResource(MR.strings.action_resume),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // Texto de tiempo restante / páginas
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.Black.copy(alpha = 0.85f),
                ) {
                    Text(
                        text = remainingInfo,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Barra de progreso visual en el borde inferior del thumbnail
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter),
                    color = mediaType.color,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }

            // Título y subtítulo con alto contraste y fondo sólido
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Carrusel de Banner principal (Hero Carousel) que desliza automáticamente entre varios ítems destacados.
 */
@Composable
fun HeroMediaCarousel(
    heroList: List<HomeItemData>,
    onItemClick: (HomeItemData) -> Unit,
    modifier: Modifier = Modifier,
    autoScrollHero: Boolean = true,
) {
    if (heroList.isEmpty()) return

    val displayList = remember(heroList) { heroList.take(7) }
    val pagerState = rememberPagerState(pageCount = { displayList.size })

    // Auto-advance cada 4 segundos
    LaunchedEffect(pagerState, displayList, autoScrollHero) {
        if (autoScrollHero && displayList.size > 1) {
            while (true) {
                delay(4000L)
                val nextPage = (pagerState.currentPage + 1) % displayList.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val item = displayList[page]
            HeroMediaBanner(
                title = item.title,
                genres = item.genres.ifBlank { item.subtitle },
                synopsis = item.synopsis,
                rating = item.rating,
                coverUrl = item.coverUrl,
                coverData = item.coverData,
                mediaType = item.mediaType,
                onPrimaryAction = { onItemClick(item) },
            )
        }

        // Indicadores de páginas (puntos estilizados)
        if (displayList.size > 1) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(displayList.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(
                                    width = if (isSelected) 14.dp else 6.dp,
                                    height = 6.dp,
                                )
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(0xFFFFC107) else Color.White.copy(alpha = 0.45f),
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Banner principal (Hero Slider) con estética cinematográfica y badges informativos.
 */
@Composable
fun HeroMediaBanner(
    title: String,
    genres: String,
    synopsis: String,
    rating: String,
    coverUrl: String? = null,
    coverData: Any? = null,
    mediaType: MediaType,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(270.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = coverData ?: coverUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(Color(0x1F888888)),
                error = eu.kanade.presentation.util.rememberResourceBitmapPainter(
                    id = eu.kanade.tachiyomi.R.drawable.cover_error,
                ),
            )

            // Gradiente cinematográfico oscuro
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.95f),
                            ),
                        ),
                    ),
            )

            // Contenido en la parte inferior del banner
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Badge del formato real (PELÍCULA, ANIME, SERIE)
                    MediaFormatBadge(mediaType = mediaType)

                    // Badge de Destacado
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFC107).copy(alpha = 0.9f),
                    ) {
                        Text(
                            text = stringResource(MR.strings.label_featured).uppercase(),
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            letterSpacing = 0.5.sp,
                        )
                    }

                    // Rating Badge (solo si tiene calificación real)
                    if (rating.isNotBlank() && rating != "0" && rating != "0.0") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = rating,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = genres,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (synopsis.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = synopsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onPrimaryAction,
                    colors = ButtonDefaults.buttonColors(containerColor = mediaType.color),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = if (mediaType == MediaType.MANGA) Icons.Default.Book else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (mediaType == MediaType.MANGA) {
                            stringResource(MR.strings.action_read_now)
                        } else {
                            stringResource(MR.strings.action_watch_now)
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Diálogo modal para personalizar y reordenar las secciones del Feed de Inicio.
 */
@Composable
private fun <T> CompactSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                val surfaceColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
                val textColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    color = surfaceColor,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(value) },
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Diálogo modal para personalizar las opciones y secciones del Feed de Inicio.
 */
@Composable
fun HomeFeedSettingsDialog(
    state: HomeFeedScreenModel.State,
    onToggleSection: (String) -> Unit,
    onSetMediaFilter: (HomeMediaFilter) -> Unit,
    onToggleAutoScrollHero: () -> Unit,
    onSetHeroSource: (HeroSource) -> Unit,
    onSetItemsPerSection: (Int) -> Unit,
    onToggleHideCompleted: () -> Unit,
    onToggleEnableTmdb: () -> Unit,
    onToggleEnableAnilist: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(MR.strings.content_filter_title),
            stringResource(MR.strings.show_featured),
            stringResource(MR.strings.visible_sections_title),
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> {
                    HeadingItem(stringResource(MR.strings.content_filter_title))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        CompactSegmentedControl(
                            options = listOf(
                                HomeMediaFilter.ALL to
                                    stringResource(MR.strings.home_media_filter_all),
                                HomeMediaFilter.VIDEO_ONLY to
                                    stringResource(MR.strings.home_media_filter_video),
                                HomeMediaFilter.MANGA_ONLY to
                                    stringResource(MR.strings.home_media_filter_manga),
                            ),
                            selected = state.mediaFilter,
                            onSelect = onSetMediaFilter,
                        )
                    }

                    HeadingItem(stringResource(MR.strings.items_per_section))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        CompactSegmentedControl(
                            options = listOf(
                                6 to stringResource(MR.strings.items_count_format, 6),
                                12 to stringResource(MR.strings.items_count_format, 12),
                                24 to stringResource(MR.strings.items_count_format, 24),
                            ),
                            selected = state.itemsPerSection,
                            onSelect = onSetItemsPerSection,
                        )
                    }

                    HeadingItem(stringResource(MR.strings.additional_filters_title))
                    CheckboxItem(
                        label = stringResource(MR.strings.hide_completed_recommended),
                        checked = state.hideCompletedInRecommended,
                        onClick = onToggleHideCompleted,
                    )
                    CheckboxItem(
                        label = stringResource(MR.strings.home_enable_tmdb),
                        checked = state.enableTmdb,
                        onClick = onToggleEnableTmdb,
                    )
                    CheckboxItem(
                        label = stringResource(MR.strings.home_enable_anilist),
                        checked = state.enableAnilist,
                        onClick = onToggleEnableAnilist,
                    )
                }

                1 -> {
                    HeadingItem(stringResource(MR.strings.show_featured))
                    CheckboxItem(
                        label = stringResource(MR.strings.auto_scroll_hero),
                        checked = state.autoScrollHero,
                        onClick = onToggleAutoScrollHero,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        CompactSegmentedControl(
                            options = listOf(
                                HeroSource.BOTH to
                                    stringResource(MR.strings.hero_source_both),
                                HeroSource.LIBRARY_ONLY to
                                    stringResource(MR.strings.hero_source_library),
                            ),
                            selected = state.heroSource,
                            onSelect = onSetHeroSource,
                        )
                    }
                }

                2 -> {
                    HeadingItem(stringResource(MR.strings.visible_sections_title))
                    CheckboxItem(
                        label = stringResource(MR.strings.show_featured),
                        checked = state.showFeatured,
                        onClick = { onToggleSection("featured") },
                    )
                    CheckboxItem(
                        label = stringResource(MR.strings.show_continue),
                        checked = state.showContinue,
                        onClick = { onToggleSection("continue") },
                    )
                    CheckboxItem(
                        label = stringResource(MR.strings.show_because_you_watched),
                        checked = state.showBecauseYouWatched,
                        onClick = { onToggleSection("because_you_watched") },
                    )
                    CheckboxItem(
                        label = stringResource(MR.strings.show_recommended),
                        checked = state.showRecommended,
                        onClick = { onToggleSection("recommended") },
                    )
                    CheckboxItem(
                        label = stringResource(MR.strings.label_popular_movies),
                        checked = state.showPopularMovies,
                        onClick = { onToggleSection("popular_movies") },
                    )
                    CheckboxItem(
                        label = stringResource(MR.strings.label_popular_series),
                        checked = state.showPopularSeries,
                        onClick = { onToggleSection("popular_series") },
                    )
                    CheckboxItem(
                        label = stringResource(MR.strings.show_popular_anime),
                        checked = state.showPopularAnime,
                        onClick = { onToggleSection("popular_anime") },
                    )
                    CheckboxItem(
                        label = stringResource(MR.strings.show_popular_manga),
                        checked = state.showPopularManga,
                        onClick = { onToggleSection("popular_manga") },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedSectionToggleItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
