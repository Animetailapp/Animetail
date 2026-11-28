package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadConditions.DeviceState
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadConditions.Hold
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadConditions.Requirements
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED

class AnimeDownloadConditionsTest {

    data class HoldCase(
        val description: String,
        val conditions: AnimeDownloadConditions,
        val state: DeviceState,
        val expectedManual: Hold?,
        val expectedAutomatic: Hold?,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("holdCases")
    fun `auto-download restrictions hold back only the episodes the library update queued`(case: HoldCase) {
        val holds = case.conditions.holds(case.state)
        assertEquals(case.expectedManual, holds.manual, "manual")
        assertEquals(case.expectedAutomatic, holds.automatic, "automatic")
    }

    data class ResumeCase(
        val description: String,
        val conditions: AnimeDownloadConditions,
        val pendingAutomatic: List<Boolean>,
        val expected: Requirements?,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest
    @MethodSource("resumeCases")
    fun `held downloads resume as soon as the least demanding one can run`(case: ResumeCase) {
        assertEquals(case.expected, case.conditions.resumeRequirements(case.pendingAutomatic))
    }

    companion object {
        private val noRestrictions = AnimeDownloadConditions(onlyOnWifi = false, autoDownloadRestrictions = emptySet())
        private val wifiOnly = AnimeDownloadConditions(onlyOnWifi = true, autoDownloadRestrictions = emptySet())
        private val charging = AnimeDownloadConditions(
            onlyOnWifi = false,
            autoDownloadRestrictions = setOf(DEVICE_CHARGING),
        )
        private val all = AnimeDownloadConditions(
            onlyOnWifi = true,
            autoDownloadRestrictions = setOf(DEVICE_NETWORK_NOT_METERED, DEVICE_CHARGING),
        )

        private val homeWifiCharging =
            DeviceState(isOnline = true, isWifi = true, isUnmetered = true, isCharging = true)
        private val homeWifiOnBattery = homeWifiCharging.copy(isCharging = false)
        private val hotspot = homeWifiOnBattery.copy(isUnmetered = false)
        private val mobileData = DeviceState(isOnline = true, isWifi = false, isUnmetered = false, isCharging = true)
        private val offline = DeviceState(isOnline = false, isWifi = false, isUnmetered = false, isCharging = true)

        @JvmStatic
        fun holdCases() = listOf(
            HoldCase(
                "no restrictions: nothing is held, even on mobile data",
                noRestrictions,
                mobileData,
                expectedManual = null,
                expectedAutomatic = null,
            ),
            HoldCase(
                "offline holds everything, whatever the settings",
                noRestrictions,
                offline,
                expectedManual = Hold.NO_NETWORK,
                expectedAutomatic = Hold.NO_NETWORK,
            ),
            HoldCase(
                "the existing Only on Wi-Fi setting still applies to manual downloads too",
                wifiOnly,
                mobileData,
                expectedManual = Hold.NO_WIFI,
                expectedAutomatic = Hold.NO_WIFI,
            ),
            HoldCase(
                "charging restriction: an episode the user queued downloads on battery, a new one waits",
                charging,
                homeWifiOnBattery,
                expectedManual = null,
                expectedAutomatic = Hold.NOT_CHARGING,
            ),
            HoldCase(
                "a phone hotspot is Wi-Fi but metered: only new episodes wait",
                all,
                hotspot,
                expectedManual = null,
                expectedAutomatic = Hold.METERED_NETWORK,
            ),
            HoldCase(
                "network holds are reported before charging, the more useful thing to fix",
                all,
                hotspot.copy(isCharging = false),
                expectedManual = null,
                expectedAutomatic = Hold.METERED_NETWORK,
            ),
            HoldCase(
                "all restrictions met: nothing is held",
                all,
                homeWifiCharging,
                expectedManual = null,
                expectedAutomatic = null,
            ),
        )

        @JvmStatic
        fun resumeCases() = listOf(
            ResumeCase("nothing pending: nothing to resume", all, emptyList(), expected = null),
            ResumeCase(
                "only new episodes pending: wait for every restriction",
                all,
                listOf(true, true),
                expected = Requirements(wifi = true, unmetered = true, charging = true),
            ),
            ResumeCase(
                "one manual download among them: resume on Wi-Fi alone, the new episodes stay held",
                all,
                listOf(true, false, true),
                expected = Requirements(wifi = true, unmetered = false, charging = false),
            ),
            ResumeCase(
                "no restrictions: any connection resumes",
                noRestrictions,
                listOf(true),
                expected = Requirements(wifi = false, unmetered = false, charging = false),
            ),
        )
    }
}
