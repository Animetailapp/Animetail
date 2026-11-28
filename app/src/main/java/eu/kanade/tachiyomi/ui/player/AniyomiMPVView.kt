/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.SurfaceHolder
import animetail.feature.mpvfiles.MpvConfig
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.player.controls.components.panels.toColorHexString
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitleAssOverride
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.player.utils.LanguageTags
import eu.kanade.tachiyomi.ui.player.utils.applyVideoAspect
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.KeyMapping
import `is`.xyz.mpv.MPV
import logcat.LogPriority
import logcat.logcat
import mihon.app.di.appGraph

class AniyomiMPVView(context: Context, attributes: AttributeSet?) : BaseMPVView(context, attributes) {

    private val appGraph by lazy { context.appGraph }
    private val playerPreferences get() = appGraph.playerPreferences
    private val decoderPreferences get() = appGraph.decoderPreferences
    private val subtitlePreferences get() = appGraph.subtitlePreferences
    private val audioPreferences get() = appGraph.audioPreferences
    private val advancedPreferences get() = appGraph.advancedPlayerPreferences
    private val networkPreferences get() = appGraph.networkPreferences

    var isExiting = false

    private val optionNameRegex = Regex("""^(?:--)?([\w-]+)(?:=|$)""", RegexOption.MULTILINE)
    private val mpvOptionNames = optionNameRegex.findAll(advancedPreferences.mpvConf().get()).map {
        it.groupValues[1].removePrefix("no-")
    }.toSet()

    // Set mpv option unless it's present in mpv.conf
    private fun setSafeOptionString(name: String, value: String) {
        if (name in mpvOptionNames) return
        mpv?.setOptionString(name, value)
    }

    fun resetTrackSelection() {
        setSafeOptionString("sid", "no")
        setSafeOptionString("aid", "auto")
    }

