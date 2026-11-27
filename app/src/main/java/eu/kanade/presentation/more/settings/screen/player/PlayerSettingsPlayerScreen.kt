package eu.kanade.presentation.more.settings.screen.player

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.AMNIS
import eu.kanade.tachiyomi.ui.player.JUST_PLAYER
import eu.kanade.tachiyomi.ui.player.MPV_KT
import eu.kanade.tachiyomi.ui.player.MPV_KT_PREVIEW
import eu.kanade.tachiyomi.ui.player.MPV_PLAYER
import eu.kanade.tachiyomi.ui.player.MPV_REMOTE
import eu.kanade.tachiyomi.ui.player.MX_PLAYER
import eu.kanade.tachiyomi.ui.player.MX_PLAYER_FREE
import eu.kanade.tachiyomi.ui.player.MX_PLAYER_PRO
import eu.kanade.tachiyomi.ui.player.NEXT_PLAYER
import eu.kanade.tachiyomi.ui.player.PlayerOrientation
import eu.kanade.tachiyomi.ui.player.VLC_PLAYER
import eu.kanade.tachiyomi.ui.player.WEB_VIDEO_CASTER
import eu.kanade.tachiyomi.ui.player.X_PLAYER
import eu.kanade.tachiyomi.ui.player.loader.HosterCache
import eu.kanade.tachiyomi.ui.player.loader.HosterPriority
import eu.kanade.tachiyomi.ui.player.settings.HOSTER_CACHE_DURATIONS
import eu.kanade.tachiyomi.ui.player.settings.HOSTER_LIST_CACHE_DURATIONS
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.hosterCacheDurationLabel
import eu.kanade.tachiyomi.ui.player.utils.ChapterSkipMode
import eu.kanade.tachiyomi.util.LocalHttpServerHolder
import eu.kanade.tachiyomi.util.LocalHttpServerService
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.Serializable
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.NumberFormat

