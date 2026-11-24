package eu.kanade.tachiyomi.ui.player.settings

import android.content.Context
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

/** Video link cache durations offered in the settings, in minutes. Zero turns the cache off. */
val HOSTER_CACHE_DURATIONS = listOf(0, 30, 60, 120, 360, 720, 1440, 43200)

/** Hoster list cache durations offered in the settings, in minutes. Zero turns the cache off. */
val HOSTER_LIST_CACHE_DURATIONS = listOf(0, 60, 360, 720, 1440, 4320, 10080, 43200)

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 1440

fun Context.hosterCacheDurationLabel(minutes: Int): String = when {
    minutes == 0 -> stringResource(MR.strings.off)

    minutes % MINUTES_PER_DAY == 0 && minutes > MINUTES_PER_DAY -> {
        val days = minutes / MINUTES_PER_DAY
        pluralStringResource(AYMR.plurals.hoster_cache_days, days, days)
    }

    minutes % MINUTES_PER_HOUR == 0 -> {
        val hours = minutes / MINUTES_PER_HOUR
        pluralStringResource(AYMR.plurals.hoster_cache_hours, hours, hours)
    }

    else -> pluralStringResource(AYMR.plurals.hoster_cache_minutes, minutes, minutes)
}
