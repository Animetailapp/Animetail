package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.player.loader.HosterCache
import eu.kanade.tachiyomi.ui.player.settings.HOSTER_CACHE_DURATIONS
import eu.kanade.tachiyomi.ui.player.settings.HOSTER_LIST_CACHE_DURATIONS
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.hosterCacheDurationLabel
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.preference.Preference as AppPreference

/**
 * Appends the link cache settings to a source's preference screen. The durations are the app-wide
 * player settings, so they're kept out of the source's own preference store and say so in their
 * summary; only clearing the cache is per source.
 */
fun PreferenceScreen.addHosterCachePreferences(sourceId: Long) {
    val preferences = Injekt.get<PlayerPreferences>()

    val category = PreferenceCategory(context).apply {
        title = context.stringResource(AYMR.strings.pref_hoster_cache)
        isIconSpaceReserved = false
    }
    addPreference(category)

    category.addPreference(
        durationPreference(
            preference = preferences.hosterListCacheMinutes(),
            title = AYMR.strings.pref_hoster_list_cache_duration,
            durations = HOSTER_LIST_CACHE_DURATIONS,
        ),
    )
    category.addPreference(
        durationPreference(
            preference = preferences.hosterCacheMinutes(),
            title = AYMR.strings.pref_hoster_cache_duration,
            durations = HOSTER_CACHE_DURATIONS,
        ),
    )

    category.addPreference(
        Preference(context).apply {
            key = "app_hoster_cache_clear"
            title = context.stringResource(AYMR.strings.pref_hoster_cache_clear)
            summary = context.stringResource(AYMR.strings.pref_hoster_cache_clear_source_summary)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                HosterCache.shared.clearSource(sourceId)
                context.toast(AYMR.strings.pref_hoster_cache_cleared)
                true
            }
        },
    )
}

private fun PreferenceScreen.durationPreference(
    preference: AppPreference<Int>,
    title: StringResource,
    durations: List<Int>,
) = ListPreference(context).apply {
    key = "app_${preference.key()}"
    this.title = context.stringResource(title)
    dialogTitle = this.title
    entries = durations.map { context.hosterCacheDurationLabel(it) }.toTypedArray()
    entryValues = durations.map { it.toString() }.toTypedArray()
    isPersistent = false
    value = preference.get().toString()
    summaryProvider = Preference.SummaryProvider<ListPreference> {
        context.stringResource(AYMR.strings.pref_hoster_cache_duration_all_sources_summary, it.entry.toString())
    }
    isIconSpaceReserved = false
    setOnPreferenceChangeListener { _, newValue ->
        preference.set((newValue as String).toInt())
        true
    }
}
