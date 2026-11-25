package eu.kanade.tachiyomi.ui.player.settings

import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.tail.TLMR

@Inject
@SingleIn(AppScope::class)
class AudioPreferences(
    private val preferenceStore: PreferenceStore,
) {
    /**
     * What a show without its own audio language setting plays: its original language, or the
     * [preferredAudioLanguages] list. The list is also the fallback when the original language is
     * unknown or not available.
     */
    fun defaultAudioLanguage() = preferenceStore.getEnum("pref_audio_default_language", DefaultAudioLanguage.Original)

    fun preferredAudioLanguages() = preferenceStore.getString("pref_audio_lang", "")
    fun enablePitchCorrection() = preferenceStore.getBoolean("pref_audio_pitch_correction", true)
    fun audioChannels() = preferenceStore.getEnum("pref_audio_config", AudioChannels.AutoSafe)
    fun volumeBoostCap() = preferenceStore.getInt("pref_audio_volume_boost_cap", 30)

    // Non-preferences

    fun audioDelay() = preferenceStore.getInt("pref_audio_delay", 0)
}

enum class DefaultAudioLanguage(val titleRes: StringResource) {
    Original(TLMR.strings.pref_player_audio_default_language_original),
    PreferredLanguages(TLMR.strings.pref_player_audio_default_language_preferred),
}

enum class AudioChannels(val titleRes: StringResource, val property: String, val value: String) {
    Auto(AYMR.strings.pref_player_audio_channels_auto, "audio-channels", "auto-safe"),
    AutoSafe(AYMR.strings.pref_player_audio_channels_auto_safe, "audio-channels", "auto"),
    Mono(AYMR.strings.pref_player_audio_channels_mono, "audio-channels", "mono"),
    Stereo(AYMR.strings.pref_player_audio_channels_stereo, "audio-channels", "stereo"),
    ReverseStereo(AYMR.strings.pref_player_audio_channels_reverse_stereo, "af", "pan=[stereo|c0=c1|c1=c0]"),
}
