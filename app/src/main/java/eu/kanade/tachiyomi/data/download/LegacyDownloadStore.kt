package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Preference file that the anime and manga download stores used to share. Each store's restore() and
 * clear() cleared the whole file, so restoring one queue wiped the other, and an episode and a chapter
 * with the same id overwrote each other's entry.
 */
private const val LEGACY_FILE = "active_downloads"

/**
 * Moves the entries of the legacy shared file that [belongsHere] accepts into this preference file.
 * Each store only accepts entries that deserialize as its own type, so neither store touches the
 * other's entries.
 */
fun SharedPreferences.moveEntriesFromLegacyDownloadStore(context: Context, belongsHere: (String) -> Boolean) {
    val legacy = context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
    val entries = legacy.all
        .mapNotNull { (key, value) -> (value as? String)?.takeIf(belongsHere)?.let { key to it } }
    if (entries.isEmpty()) return

    // Copy before removing, synchronously, so an entry is always in at least one of the files.
    edit(commit = true) { entries.forEach { (key, value) -> putString(key, value) } }
    legacy.edit(commit = true) { entries.forEach { (key, _) -> remove(key) } }
}
