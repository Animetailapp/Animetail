package eu.kanade.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

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
 * Componente de sección reutilizable para el feed de inicio con soporte de filtrado y límite.
 */
@Composable
fun HomeFeedSection(
    title: String,
    items: List<HomeItemData>,
    selectedMediaType: MediaType,
    itemsPerSection: Int = 12,
    shouldFilterByType: Boolean = true,
    onItemClick: (HomeItemData) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val filteredItems = remember(items, selectedMediaType, itemsPerSection, shouldFilterByType) {
        if (!shouldFilterByType || selectedMediaType == MediaType.ALL) {
            items
        } else {
            items.filter { it.mediaType == selectedMediaType }
        }.take(itemsPerSection)
    }

    if (filteredItems.isNotEmpty()) {
        Column(modifier = modifier) {
            SectionHeader(title = title)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filteredItems) { item ->
                    MediaPosterCard(
                        item = item,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }
    }
}
