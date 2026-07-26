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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
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
                            imageVector = if (mediaType == MediaType.MANGA) Icons.Default.Book else Icons.Default.PlayArrow,
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
) {
    if (heroList.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { heroList.size })

    // Auto-advance cada 4 segundos
    LaunchedEffect(pagerState, heroList) {
        if (heroList.size > 1) {
            while (true) {
                delay(4000L)
                val nextPage = (pagerState.currentPage + 1) % heroList.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val item = heroList[page]
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

        // Indicadores de páginas (puntos)
        if (heroList.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(heroList.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color.Red else Color.White.copy(alpha = 0.5f),
                            ),
                    )
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
            .height(260.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Imagen de portada principal
            eu.kanade.presentation.entries.components.ItemCover.Book(
                data = coverData ?: coverUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
            )

            // Gradiente cinematográfico oscuro
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f),
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
                    MediaFormatBadge(mediaType = mediaType, extraText = stringResource(MR.strings.label_featured))

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
fun HomeFeedSettingsDialog(
    showFeatured: Boolean,
    showContinue: Boolean,
    showBecauseYouWatched: Boolean,
    showRecommended: Boolean,
    showPopularAnime: Boolean,
    showPopularManga: Boolean,
    onToggleSection: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(MR.strings.customize_home_feed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedSectionToggleItem(
                    label = stringResource(MR.strings.show_featured),
                    checked = showFeatured,
                    onCheckedChange = { onToggleSection("featured") },
                )
                FeedSectionToggleItem(
                    label = stringResource(MR.strings.show_continue),
                    checked = showContinue,
                    onCheckedChange = { onToggleSection("continue") },
                )
                FeedSectionToggleItem(
                    label = stringResource(MR.strings.show_because_you_watched),
                    checked = showBecauseYouWatched,
                    onCheckedChange = { onToggleSection("because_you_watched") },
                )
                FeedSectionToggleItem(
                    label = stringResource(MR.strings.show_recommended),
                    checked = showRecommended,
                    onCheckedChange = { onToggleSection("recommended") },
                )
                FeedSectionToggleItem(
                    label = stringResource(MR.strings.show_popular_anime),
                    checked = showPopularAnime,
                    onCheckedChange = { onToggleSection("popular_anime") },
                )
                FeedSectionToggleItem(
                    label = stringResource(MR.strings.show_popular_manga),
                    checked = showPopularManga,
                    onCheckedChange = { onToggleSection("popular_manga") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
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
