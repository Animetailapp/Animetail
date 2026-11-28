package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import org.json.JSONObject
import tachiyomi.domain.items.episode.model.EpisodeUpdate

/**
 * Saves the progress of the episode playing on a cast device into the library, whichever screen
 * sent it and even after the player is closed, and marks it seen past the player's progress
 * setting. Episodes are recognized by the id put in the media with [episodeData].
 */
object CastProgressSync {

    private const val EPISODE_ID = "episodeId"
    private const val INTERVAL_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private var trackedClient: RemoteMediaClient? = null
    private var progressListener: RemoteMediaClient.ProgressListener? = null

    /** Custom data that identifies [episodeId] in the media sent to the device. */
    fun episodeData(episodeId: Long?): JSONObject? = episodeId?.let { JSONObject().put(EPISODE_ID, it) }

    fun episodeId(client: RemoteMediaClient): Long? =
        client.mediaInfo?.customData?.optLong(EPISODE_ID, -1L)?.takeIf { it > 0 }

    /** Starts following cast sessions; later calls do nothing. */
    fun start(context: Context) {
        if (started) return
        val appContext = context.applicationContext
        // Cast needs Google Play services, which some devices lack.
        val sessionManager = runCatching { CastContext.getSharedInstance(appContext).sessionManager }.getOrNull()
            ?: return
        started = true
        sessionManager.addSessionManagerListener(SessionListener(appContext), CastSession::class.java)
        sessionManager.currentCastSession?.let { track(appContext, it) }
    }

    private fun track(context: Context, session: CastSession) {
        val client = session.remoteMediaClient ?: return
        if (client == trackedClient) return
        progressListener?.let { trackedClient?.removeProgressListener(it) }
        val listener = RemoteMediaClient.ProgressListener { progressMs, durationMs ->
            if (client.isPlaying && durationMs > 0) {
                val episodeId = episodeId(client) ?: return@ProgressListener
                scope.launch { save(context, episodeId, progressMs, durationMs) }
            }
        }
        client.addProgressListener(listener, INTERVAL_MS)
        trackedClient = client
        progressListener = listener
    }

    private suspend fun save(context: Context, episodeId: Long, progressMs: Long, durationMs: Long) {
        val graph = context.appGraph
        if (graph.basePreferences.incognitoMode.get()) return
        val seen = progressMs >= durationMs * graph.playerPreferences.progressPreference().get()
        graph.updateEpisode.await(
            EpisodeUpdate(
                id = episodeId,
                seen = true.takeIf { seen },
                lastSecondSeen = progressMs,
                totalSeconds = durationMs,
            ),
        )
    }

    private class SessionListener(private val context: Context) : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = track(context, session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = track(context, session)
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }
}
