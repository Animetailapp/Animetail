package tachiyomi.domain.upcoming.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getLongArray

class UpcomingPreferences(
    preferenceStore: PreferenceStore,
) {

    val mangaFilterIncludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        "pref_filter_upcoming_manga_included_categories",
        emptyList(),
    )

    val mangaFilterExcludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        "pref_filter_upcoming_manga_excluded_categories",
        emptyList(),
    )

    val animeFilterIncludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        "pref_filter_upcoming_anime_included_categories",
        emptyList(),
    )

    val animeFilterExcludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        "pref_filter_upcoming_anime_excluded_categories",
        emptyList(),
    )
}
