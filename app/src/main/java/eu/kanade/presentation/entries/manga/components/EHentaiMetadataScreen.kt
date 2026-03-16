package eu.kanade.presentation.entries.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.manga.builtin.ehentai.EHentaiGalleryMetadata
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Voyager screen that displays all E-Hentai gallery metadata fields.
 * Mirrors Komikku's "Más información" detail screen.
 *
 * @param metadataJson The serialized [EHentaiGalleryMetadata] JSON string (without the marker).
 */
class EHentaiMetadataScreen(
    private val metadataJson: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val metadata = EHentaiGalleryMetadata.decode(
            EHentaiGalleryMetadata.MARKER + metadataJson,
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(TLMR.strings.eh_metadata_title)) },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            if (metadata == null) {
                Text(
                    text = stringResource(TLMR.strings.eh_metadata_unknown),
                    modifier = Modifier.padding(contentPadding).padding(16.dp),
                )
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                // Gallery ID
                item {
                    MetadataRow(
                        label = stringResource(TLMR.strings.eh_metadata_gallery_id),
                        value = metadata.gId.ifBlank { stringResource(TLMR.strings.eh_metadata_unknown) },
                    )
                }

                // Token
                item {
                    MetadataRow(
                        label = stringResource(TLMR.strings.eh_metadata_gallery_token),
                        value = metadata.gToken.ifBlank { stringResource(TLMR.strings.eh_metadata_unknown) },
                    )
                }

                // ExHentai gallery
                item {
                    MetadataRow(
                        label = stringResource(TLMR.strings.eh_metadata_exhentai_gallery),
                        value = if (metadata.exh) {
                            stringResource(TLMR.strings.eh_metadata_yes)
                        } else {
                            stringResource(TLMR.strings.eh_metadata_no)
                        },
                    )
                }

                // Thumbnail URL
                if (!metadata.thumbnailUrl.isNullOrBlank()) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_thumbnail_url),
                            value = metadata.thumbnailUrl,
                        )
                    }
                }

                // Title
                if (!metadata.title.isNullOrBlank()) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_gallery_title),
                            value = metadata.title,
                        )
                    }
                }

                // Alt title
                if (!metadata.altTitle.isNullOrBlank()) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_alt_title),
                            value = metadata.altTitle,
                        )
                    }
                }

                // Genre
                if (!metadata.genre.isNullOrBlank()) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_genre),
                            value = metadata.genre,
                        )
                    }
                }

                // Uploader
                if (!metadata.uploader.isNullOrBlank()) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_uploader),
                            value = metadata.uploader,
                        )
                    }
                }

                // Date posted
                if (!metadata.datePosted.isNullOrBlank()) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_date_posted),
                            value = metadata.datePosted,
                        )
                    }
                }

                // Parent gallery
                item {
                    MetadataRow(
                        label = stringResource(TLMR.strings.eh_metadata_parent),
                        value = metadata.parent ?: stringResource(TLMR.strings.eh_metadata_none),
                    )
                }

                // Visible
                if (!metadata.visible.isNullOrBlank()) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_visible),
                            value = metadata.visible,
                        )
                    }
                }

                // Language
                if (!metadata.language.isNullOrBlank()) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_language),
                            value = metadata.language,
                        )
                    }
                }

                // Translated
                item {
                    MetadataRow(
                        label = stringResource(TLMR.strings.eh_metadata_translated),
                        value = if (metadata.translated) {
                            stringResource(TLMR.strings.eh_metadata_yes)
                        } else {
                            stringResource(TLMR.strings.eh_metadata_no)
                        },
                    )
                }

                // File size
                if (!metadata.fileSize.isNullOrBlank()) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_file_size),
                            value = metadata.fileSize,
                        )
                    }
                }

                // Page count
                if (metadata.length != null) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_page_count),
                            value = metadata.length.toString(),
                        )
                    }
                }

                // Favorites
                if (metadata.favorites != null) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_favorites),
                            value = "${metadata.favorites}",
                        )
                    }
                }

                // Rating
                if (metadata.rating != null) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_rating),
                            value = "★ ${metadata.rating}",
                        )
                    }
                }

                // Rating count
                if (metadata.ratingCount != null) {
                    item {
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_rating_count),
                            value = metadata.ratingCount.toString(),
                        )
                    }
                }

                // Last update check
                if (metadata.lastUpdateCheck > 0L) {
                    item {
                        val dateStr = SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.getDefault(),
                        ).format(Date(metadata.lastUpdateCheck))
                        MetadataRow(
                            label = stringResource(TLMR.strings.eh_metadata_last_update_check),
                            value = dateStr,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.4f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(0.6f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
