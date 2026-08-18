package eu.kanade.tachiyomi.util

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class LocalHttpServerHolder(
    private val preferenceStore: PreferenceStore,
) {
    fun port() = preferenceStore.getString("pref_cast_server_port", "8181")
}
