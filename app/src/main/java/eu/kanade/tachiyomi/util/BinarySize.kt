package eu.kanade.tachiyomi.util

import java.util.Locale

// The cache and buffer sizes are binary (1 GB = 1024 MB). Android's formatter uses decimal units,
// which shows 4096 MB as "4.3 GB" and 256 MB as "268 MB".
fun formatBinarySize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val number = if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
    return "$number ${units[unit]}"
}
