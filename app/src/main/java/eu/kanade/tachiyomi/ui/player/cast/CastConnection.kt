package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import androidx.mediarouter.app.MediaRouteChooserDialog
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener

/**
 * Runs [onConnected] with the current cast session, first letting the user pick a device when
 * none is connected. Nothing happens if the picker is dismissed or the connection fails.
 *
 * @param context an activity context, the device picker is a dialog.
 */
fun castWhenConnected(context: Context, onConnected: (CastSession) -> Unit) {
    CastProgressSync.start(context)
    val castContext = CastContext.getSharedInstance(context)
    val sessionManager = castContext.sessionManager
    sessionManager.currentCastSession?.takeIf { it.isConnected }?.let {
        onConnected(it)
        return
    }

    val listener = SessionStartListener(sessionManager, onConnected)
    sessionManager.addSessionManagerListener(listener, CastSession::class.java)
    MediaRouteChooserDialog(context).apply {
        routeSelector = castContext.mergedSelector ?: return
        // Picking a device dismisses the dialog; only backing out of it cancels.
        setOnCancelListener { listener.remove() }
    }.show()
}

private class SessionStartListener(
    private val sessionManager: SessionManager,
    private val onConnected: (CastSession) -> Unit,
) : SessionManagerListener<CastSession> {

    fun remove() = sessionManager.removeSessionManagerListener(this, CastSession::class.java)

    override fun onSessionStarted(session: CastSession, sessionId: String) {
        remove()
        onConnected(session)
    }

    override fun onSessionStartFailed(session: CastSession, error: Int) = remove()

    override fun onSessionStarting(session: CastSession) = Unit
    override fun onSessionEnding(session: CastSession) = Unit
    override fun onSessionEnded(session: CastSession, error: Int) = Unit
    override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = Unit
    override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
    override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
}
