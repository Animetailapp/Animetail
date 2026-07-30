package eu.kanade.presentation.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
