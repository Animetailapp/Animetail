package eu.kanade.tachiyomi.data.torrent.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import aniyomi.core.common.torrent.ProxyMode
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import xyz.secozzi.torrserver.TorrServer
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

class TorrentServerService : Service() {
    private val serviceScope = CoroutineScope(EmptyCoroutineContext)
    private val appGraph by lazy { applicationContext.appGraph }
    private val networkPreferences get() = appGraph.networkPreferences
    private val torrentPreferences get() = appGraph.torrentPreferences
    private val torrentServerUtils get() = appGraph.torrentServerUtils
    private val api get() = appGraph.torrentServerApi

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.action != null) {
                when (it.action) {
                    ACTION_START -> {
                        startServer()
                        notification(applicationContext)
                        return START_STICKY
                    }

                    ACTION_STOP -> {
                        stopServer()
                        return START_NOT_STICKY
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        serviceScope.launch {
            val preferredPortStr = torrentPreferences.torrServerPort().get()
            val preferredPort = preferredPortStr.toIntOrNull() ?: 8090
            if (api.getPort() == 0) {
                api.setPort(preferredPort)
            }
            if (api.echo() == "") {
                if (networkPreferences.verboseLogging().get()) {
                    TorrServer.registerLogCallback()
                }

                val proxyMode = torrentPreferences.torrServerProxyMode().get()
                val port = TorrServer.startServer(
                    port = preferredPortStr,
                    path = filesDir.absolutePath,
                    proxyMode = proxyMode.value,
                    proxyUrl = if (proxyMode == ProxyMode.None) "" else torrentPreferences.torrServerProxyUrl().get(),
                )
                if (port != -1) {
                    api.setPort(port)
                    wait(this@TorrentServerService, 20)
                    torrentServerUtils.setTrackersList()
                }
            }
        }
    }

    private fun stopServer() {
        serviceScope.launch {
            TorrServer.stopServer()
            applicationContext.cancelNotification(Notifications.ID_TORRENT_SERVER)
            stopSelf()
        }
    }

    private fun notification(context: Context) {
        // fuck android 14
        val startAgainIntent = PendingIntent.getService(
            applicationContext,
            0,
            Intent(applicationContext, TorrentServerService::class.java).apply {
                action = ACTION_START
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exitPendingIntent =
            PendingIntent.getService(
                applicationContext,
                0,
                Intent(applicationContext, TorrentServerService::class.java).apply {
                    action = ACTION_STOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder = context.notificationBuilder(Notifications.CHANNEL_TORRENT_SERVER) {
            setSmallIcon(R.drawable.ic_ani)
            setContentText(stringResource(AYMR.strings.torrentserver_is_running))
            setContentTitle(stringResource(MR.strings.app_name))
            setAutoCancel(false)
            setOngoing(true)
            setDeleteIntent(startAgainIntent)
            setUsesChronometer(true)
            addAction(
                R.drawable.ic_close_24dp,
                "Stop",
                exitPendingIntent,
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifications.ID_TORRENT_SERVER,
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(Notifications.ID_TORRENT_SERVER, builder.build())
        }
    }

    companion object {
        const val ACTION_START = "start_torrent_server"
        const val ACTION_STOP = "stop_torrent_server"

        suspend fun start(context: Context) {
            try {
                val appContext = context.applicationContext
                val appGraph = appContext.appGraph
                val api = appGraph.torrentServerApi
                val preferredPort = appGraph.torrentPreferences.torrServerPort().get().toIntOrNull() ?: 8090
                if (api.getPort() == 0) {
                    api.setPort(preferredPort)
                }
                val intent =
                    Intent(appContext, TorrentServerService::class.java).apply {
                        action = ACTION_START
                    }
                appContext.startService(intent)
                wait(appContext, 20)
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "Failed to start torrent service" }
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                val appContext = context.applicationContext
                val intent =
                    Intent(appContext, TorrentServerService::class.java).apply {
                        action = ACTION_STOP
                    }
                appContext.startService(intent)
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "Failed to stop torrent service" }
                e.printStackTrace()
            }
        }

        suspend fun wait(context: Context, timeout: Int = -1): Boolean {
            var count = 0
            val timeoutCount = if (timeout < 0) 20 else timeout
            val appGraph = context.applicationContext.appGraph
            val api = appGraph.torrentServerApi

            // Ensure API has a port set before checking echo
            if (api.getPort() == 0) {
                val preferredPort = appGraph.torrentPreferences.torrServerPort().get().toIntOrNull() ?: 8090
                api.setPort(preferredPort)
            }

            while (api.echo() == "") {
                delay(1.seconds)
                count++
                if (count > timeoutCount) {
                    return false
                }
            }
            return true
        }
    }
}
