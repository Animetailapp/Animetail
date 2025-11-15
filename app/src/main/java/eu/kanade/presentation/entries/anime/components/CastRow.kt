package eu.kanade.presentation.entries.anime.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(cast.size) { idx ->
            val credit = cast[idx]
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .width(92.dp)
                    .clickable { onClick(credit) },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(6.dp),
                ) {
                    // Image circle
                    val imageModifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)

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
                        )
                    } else {
                        // Fallback placeholder when there is no image URL to avoid Coil null data crash
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = imageModifier,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = credit.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )

                    if (!credit.role.isNullOrBlank()) {
                        Text(
                            text = credit.role ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    } else if (!credit.character.isNullOrBlank()) {
                        Text(
                            text = credit.character ?: "",
                            style = MaterialTheme.typography.labelSmall,
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
