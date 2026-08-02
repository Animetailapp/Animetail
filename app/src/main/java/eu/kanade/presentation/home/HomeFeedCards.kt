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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

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
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
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
                            imageVector = if (mediaType == MediaType.MANGA) {
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
