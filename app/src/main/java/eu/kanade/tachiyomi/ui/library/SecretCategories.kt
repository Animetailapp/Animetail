package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.fragment.app.FragmentActivity
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.tail.TLMR

/**
 * What the library shows: its tabs, and the entries of each category id.
 */
data class SecretLibraryView<T>(
    val categories: List<Category>,
    val entriesByCategoryId: Map<Long, List<T>>,
)

object SecretCategoryFilter {

    /**
     * Hidden categories are never tabs. Secret categories are tabs only while [unlocked].
     *
     * While locked, an entry in any secret category is removed from every category it is in, so it can't show
     * through another tab or the ungrouped and grouped views that flatten all categories. Categories left without
     * entries are dropped from [SecretLibraryView.entriesByCategoryId], matching how the library groups entries.
     */
    fun <T> apply(
        allCategories: List<Category>,
        entriesByCategoryId: Map<Long, List<T>>,
        entryId: (T) -> Long,
        unlocked: Boolean,
    ): SecretLibraryView<T> {
        val tabs = allCategories.filter { !it.hidden && (unlocked || !it.secret) }
        val secretCategoryIds = allCategories.filter { it.secret }.mapTo(HashSet()) { it.id }
        if (unlocked || secretCategoryIds.isEmpty()) {
            return SecretLibraryView(tabs, entriesByCategoryId)
        }

        val secretEntryIds = secretCategoryIds.flatMapTo(HashSet()) { categoryId ->
            entriesByCategoryId[categoryId].orEmpty().map(entryId)
        }
        val entries = entriesByCategoryId
            .filterKeys { it !in secretCategoryIds }
            .mapValues { (_, entries) -> entries.filterNot { entryId(it) in secretEntryIds } }
            .filterValues { it.isNotEmpty() }
        return SecretLibraryView(tabs, entries)
    }

    /**
     * Library rows, one per entry and category, without the entries in a secret category. For lists outside the
     * library, which never show secret entries.
     */
    fun <T> withoutSecretEntries(
        allCategories: List<Category>,
        rows: List<T>,
        entryId: (T) -> Long,
        categoryId: (T) -> Long,
    ): List<T> {
        val secretCategoryIds = allCategories.filter { it.secret }.mapTo(HashSet()) { it.id }
        if (secretCategoryIds.isEmpty()) return rows
        val secretEntryIds = rows.filter { categoryId(it) in secretCategoryIds }.mapTo(HashSet(), entryId)
        return rows.filterNot { entryId(it) in secretEntryIds }
    }
}

/**
 * Whether secret categories are shown. One switch for the anime and manga libraries: it's turned on by the
 * unlock gesture and off as soon as another home tab is shown or the app goes to the background.
 */
object SecretCategoryLock {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var shownTabKey: String? = null

    fun unlock() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }

    /**
     * Called whenever a home tab is composed. Coming back to the same tab, for example from an entry opened in the
     * secret category, keeps it unlocked; switching to another tab locks it.
     */
    fun onHomeTabShown(key: String) {
        if (shownTabKey != null && key != shownTabKey) lock()
        shownTabKey = key
    }
}

/**
 * Counts taps and reports when [requiredTaps] of them came in a row, each at most [maxGapMillis] after the one
 * before. Rapid taps on a plain title are deliberate: scrolling, swiping tabs or a stray touch don't produce them.
 */
class SecretUnlockTapCounter(
    private val requiredTaps: Int = REQUIRED_TAPS,
    private val maxGapMillis: Long = MAX_GAP_MILLIS,
) {
    private var count = 0
    private var lastTapAtMillis = 0L

    /** Returns true when this tap completes the sequence, which then starts over. */
    fun onTap(atMillis: Long): Boolean {
        count = if (count > 0 && atMillis - lastTapAtMillis <= maxGapMillis) count + 1 else 1
        lastTapAtMillis = atMillis
        if (count < requiredTaps) return false
        count = 0
        return true
    }

    companion object {
        const val REQUIRED_TAPS = 5
        const val MAX_GAP_MILLIS = 500L
    }
}

/**
 * Calls [onUnlock] after the secret tap sequence on this element. No ripple and no click semantics, so the title
 * doesn't advertise that it reacts. [onUnlock] is captured once; pass a lambda that reads the current callback.
 */
fun Modifier.secretUnlockTaps(onUnlock: () -> Unit): Modifier = pointerInput(Unit) {
    val counter = SecretUnlockTapCounter()
    detectTapGestures(
        onTap = { if (counter.onTap(SystemClock.uptimeMillis())) onUnlock() },
    )
}

/**
 * Unlocks the secret categories after the tap sequence, asking for the screen lock first when that's turned on.
 * Does nothing when there are no secret categories, so the gesture gives nothing away. Returns whether it unlocked.
 */
suspend fun requestSecretCategoryUnlock(
    context: Context,
    hasSecretCategories: suspend () -> Boolean,
    unlock: () -> Unit,
): Boolean {
    if (!hasSecretCategories()) return false
    val requireAuth = context.appGraph.libraryPreferences.secretCategoriesRequireAuth.get()
    val allowed = !requireAuth ||
        (context as? FragmentActivity)?.authenticate(
            title = context.stringResource(TLMR.strings.secret_categories_unlock_title),
            subtitle = null,
        ) == true
    if (allowed) unlock()
    return allowed
}