@Serializable
data object PlayerSettingsPlayerScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_player_internal

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val appGraph = remember(context) { context.appGraph }
        val playerPreferences = remember { appGraph.playerPreferences }
        val basePreferences = remember { appGraph.basePreferences }
        val deviceSupportsPip = basePreferences.deviceHasPip
        val localHttpServerHolder = remember { appGraph.localHttpServerHolder }

        return listOfNotNull(
            Preference.PreferenceItem.ListPreference(
                preference = playerPreferences.progressPreference(),
                entries = persistentMapOf(
                    1.00F to stringResource(AYMR.strings.pref_progress_100),
                    0.95F to stringResource(AYMR.strings.pref_progress_95),
                    0.90F to stringResource(AYMR.strings.pref_progress_90),
                    0.85F to stringResource(AYMR.strings.pref_progress_85),
                    0.80F to stringResource(AYMR.strings.pref_progress_80),
                    0.75F to stringResource(AYMR.strings.pref_progress_75),
                    0.70F to stringResource(AYMR.strings.pref_progress_70),
                ),
                title = stringResource(AYMR.strings.pref_progress_mark_as_seen),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = playerPreferences.preserveWatchingPosition(),
                title = stringResource(AYMR.strings.pref_preserve_watching_position),
            ),
            getCastGroup(playerPreferences = playerPreferences),
            Preference.PreferenceItem.ListPreference(
                preference = playerPreferences.defaultPlayerOrientationType(),
                entries = PlayerOrientation.entries.associateWith {
                    stringResource(it.titleRes)
                }.toPersistentMap(),
                title = stringResource(AYMR.strings.pref_category_player_orientation),
            ),
            getControlsGroup(playerPreferences = playerPreferences),
            getHosterGroup(playerPreferences = playerPreferences),
            getDisplayGroup(playerPreferences = playerPreferences),
            getIntroSkipGroup(playerPreferences = playerPreferences),
            if (deviceSupportsPip) getPipGroup(playerPreferences = playerPreferences) else null,
            getExternalPlayerGroup(
                playerPreferences = playerPreferences,
                basePreferences = basePreferences,
            ),
            geCastServerGroup(context, localHttpServerHolder),
        )
    }

    @Composable
    private fun getControlsGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val allowGestures = playerPreferences.allowGestures()
        val showLoading = playerPreferences.showLoadingCircle()
        val showChapter = playerPreferences.showCurrentChapter()
        val rememberPlayerBrightness = playerPreferences.rememberPlayerBrightness()
        val rememberPlayerVolume = playerPreferences.rememberPlayerVolume()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_controls),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = allowGestures,
                    title = stringResource(AYMR.strings.pref_controls_allow_gestures_in_panels),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showLoading,
                    title = stringResource(AYMR.strings.pref_controls_show_loading),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showChapter,
                    title = stringResource(AYMR.strings.pref_controls_show_chapter_indicator),
                    subtitle = stringResource(AYMR.strings.pref_controls_show_chapter_indicator_info),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rememberPlayerBrightness,
                    title = stringResource(AYMR.strings.pref_remember_brightness),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rememberPlayerVolume,
                    title = stringResource(AYMR.strings.pref_remember_volume),
                ),
            ),
        )
    }

    @Composable
    private fun getHosterGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val showFailure = playerPreferences.showFailedHosters()
        val showEmpty = playerPreferences.showEmptyHosters()
        val switchOnFailure = playerPreferences.switchOnFailure()
        val priorityPref = playerPreferences.hosterPriority()
        val priority by priorityPref.collectAsState()
        val context = LocalContext.current
        val cacheMinutes = playerPreferences.hosterCacheMinutes()
        val disabledPref = playerPreferences.disabledHosters()
        val disabled by disabledPref.collectAsState()
        // Hosters are named by each extension, so only the ones saved from the player are known.
        val savedHosters = remember(priority, disabled) {
            (HosterPriority.parseNames(priority) + HosterPriority.parseNames(disabled)).distinct()
        }
        val disabledHosters = remember(disabled) { HosterPriority.parseNames(disabled).toSet() }
        var showPriorityDialog by remember { mutableStateOf(false) }

        if (showPriorityDialog) {
            HosterPriorityDialog(
                hosterNames = savedHosters,
                disabledNames = disabledHosters,
                onConfirm = { order, disabledNames ->
                    priorityPref.set(order.joinToString(", "))
                    disabledPref.set(disabledNames.joinToString(", "))
                    showPriorityDialog = false
                },
                onDismissRequest = { showPriorityDialog = false },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_hosters),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = showFailure,
                    title = stringResource(AYMR.strings.pref_hosters_show_failure),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showEmpty,
                    title = stringResource(AYMR.strings.pref_hosters_show_empty),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = switchOnFailure,
                    title = stringResource(AYMR.strings.pref_hosters_switch_on_error),
                    subtitle = stringResource(AYMR.strings.pref_hosters_switch_on_error_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.pref_hosters_priority),
                    subtitle = hosterPrioritySummary(savedHosters, disabledHosters),
                    enabled = savedHosters.isNotEmpty(),
                    onClick = { showPriorityDialog = true },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.pref_hosters_priority_reset),
                    enabled = savedHosters.isNotEmpty(),
                    onClick = {
                        priorityPref.set("")
                        disabledPref.set("")
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = playerPreferences.hosterListCacheMinutes(),
                    entries = HOSTER_LIST_CACHE_DURATIONS
                        .associateWith { context.hosterCacheDurationLabel(it) }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.pref_hoster_list_cache_duration),
                    subtitle = stringResource(AYMR.strings.pref_hoster_list_cache_duration_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = cacheMinutes,
                    entries = HOSTER_CACHE_DURATIONS
                        .associateWith { context.hosterCacheDurationLabel(it) }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.pref_hoster_cache_duration),
                    subtitle = stringResource(AYMR.strings.pref_hoster_cache_duration_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.pref_hoster_cache_clear),
                    onClick = {
                        HosterCache.shared(context).clear()
                        context.toast(AYMR.strings.pref_hoster_cache_cleared)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun hosterPrioritySummary(hosters: List<String>, disabled: Set<String>): String {
        if (hosters.isEmpty()) return stringResource(AYMR.strings.pref_hosters_priority_empty)
        val enabled = hosters.filterNot { it in disabled }.joinToString(", ")
        if (disabled.isEmpty()) return enabled
        return stringResource(AYMR.strings.pref_hosters_priority_summary, enabled, disabled.size)
    }

    @Composable
    private fun getDisplayGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val fullScreen = playerPreferences.playerFullscreen()
        val hideControls = playerPreferences.hideControls()
        val displayVol = playerPreferences.displayVolPer()
        val showSystemBar = playerPreferences.showSystemStatusBar()
        val reduceMotion = playerPreferences.reduceMotion()
        val hideTime = playerPreferences.playerTimeToDisappear()

        val panelOpacityPref = playerPreferences.panelOpacity()
        val panelOpacity by panelOpacityPref.collectAsState()
        val numberFormat = remember { NumberFormat.getPercentInstance() }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = fullScreen,
                    title = stringResource(AYMR.strings.pref_player_fullscreen),
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = hideControls,
                    title = stringResource(AYMR.strings.pref_player_hide_controls),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = displayVol,
                    title = stringResource(AYMR.strings.pref_controls_display_volume_percentage),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showSystemBar,
                    title = stringResource(AYMR.strings.pref_show_system_bar),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = reduceMotion,
                    title = stringResource(AYMR.strings.pref_reduce_motion),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = hideTime,
                    entries = listOf(500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000).associateWith {
                        stringResource(AYMR.strings.pref_player_time_to_disappear_summary, it)
                    }.toPersistentMap(),
                    title = stringResource(AYMR.strings.pref_player_time_to_disappear),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = panelOpacity,
                    valueRange = 0..100,
                    title = stringResource(AYMR.strings.pref_panel_opacity),
                    subtitle = numberFormat.format(panelOpacity / 100f),
                    onValueChanged = {
                        panelOpacityPref.set(it)
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getIntroSkipGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val openingSkipMode = playerPreferences.openingSkipMode()
        val endingSkipMode = playerPreferences.endingSkipMode()
        val openingMode by openingSkipMode.collectAsState()
        val endingMode by endingSkipMode.collectAsState()
        val isIntroSkipEnabled = openingMode != ChapterSkipMode.Off || endingMode != ChapterSkipMode.Off
        val usesCountdown = openingMode == ChapterSkipMode.Countdown ||
            endingMode == ChapterSkipMode.Countdown ||
            endingMode == ChapterSkipMode.PlayNext

        val waitingTimeAniSkip = playerPreferences.waitingTimeIntroSkip()

        // AniSkip
        val enableAniSkip = playerPreferences.aniSkipEnabled()
        val disableAniSkipChapters = playerPreferences.disableAniSkipOnChapters()
        val isAniSkipEnabled by enableAniSkip.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_intro_skip),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = openingSkipMode,
                    entries = ChapterSkipMode.OpeningModes.associateWith {
                        stringResource(it.titleRes)
                    }.toPersistentMap(),
                    title = stringResource(TLMR.strings.pref_opening_skip_mode),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = endingSkipMode,
                    entries = ChapterSkipMode.EndingModes.associateWith {
                        stringResource(it.titleRes)
                    }.toPersistentMap(),
                    title = stringResource(TLMR.strings.pref_ending_skip_mode),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = waitingTimeAniSkip,
                    entries = persistentMapOf(
                        5 to stringResource(AYMR.strings.pref_waiting_time_aniskip_5),
                        6 to stringResource(AYMR.strings.pref_waiting_time_aniskip_6),
                        7 to stringResource(AYMR.strings.pref_waiting_time_aniskip_7),
                        8 to stringResource(AYMR.strings.pref_waiting_time_aniskip_8),
                        9 to stringResource(AYMR.strings.pref_waiting_time_aniskip_9),
                        10 to stringResource(AYMR.strings.pref_waiting_time_aniskip_10),
                    ),
                    title = stringResource(TLMR.strings.pref_chapter_skip_countdown_time),
                    enabled = usesCountdown,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = enableAniSkip,
                    title = stringResource(AYMR.strings.pref_enable_aniskip),
                    enabled = isIntroSkipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = disableAniSkipChapters,
                    title = stringResource(AYMR.strings.pref_disable_aniskip_chapter),
                    enabled = isIntroSkipEnabled && isAniSkipEnabled,
                ),
                Preference.PreferenceItem.InfoPreference(
                    title = stringResource(AYMR.strings.pref_category_player_aniskip_info),
                    enabled = isIntroSkipEnabled,
                ),
            ),
        )
    }

    @Composable
    private fun getPipGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val enablePip = playerPreferences.enablePip()
        val pipEpisodeToasts = playerPreferences.pipEpisodeToasts()
        val pipOnExit = playerPreferences.pipOnExit()
        val pipReplaceWithPrevious = playerPreferences.pipReplaceWithPrevious()

        val isPipEnabled by enablePip.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_pip),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = enablePip,
                    title = stringResource(AYMR.strings.pref_enable_pip),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = pipEpisodeToasts,
                    title = stringResource(AYMR.strings.pref_pip_episode_toasts),
                    enabled = isPipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = pipOnExit,
                    title = stringResource(AYMR.strings.pref_pip_on_exit),
                    enabled = isPipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = pipReplaceWithPrevious,
                    title = stringResource(AYMR.strings.pref_pip_replace_with_previous),
                    enabled = isPipEnabled,
                ),
            ),
        )
    }

    // habilita o desabilita el uso de cast que habilitarlo o deshabilitarlo sea con switch
    @Composable
    private fun getCastGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val enableCast = playerPreferences.enableCast()
        return Preference.PreferenceGroup(
            title = stringResource(TLMR.strings.pref_category_cast),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = enableCast,
                    title = stringResource(TLMR.strings.pref_enable_cast),
                ),
            ),
        )
    }

    @Composable
    private fun getExternalPlayerGroup(
        playerPreferences: PlayerPreferences,
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val alwaysUseExternalPlayer = playerPreferences.alwaysUseExternalPlayer()
        val externalPlayerPreference = playerPreferences.externalPlayerPreference()

        val pm = basePreferences.context.packageManager
        val installedPackages = pm.getInstalledPackages(0)
        val supportedPlayers = installedPackages.filter { it.packageName in externalPlayers }

        val packageNames = supportedPlayers.map { it.packageName }
        val packageNamesReadable = supportedPlayers
            .map { pm.getApplicationLabel(it.applicationInfo!!).toString() }

        val packageNamesMap: Map<String, String> =
            packageNames.zip(packageNamesReadable)
                .toMap()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_external_player),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = alwaysUseExternalPlayer,
                    title = stringResource(AYMR.strings.pref_always_use_external_player),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = externalPlayerPreference,
                    entries = (mapOf("" to "None") + packageNamesMap).toPersistentMap(),
                    title = stringResource(AYMR.strings.pref_external_player_preference),
                ),
            ),
        )
    }

    @Composable
    private fun geCastServerGroup(
        context: Context,
        localHttpServerHolder: LocalHttpServerHolder,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(TLMR.strings.pref_category_castserver),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.EditTextPreference(
                    preference = localHttpServerHolder.port(),
                    title = stringResource(TLMR.strings.pref_cast_server_port),
                    onValueChanged = {
                        try {
                            Integer.parseInt(it)
                            LocalHttpServerService.stop(context)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    },
                ),
            ),
        )
    }
}

val externalPlayers = listOf(
    MPV_PLAYER,
    MX_PLAYER,
    MX_PLAYER_FREE,
    MX_PLAYER_PRO,
    VLC_PLAYER,
    MPV_KT,
    MPV_KT_PREVIEW,
    MPV_REMOTE,
    JUST_PLAYER,
    NEXT_PLAYER,
    X_PLAYER,
    WEB_VIDEO_CASTER,
    AMNIS,
)