    /**
     * Returns the video aspect ratio. Rotation is taken into account.
     */
    fun getVideoOutAspect(): Double? {
        return mpv?.getPropertyDouble("video-params/aspect")?.let {
            if (it < 0.001) return 0.0
            if ((mpv?.getPropertyInt("video-params/rotate") ?: 0) % 180 == 90) 1.0 / it else it
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.surfaceChanged(holder, format, width, height)
        // The surface is resized by folding, rotating and the tabletop split; Stretch has to follow it.
        val mpv = mpv ?: return
        if (width <= 0 || height <= 0) return
        mpv.applyVideoAspect(playerPreferences.aspectState().get(), width / height.toDouble())
        // mpv only renders the resized surface with the next frame, so a paused video would keep showing a
        // stale, wrongly sized buffer. Re-decoding the current frame is how mpv itself refreshes paused video.
        if (mpv.getPropertyBoolean("pause") == true && mpv.getPropertyDouble("time-pos") != null) {
            mpv.command("seek", "0", "relative+exact")
        }
    }

    private val videoOutput: String
        get() = if (decoderPreferences.gpuNext().get()) "gpu-next" else "gpu"

    fun init(mpvInst: MPV) {
        this.mpv = mpvInst
        setVo(videoOutput)
        mpv?.setPropertyBoolean("pause", true)
        setSafeOptionString("profile", "fast")
        mpv?.setOptionString("hwdec", if (decoderPreferences.tryHWDecoding().get()) "auto" else "no")

        if (decoderPreferences.useYUV420P().get()) {
            mpv?.setOptionString("vf", "format=yuv420p")
        }
        mpv?.setOptionString("msg-level", "all=" + if (networkPreferences.verboseLogging.get()) "v" else "warn")

        mpv?.setPropertyBoolean("input-default-bindings", true)

        mpv?.setOptionString("idle", "yes")
        mpv?.setOptionString("ytdl", "no")
        setSafeOptionString("tls-verify", "no")
        setSafeOptionString("tls-ca-file", "${context.filesDir.path}/${PlayerActivity.MPV_DIR}/cacert.pem")

        // Select audio during initial preload to avoid demux_lavf refresh seeks on HLS streams
        resetTrackSelection()

        // Limit demuxer cache since the defaults are too high for mobile devices
        val cacheMegs = playerPreferences.playerBufferMegabytes().get().takeIf { it > 0 }
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        setSafeOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        setSafeOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")

        // A connection that drops or stalls in the middle of an HLS segment otherwise loses the rest of
        // that segment, so playback jumps ahead a few seconds (or freezes until mpv's 60s timeout).
        // FFmpeg's HLS demuxer doesn't pass `reconnect` on to segment requests, but mpv propagates
        // demuxer-lavf-o to them, so the segment resumes from where it was cut with a Range request.
        setSafeOptionString("network-timeout", "10")
        setSafeOptionString(
            "demuxer-lavf-o",
            "reconnect=1,reconnect_on_network_error=1,reconnect_delay_max=5",
        )

        // By default ffmpeg analyzes up to 5 MB or 5 seconds before playing. On HLS that downloads
        // the first segments of the video even when playback resumes much later in it.
        if (playerPreferences.fastVideoStart().get()) {
            setSafeOptionString("demuxer-lavf-probesize", "${FAST_START_PROBE_BYTES}")
            setSafeOptionString("demuxer-lavf-analyzeduration", "$FAST_START_ANALYZE_SECONDS")
        }

        val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        screenshotDir.mkdirs()
        mpv?.setOptionString("screenshot-directory", screenshotDir.path)

        VideoFilters.entries.forEach {
            mpv?.setOptionString(it.mpvProperty, it.preference(decoderPreferences).get().toString())
        }

        mpv?.setOptionString("speed", playerPreferences.playerSpeed().get().toString())
        // workaround for <https://github.com/mpv-player/mpv/issues/14651>
        setSafeOptionString("vd-lavc-film-grain", "cpu")

        postInitOptions()
        setupSubtitlesOptions()
        setupAudioOptions()
        observeProperties()
    }

    /**
     * Shows [mpvInst], which [init] already set up for the view of an activity that was recreated.
     * Setting it up again would restart its video output while no surface is attached, which
     * leaves the video black.
     */
    fun attach(mpvInst: MPV) {
        // Before mpv is set, this only picks the video output to start once the surface is attached
        setVo(videoOutput)
        this.mpv = mpvInst
    }

    fun observeProperties() {
        for ((name, format) in observedProps) mpv?.observeProperty(name, format)
    }

    fun postInitOptions() {
        when (decoderPreferences.videoDebanding().get()) {
            Debanding.None -> {}
            Debanding.CPU -> mpv?.setOptionString("vf", "gradfun=radius=12")
            Debanding.GPU -> mpv?.setOptionString("deband", "yes")
        }

        advancedPreferences.playerStatisticsPage().get().let {
            if (it != 0) {
                mpv?.command("script-binding", "stats/display-stats-toggle")
                mpv?.command("script-binding", "stats/display-page-$it")
            }
        }
    }

    @Suppress("DEPRECATION")
    fun onKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE || KeyEvent.isModifierKey(event.keyCode)) {
            return false
        }

        var mapped = KeyMapping[event.keyCode]
        if (mapped == null) {
            // Fallback to produced glyph
            if (!event.isPrintingKey) {
                if (event.repeatCount == 0) {
                    logcat(LogPriority.DEBUG) { "Unmapped non-printable key ${event.keyCode}" }
                }
                return false
            }

            val ch = event.unicodeChar
            if (ch.and(KeyCharacterMap.COMBINING_ACCENT) != 0) {
                return false // dead key
            }
            mapped = ch.toChar().toString()
        }

        if (event.repeatCount > 0) {
            return true // eat event but ignore it, mpv has its own key repeat
        }

        val mod: MutableList<String> = mutableListOf()
        event.isShiftPressed && mod.add("shift")
        event.isCtrlPressed && mod.add("ctrl")
        event.isAltPressed && mod.add("alt")
        event.isMetaPressed && mod.add("meta")

        val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
        mod.add(mapped)
        mpv?.command(action, mod.joinToString("+"))

