package eu.kanade.tachiyomi.ui.player

import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import logcat.LogPriority

// Takes the view model directly: mpv calls back on its own thread, and the activity's lazy
// view model property isn't safe to initialize from there.
class PlayerObserver(val activity: PlayerActivity, private val viewModel: PlayerViewModel) :
    MPV.EventObserver,
    MPV.LogObserver {

    override fun eventProperty(property: String) {
        activity.runOnUiThread { activity.onObserverEvent(property) }
    }

    override fun eventProperty(property: String, value: Long) {
        activity.runOnUiThread { activity.onObserverEvent(property, value) }
    }

    override fun eventProperty(property: String, value: Boolean) {
        activity.runOnUiThread { activity.onObserverEvent(property, value) }
    }

    override fun eventProperty(property: String, value: String) {
        activity.runOnUiThread { activity.onObserverEvent(property, value) }
    }

    override fun eventProperty(property: String, value: Double) {
        activity.runOnUiThread { activity.onObserverEvent(property, value) }
    }

    @Suppress("unused")
    override fun eventProperty(property: String, value: MPVNode) {
        activity.runOnUiThread { activity.onObserverEvent(property, value) }
    }

    override fun event(eventId: Int, data: MPVNode) {
        // Read on mpv's thread: by the time the UI thread runs, a newer video may have been loaded.
        val playbackId = viewModel.playbackId
        activity.runOnUiThread { activity.event(eventId, data, playbackId) }
    }

    var httpError: String? = null

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (level == MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR) {
            if (text.startsWith(TRACK_LOAD_FAILURE)) {
                val url = text.removePrefix(TRACK_LOAD_FAILURE).substringBeforeLast(".")
                activity.onTrackLoadedFailure(url)
            }
        }

        val logPriority = when (level) {
            MPV.mpvLogLevel.MPV_LOG_LEVEL_FATAL, MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR -> LogPriority.ERROR
            MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN -> LogPriority.WARN
            MPV.mpvLogLevel.MPV_LOG_LEVEL_INFO -> LogPriority.INFO
            else -> LogPriority.VERBOSE
        }
        if (text.contains("HTTP error")) httpError = text.removePrefix("http: ")
        logcat.logcat("mpv/$prefix", logPriority) { text }
    }

    companion object {
        const val TRACK_LOAD_FAILURE = "Can not open external file "
    }
}
