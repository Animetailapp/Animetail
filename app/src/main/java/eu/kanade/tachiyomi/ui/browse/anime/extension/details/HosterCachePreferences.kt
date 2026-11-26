package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.player.loader.HosterCache
import eu.kanade.tachiyomi.ui.player.settings.HOSTER_CACHE_DURATIONS
import eu.kanade.tachiyomi.ui.player.settings.HOSTER_LIST_CACHE_DURATIONS
import eu.kanade.tachiyomi.ui.player.settings.hosterCacheDurationLabel
import eu.kanade.tachiyomi.util.system.toast
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.core.common.preference.Preference as AppPreference

/**
 * Appends the hoster and cache settings to a source's preference screen. The durations and the
 * hoster priority are app-wide player settings, so they're kept out of the source's own preference
 * store and say so in their summary; clearing the caches is per source, or per anime when the
 * screen was opened from one.
 *
 * @param isForAnime whether [onClearVideoCache] clears only the anime the screen was opened from.
 */
fun PreferenceScreen.addHosterCachePreferences(
    sourceId: Long,
    isForAnime: Boolean,
    onHosterPriorityClick: () -> Unit,
    onClearVideoCache: () -> Unit,
) {
    val preferences = context.appGraph.playerPreferences

    val category = PreferenceCategory(context).apply {
        title = context.stringResource(AYMR.strings.pref_hoster_cache)
        isIconSpaceReserved = false
    }
    addPreference(category)

    category.addPreference(
        Preference(context).apply {
            key = "app_hoster_priority"
            title = context.stringResource(AYMR.strings.pref_hosters_priority)
            summary = context.stringResource(AYMR.strings.pref_hosters_priority_source_summary)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                onHosterPriorityClick()
                true
            }
        },
    )

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
                HosterCache.shared(context).clearSource(sourceId)
                context.toast(AYMR.strings.pref_hoster_cache_cleared)
                true
            }
        },
    )

    category.addPreference(
        Preference(context).apply {
            key = "app_video_cache_clear"
            title = context.stringResource(AYMR.strings.pref_video_cache_clear)
            summary = context.stringResource(
                if (isForAnime) {
                    AYMR.strings.pref_video_cache_clear_anime_summary
                } else {
                    AYMR.strings.pref_video_cache_clear_source_summary
                },
            )
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                onClearVideoCache()
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
    // Attaching a non-persistent preference resets its value to the default, so set both.
    setDefaultValue(preference.get().toString())
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
