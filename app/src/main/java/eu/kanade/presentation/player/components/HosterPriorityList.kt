package eu.kanade.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import tachiyomi.presentation.core.components.material.padding

/**
 * Reorderable list of hoster names with a checkbox to turn each one off. Used by the player's
 * hoster priority sheet and the player settings.
 */
@Composable
fun HosterPriorityList(
    order: List<String>,
    disabled: Set<String>,
    onOrderChange: (List<String>) -> Unit,
    onDisabledChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    // Items are not keyed: with keys the list scrolls along with a row moved to the top.
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        itemsIndexed(order) { index, name ->
            HosterPriorityRow(
                name = name,
                enabled = name !in disabled,
                canMoveUp = index > 0,
                canMoveDown = index < order.lastIndex,
                onToggle = { onDisabledChange(if (name in disabled) disabled - name else disabled + name) },
                onMoveUp = { onOrderChange(order.swapped(index, index - 1)) },
                onMoveDown = { onOrderChange(order.swapped(index, index + 1)) },
            )
        }
    }
}

@Composable
private fun HosterPriorityRow(
    name: String,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Checkbox(checked = enabled, onCheckedChange = { onToggle() })
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Outlined.ArrowUpward, contentDescription = null)
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Outlined.ArrowDownward, contentDescription = null)
        }
    }
}

private fun <T> List<T>.swapped(from: Int, to: Int): List<T> = toMutableList().apply {
    this[from] = this[to].also { this[to] = this[from] }
}
