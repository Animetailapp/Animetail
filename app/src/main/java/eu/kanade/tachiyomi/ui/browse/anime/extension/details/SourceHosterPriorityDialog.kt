package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.screen.player.HosterPriorityDialog
import eu.kanade.tachiyomi.ui.player.loader.HosterCache
import eu.kanade.tachiyomi.ui.player.loader.HosterPriority
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Edits the app-wide hoster priority showing only the hosters cached for [sourceId], so the order
 * saved for other extensions is kept.
 */
@Composable
fun SourceHosterPriorityDialog(sourceId: Long, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { Injekt.get<PlayerPreferences>() }
    val sourceHosters by produceState<List<String>?>(null, sourceId) {
        value = withIOContext { HosterCache.shared.hosterNames(sourceId) }
    }
    val hosterNames = sourceHosters ?: return

    if (hosterNames.isEmpty()) {
        LaunchedEffect(Unit) {
            context.toast(AYMR.strings.pref_hosters_priority_empty)
            onDismissRequest()
        }
        return
    }

    val priority = remember(hosterNames) {
        HosterPriority(preferences.hosterPriority().get(), preferences.disabledHosters().get())
    }
    val orderedNames = remember(priority) { priority.sort(hosterNames) { it } }

    HosterPriorityDialog(
        hosterNames = orderedNames,
        disabledNames = orderedNames.filter(priority::isDisabled).toSet(),
        onConfirm = { order, disabledNames ->
            val savedOrder = HosterPriority.parseNames(preferences.hosterPriority().get())
            val savedDisabled = HosterPriority.parseNames(preferences.disabledHosters().get())
            preferences.hosterPriority().set(HosterPriority.mergeOrder(savedOrder, order).joinToString(", "))
            preferences.disabledHosters().set(
                HosterPriority.mergeDisabled(savedDisabled, order, disabledNames).joinToString(", "),
            )
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest,
    )
}
