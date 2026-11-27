package eu.kanade.tachiyomi.ui.player.utils

/**
 * Detects a video that never starts playing: handed to mpv, but no playback progress for
 * [timeoutSeconds] seconds of not being paused by the user. A dead stream often doesn't end with
 * an error; mpv keeps waiting for data and the player sits at 00:00.
 *
 * Driven by the player: [start] when a video is loaded, [onPlaybackRestart] and [onPosition]
 * from mpv, and [tick] once a second.
 */
class PlaybackStallDetector(private val timeoutSeconds: Int) {

    private var active = false
    private var stalledSeconds = 0
    private var restartPosition: Float? = null

    /** A new video was handed to mpv. */
    @Synchronized
    fun start() {
        active = true
        stalledSeconds = 0
        restartPosition = null
    }

    @Synchronized
    fun stop() {
        active = false
    }

    /** mpv is ready to play from [position] (after loading the file or a seek). */
    @Synchronized
    fun onPlaybackRestart(position: Float) {
        if (active && restartPosition == null) restartPosition = position
    }

    @Synchronized
    fun onPosition(position: Float) {
        val from = restartPosition ?: return
        if (position >= from + PROGRESS_SECONDS) active = false
    }

    /**
     * Counts one second, unless the user paused playback (a paused video doesn't advance).
     *
     * @return true once, when the video counts as stalled.
     */
    @Synchronized
    fun tick(pausedByUser: Boolean): Boolean {
        if (!active || pausedByUser) return false
        stalledSeconds++
        if (stalledSeconds < timeoutSeconds) return false
        active = false
        return true
    }

    private companion object {
        /** Playback that advanced this far has really started. */
        const val PROGRESS_SECONDS = 1f
    }
}
