package eu.kanade.presentation.browse.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.ItemCover
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import kotlin.math.roundToInt

/** Marker prefix written into [Manga.description] by EHentaiSource.parseGalleryElement */
const val EH_LIST_PREFIX = "EH_LIST:"

/**
 * Rich list item for E-Hentai / ExHentai browse results.
 * Shows: cover · title · uploader · star rating · pages · category chip · date · language flag.
 *
 * The [Manga.description] field must start with [EH_LIST_PREFIX] followed by pipe-separated values:
 *   category|uploader|rating|pages|posted|language
 */
@Composable
fun EHentaiBrowseListItem(
    manga: Manga,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val raw = manga.description?.takeIf { it.startsWith(EH_LIST_PREFIX) }
        ?.removePrefix(EH_LIST_PREFIX)
    val parts = raw?.split("|", limit = 6) ?: emptyList()

    val cat = parts.getOrNull(0)?.ifBlank { null }
    val upl = parts.getOrNull(1)?.ifBlank { null }
    val rat = parts.getOrNull(2)?.toFloatOrNull()
    val pgs = parts.getOrNull(3)?.ifBlank { null }
    val pst = parts.getOrNull(4)?.ifBlank { null }
    val lng = parts.getOrNull(5)?.ifBlank { null }

    val filledStars = rat?.roundToInt()?.coerceIn(0, 5) ?: 0
    val starText = "★".repeat(filledStars) + "☆".repeat(5 - filledStars)
    val langFlag = lng?.let { languageToEHFlag(it) }?.takeIf { it.isNotEmpty() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cover thumbnail — fixed height to avoid stretch when title is short
        ItemCover.Book(
            modifier = Modifier
                .height(80.dp)
                .alpha(if (manga.favorite) 0.34f else 1f),
            data = MangaCover(
                mangaId = manga.id,
                sourceId = manga.source,
                isMangaFavorite = manga.favorite,
                url = manga.thumbnailUrl,
                lastModified = manga.coverLastModified,
            ),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            // ── Title ────────────────────────────────────────────────────────────
            Text(
                text = manga.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (raw != null) {
                // ── Uploader ─────────────────────────────────────────────────────
                if (upl != null) {
                    Text(
                        text = upl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                Spacer(Modifier.height(2.dp))

                // ── Stars + pages + flag ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = starText,
                        color = Color(0xFFFFAD00),
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (langFlag != null) {
                        Text(
                            text = langFlag,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (pgs != null) {
                        Text(
                            text = "$pgs páginas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                // ── Category chip + date ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cat != null) {
                        Text(
                            text = cat,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(ehCategoryColor(cat))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (pst != null) {
                        Text(
                            text = pst,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun languageToEHFlag(language: String): String = when (language.lowercase().trim()) {
    "japanese" -> "🇯🇵"
    "english" -> "🇺🇸"
    "chinese" -> "🇨🇳"
    "korean" -> "🇰🇷"
    "spanish" -> "🇪🇸"
    "french" -> "🇫🇷"
    "german" -> "🇩🇪"
    "italian" -> "🇮🇹"
    "russian" -> "🇷🇺"
    "portuguese" -> "🇧🇷"
    "polish" -> "🇵🇱"
    "dutch" -> "🇳🇱"
    "thai" -> "🇹🇭"
    "arabic" -> "🇸🇦"
    "hungarian" -> "🇭🇺"
    "bulgarian" -> "🇧🇬"
    "ukrainian" -> "🇺🇦"
    "romanian" -> "🇷🇴"
    "czech" -> "🇨🇿"
    "indonesian" -> "🇮🇩"
    "vietnamese" -> "🇻🇳"
    "turkish" -> "🇹🇷"
    else -> ""
}

private fun ehCategoryColor(category: String): Color = when (category.lowercase().trim()) {
    "doujinshi" -> Color(0xFFE53935)
    "manga" -> Color(0xFFE65100)
    "artist cg", "artistcg" -> Color(0xFF1565C0)
    "game cg", "gamecg" -> Color(0xFF6A1B9A)
    "western" -> Color(0xFF2E7D32)
    "non-h", "non h" -> Color(0xFF37474F)
    "image set", "imageset" -> Color(0xFFF57F17)
    "cosplay" -> Color(0xFFBF360C)
    "asian porn", "asianporn" -> Color(0xFF880E4F)
    "misc", "miscellaneous" -> Color(0xFFC62828)
    else -> Color(0xFF616161)
}
