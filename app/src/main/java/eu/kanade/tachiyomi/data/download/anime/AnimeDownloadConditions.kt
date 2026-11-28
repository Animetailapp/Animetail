package eu.kanade.tachiyomi.data.download.anime

import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED

/**
 * When queued downloads may run. "Only on Wi-Fi" applies to every download; the auto-download
 * restrictions apply on top of it to the episodes the library update queued (see
 * [eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload.automatic]). A download that can't
 * run stays in the queue until the device meets its requirements.
 */
data class AnimeDownloadConditions(
    val onlyOnWifi: Boolean,
    /** [DEVICE_NETWORK_NOT_METERED] and/or [DEVICE_CHARGING]. */
    val autoDownloadRestrictions: Set<String>,
) {

    data class DeviceState(
        val isOnline: Boolean,
        val isWifi: Boolean,
        val isUnmetered: Boolean,
        val isCharging: Boolean,
    )

    /** Why a download is held back. */
    enum class Hold { NO_NETWORK, NO_WIFI, METERED_NETWORK, NOT_CHARGING }

    /** What the device must offer for a download to run, besides a network connection. */
    data class Requirements(val wifi: Boolean, val unmetered: Boolean, val charging: Boolean)

    /** The holds on manual and on automatic downloads in one device state. */
    data class Holds(val manual: Hold?, val automatic: Hold?) {
        fun of(automatic: Boolean): Hold? = if (automatic) this.automatic else manual

        companion object {
            val NONE = Holds(manual = null, automatic = null)
        }
    }

    fun requirements(automatic: Boolean) = Requirements(
        wifi = onlyOnWifi,
        unmetered = automatic && DEVICE_NETWORK_NOT_METERED in autoDownloadRestrictions,
        charging = automatic && DEVICE_CHARGING in autoDownloadRestrictions,
    )

    /** Why a download can't run in [state], or null if it can. */
    fun hold(automatic: Boolean, state: DeviceState): Hold? {
        val requirements = requirements(automatic)
        return when {
            !state.isOnline -> Hold.NO_NETWORK
            requirements.wifi && !state.isWifi -> Hold.NO_WIFI
            requirements.unmetered && !state.isUnmetered -> Hold.METERED_NETWORK
            requirements.charging && !state.isCharging -> Hold.NOT_CHARGING
            else -> null
        }
    }

    fun holds(state: DeviceState) = Holds(manual = hold(false, state), automatic = hold(true, state))

    /**
     * What the device must offer for the first of the pending downloads to run, given whether each
     * is automatic, or null when nothing is pending. A manual download needs no more than an
     * automatic one, so any manual download decides it.
     */
    fun resumeRequirements(pendingAutomatic: Collection<Boolean>): Requirements? {
        if (pendingAutomatic.isEmpty()) return null
        return requirements(automatic = pendingAutomatic.all { it })
    }
}