        return true
    }

    private val observedProps = mapOf(
        "pause" to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "paused-for-cache" to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "seeking" to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "eof-reached" to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "time-pos" to MPV.mpvFormat.MPV_FORMAT_INT64,
        "duration" to MPV.mpvFormat.MPV_FORMAT_INT64,
        "demuxer-cache-time" to MPV.mpvFormat.MPV_FORMAT_INT64,
        "volume" to MPV.mpvFormat.MPV_FORMAT_INT64,
        "volume-max" to MPV.mpvFormat.MPV_FORMAT_INT64,
        "chapter-list" to MPV.mpvFormat.MPV_FORMAT_NONE,
        "track-list" to MPV.mpvFormat.MPV_FORMAT_NONE,
        "video-params/aspect" to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "speed" to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "aid" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "sid" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "secondary-sid" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "hwdec" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "hwdec-current" to MPV.mpvFormat.MPV_FORMAT_STRING,

        "user-data/aniyomi/show_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/toggle_ui" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/show_panel" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/software_keyboard" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/set_button_title" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/reset_button_title" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/toggle_button" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/switch_episode" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/pause" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_by" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_to" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_by_with_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_to_with_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/launch_int_picker" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/show_seek_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
    )

    private fun setupAudioOptions() {
        mpv?.setOptionString(
            "alang",
            LanguageTags.toMpvAlang(audioPreferences.preferredAudioLanguages().get().split(",")),
        )
        mpv?.setOptionString("audio-delay", (audioPreferences.audioDelay().get() / 1000.0).toString())
        mpv?.setOptionString("audio-pitch-correction", audioPreferences.enablePitchCorrection().get().toString())
        mpv?.setOptionString("volume-max", (audioPreferences.volumeBoostCap().get() + 100).toString())
    }

    private fun setupSubtitlesOptions() {
        mpv?.setOptionString("sub-delay", (subtitlePreferences.subtitlesDelay().get() / 1000.0).toString())
        mpv?.setOptionString("sub-speed", subtitlePreferences.subtitlesSpeed().get().toString())
        mpv?.setOptionString(
            "secondary-sub-delay",
            (subtitlePreferences.subtitlesSecondaryDelay().get() / 1000.0).toString(),
        )

        val fontsDir = context.filesDir.resolve(PlayerActivity.MPV_DIR).resolve(MpvConfig.MPV_FONTS_DIR).path
        setSafeOptionString("sub-fonts-dir", fontsDir)
        setSafeOptionString("osd-fonts-dir", fontsDir)

        val font = subtitlePreferences.subtitleFont().get().let {
            if (it == "Sans Serif") "sans-serif" else it
        }
        mpv?.setOptionString("sub-font", font)
        subtitlePreferences.overrideSubsASS().get().let {
            mpv?.setOptionString("sub-ass-override", it.value)
            if (it != SubtitleAssOverride.No) {
                mpv?.setOptionString("sub-ass-justify", "yes")
            }
        }
        mpv?.setOptionString("sub-font-size", subtitlePreferences.subtitleFontSize().get().toString())
        mpv?.setOptionString("sub-bold", if (subtitlePreferences.boldSubtitles().get()) "yes" else "no")
        mpv?.setOptionString("sub-italic", if (subtitlePreferences.italicSubtitles().get()) "yes" else "no")
        mpv?.setOptionString("sub-justify", subtitlePreferences.subtitleJustification().get().value)
        mpv?.setOptionString("sub-color", subtitlePreferences.textColorSubtitles().get().toColorHexString())
        mpv?.setOptionString(
            "sub-back-color",
            subtitlePreferences.backgroundColorSubtitles().get().toColorHexString(),
        )
        mpv?.setOptionString("sub-outline-color", subtitlePreferences.borderColorSubtitles().get().toColorHexString())
        mpv?.setOptionString("sub-outline-size", subtitlePreferences.subtitleBorderSize().get().toString())
        mpv?.setOptionString("sub-border-style", subtitlePreferences.borderStyleSubtitles().get().value)
        mpv?.setOptionString("sub-shadow-offset", subtitlePreferences.shadowOffsetSubtitles().get().toString())
        mpv?.setOptionString("sub-pos", subtitlePreferences.subtitlePos().get().toString())
        mpv?.setOptionString("sub-scale", subtitlePreferences.subtitleFontScale().get().toString())
    }
}

// Enough to read the codec setup of a couple of seconds of 1080p video.
private const val FAST_START_PROBE_BYTES = 2 * 1024 * 1024
private const val FAST_START_ANALYZE_SECONDS = 2
