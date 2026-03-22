package eu.kanade.tachiyomi.network

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class NetworkPreferences(
    preferenceStore: PreferenceStore,
    verboseLoggingDefault: Boolean = false,
) {

    val verboseLogging: Preference<Boolean> = preferenceStore.getBoolean("verbose_logging", verboseLoggingDefault)

    // TLMR -->
    val enableFlareSolverr: Preference<Boolean> = preferenceStore.getBoolean("enable_flare_solverr", false)

    val flareSolverrUrl: Preference<String> = preferenceStore.getString("flare_solverr_url", "http://localhost:8191/v1")
    // <-- TLMR

    val dohProvider: Preference<Int> = preferenceStore.getInt("doh_provider", -1)

    val dohCustomUrl: Preference<String> = preferenceStore.getString("doh_custom_url", "")

    val dohCustomBootstrap: Preference<String> = preferenceStore.getString("doh_custom_bootstrap", "")

    val defaultUserAgent: Preference<String> = preferenceStore.getString(
        "default_user_agent",
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
    )

    fun verboseLogging() = verboseLogging

    fun enableFlareSolverr() = enableFlareSolverr

    fun flareSolverrUrl() = flareSolverrUrl

    fun dohProvider() = dohProvider

    fun dohCustomUrl() = dohCustomUrl

    fun dohCustomBootstrap() = dohCustomBootstrap

    fun defaultUserAgent() = defaultUserAgent
}
