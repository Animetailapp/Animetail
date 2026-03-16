package eu.kanade.presentation.entries.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.material.padding

/**
 * Metatag model for a single display tag.
 *
 * @param namespace The namespace (e.g. "artist", "female"), or null if flat.
 * @param text The display text of the tag value (e.g. "blonde hair").
 * @param search The search query string (e.g. "female:blonde hair$").
 */
@Immutable
data class DisplayTag(
    val namespace: String?,
    val text: String,
    val search: String,
)

/**
 * Grouped tag data computed from a List<String> of genres.
 *
 * Tags must ALL follow "namespace:value" format to produce a non-null result.
 * If not all tags match the format, returns null so the flat display is used.
 */
@Immutable
@JvmInline
value class SearchMetadataChips(val tags: Map<String, List<DisplayTag>>) {
    companion object {
        /**
         * Returns a [SearchMetadataChips] if ALL tags follow "namespace:value" format,
         * otherwise returns null (so the caller can fall back to flat display).
         */
        operator fun invoke(genres: List<String>?): SearchMetadataChips? {
            if (genres.isNullOrEmpty()) return null
            if (!genres.all { it.contains(':') }) return null

            val grouped = genres
                .map { tag ->
                    val index = tag.indexOf(':')
                    val namespace = tag.substring(0, index).trim()
                    val value = tag.substring(index + 1).trim()
                    val search = buildSearchQuery(namespace, value)
                    DisplayTag(namespace = namespace, text = value, search = search)
                }
                .groupBy { it.namespace.orEmpty() }

            return SearchMetadataChips(grouped)
        }

        /**
         * Wraps a namespace+tag into the E-Hentai search format.
         * Tags with spaces get quoted: `namespace:"multi word tag"$`
         * Single-word tags: `namespace:tag$`
         */
        private fun buildSearchQuery(namespace: String, tag: String): String {
            return if (tag.contains(' ')) {
                "$namespace:\"$tag\"$"
            } else {
                "$namespace:$tag$"
            }
        }
    }
}

/**
 * Displays tags grouped by namespace.
 *
 * Each row shows a namespace label chip (non-clickable) followed by tag chips.
 * Clicking a tag chip calls [onClick] with the search query string.
 */
@Composable
fun NamespacedTags(
    tags: SearchMetadataChips,
    onClick: (search: String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.tags.forEach { (namespace, tagList) ->
            Row(modifier = Modifier.padding(start = 16.dp)) {
                // Namespace label chip (non-interactive)
                if (namespace.isNotEmpty()) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        SuggestionChip(
                            modifier = Modifier.padding(top = 4.dp),
                            onClick = {},
                            label = {
                                Text(
                                    text = namespace.replaceFirstChar { it.uppercaseChar() },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = false,
                            ),
                        )
                    }
                }
                // Tag chips in a flow
                FlowRow(
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    tagList.forEach { tag ->
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            SuggestionChip(
                                modifier = Modifier.padding(vertical = 4.dp),
                                onClick = { onClick(tag.search) },
                                label = {
                                    Text(
                                        text = tag.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
