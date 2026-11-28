package eu.kanade.tachiyomi.ui.player.cast.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import eu.kanade.tachiyomi.ui.player.cast.ExpandedControlsActivity
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import kotlin.math.roundToInt

/**
 * Small button docked to a side of the screen anywhere in the app while a cast device is
 * connected, whether or not it plays something, that opens the cast controls. It can be dragged
 * along the edge or to the other side, where it docks again, so it never stays over a button.
 */
@Composable
fun CastBubble(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember { context.appGraph.playerPreferences }
    val castEnabled by preferences.enableCast().collectAsState()
    val bubbleEnabled by preferences.showCastBubble().collectAsState()
    if (!castEnabled || !bubbleEnabled) return

    val connected = rememberCastConnected(context)
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(LocalDensity.current) { 2 * EDGE_MARGIN.roundToPx() }
    // Docked at the vertical center of the end edge by default, so offsets are relative to it.
    val startEdge = -(parentSize.width - bubbleSize.width - margin).toFloat().coerceAtLeast(0f)
    val verticalRoom = ((parentSize.height - bubbleSize.height) / 2 - margin).toFloat().coerceAtLeast(0f)

    // Restored as a side and a relative height, so it stays on screen after a rotation.
    LaunchedEffect(startEdge, verticalRoom) {
        if (bubbleSize == IntSize.Zero) return@LaunchedEffect
        offsetX.snapTo(if (preferences.castBubbleAtStart().get()) startEdge else 0f)
        offsetY.snapTo(preferences.castBubbleVerticalBias().get().coerceIn(-1f, 1f) * verticalRoom)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { parentSize = it },
    ) {
        AnimatedVisibility(
            visible = connected,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .onSizeChanged { bubbleSize = it }
                .pointerInput(startEdge, verticalRoom) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + amount.x).coerceIn(startEdge, 0f))
                                offsetY.snapTo((offsetY.value + amount.y).coerceIn(-verticalRoom, verticalRoom))
                            }
                        },
                        onDragEnd = {
                            val atStart = offsetX.value < startEdge / 2
                            preferences.castBubbleAtStart().set(atStart)
                            if (verticalRoom >
                                0f
                            ) {
                                preferences.castBubbleVerticalBias().set(offsetY.value / verticalRoom)
                            }
                            scope.launch { offsetX.animateTo(if (atStart) startEdge else 0f) }
                        },
                    )
                },
        ) {
            SmallFloatingActionButton(
                onClick = { context.startActivity(Intent(context, ExpandedControlsActivity::class.java)) },
                modifier = Modifier
                    .size(48.dp)
                    .alpha(0.85f),
            ) {
                Icon(
                    imageVector = Icons.Default.CastConnected,
                    contentDescription = stringResource(TLMR.strings.pref_show_cast_bubble),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

private val EDGE_MARGIN = 8.dp

@Composable
private fun rememberCastConnected(context: Context): Boolean {
    // Cast needs Google Play services, which some devices lack.
    val sessionManager = remember { runCatching { CastContext.getSharedInstance(context).sessionManager }.getOrNull() }
        ?: return false
    var connected by remember { mutableStateOf(sessionManager.currentCastSession?.isConnected == true) }

    DisposableEffect(sessionManager) {
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                connected = true
            }
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                connected = true
            }
            override fun onSessionEnded(session: CastSession, error: Int) {
                connected = false
            }
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                connected = false
            }
            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        }
        sessionManager.addSessionManagerListener(listener, CastSession::class.java)
        connected = sessionManager.currentCastSession?.isConnected == true
        onDispose { sessionManager.removeSessionManagerListener(listener, CastSession::class.java) }
    }
    return connected
}
