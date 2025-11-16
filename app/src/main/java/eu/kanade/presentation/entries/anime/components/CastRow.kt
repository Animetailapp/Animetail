package eu.kanade.presentation.entries.anime.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.animesource.model.Credit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CastRow(
    cast: List<Credit>,
    modifier: Modifier = Modifier,
    onClick: (Credit) -> Unit = {},
) {
    if (cast.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        items(cast.size, key = { it }) { idx ->
            val credit = cast[idx]
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = tween(durationMillis = 150),
                label = "scale",
            )
            val elevation by animateDpAsState(
                targetValue = if (isPressed) 8.dp else 4.dp,
                animationSpec = tween(durationMillis = 150),
                label = "elevation",
            )

            Card(
                elevation = CardDefaults.cardElevation(
                    defaultElevation = elevation,
                ),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 8.dp,
                    bottomStart = 8.dp,
                    bottomEnd = 16.dp,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .width(100.dp)
                    .height(160.dp)
                    .scale(scale)
                    .animateItemPlacement()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            ),
                        ),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 8.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 16.dp,
                        ),
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onClick(credit) },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    MaterialTheme.colorScheme.surface,
                                ),
                                center = androidx.compose.ui.geometry.Offset(0.5f, 0.2f), // Slightly above center
                                radius = 150f,
                            ),
                        )
                        .padding(8.dp),
                ) {
                    // Image circle with border
                    val imageModifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = CircleShape,
                        )

                    val ctx = LocalContext.current
                    if (!credit.image_url.isNullOrBlank()) {
                        val request = ImageRequest.Builder(ctx)
                            .data(credit.image_url)
                            .crossfade(true)
                            .build()

                        SubcomposeAsyncImage(
                            model = request,
                            contentDescription = credit.name,
                            modifier = imageModifier,
                            loading = {
                                // Shimmer effect mientras carga
                                ShimmerPlaceholder(modifier = imageModifier)
                            },
                            error = {
                                // Placeholder con ícono si falla
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = imageModifier
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                                ),
                                                center = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                                radius = 40f,
                                            ),
                                        ),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            },
                        )
                    } else {
                        // Placeholder con ícono si no hay URL
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = imageModifier
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                        ),
                                        center = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                        radius = 40f,
                                    ),
                                ),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = credit.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        minLines = 1,
                    )

                    if (!credit.role.isNullOrBlank()) {
                        Text(
                            text = credit.role ?: "",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    } else if (!credit.character.isNullOrBlank()) {
                        Text(
                            text = credit.character ?: "",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.secondary,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.LightGray.copy(alpha = 0.6f),
            Color.LightGray.copy(alpha = 0.2f),
            Color.LightGray.copy(alpha = 0.6f),
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim),
        tileMode = TileMode.Mirror,
    )

    Box(
        modifier = modifier
            .background(shimmerBrush),
    )
}
