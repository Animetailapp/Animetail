package eu.kanade.tachiyomi.extension.manga.installer

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.util.system.getParcelableExtraCompat
import eu.kanade.tachiyomi.util.system.getUriSize
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class PackageInstallerInstallerManga(private val service: Service) : InstallerManga(service) {

    private val packageInstaller = service.packageManager.packageInstaller

    private val packageActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val userAction = intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT)
                    if (userAction == null) {
                        logcat(LogPriority.ERROR) { "Fatal error for $intent" }
                        continueQueue(InstallStep.Error)
                        return
                    }
                    try {
                        userAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ActivityOptions.makeBasic().apply {
                                pendingIntentBackgroundActivityStartMode =
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            }.toBundle()
                        } else {
                            null
                        }
                        service.startActivity(userAction, options)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to start user action activity" }
                        continueQueue(InstallStep.Error)
                    }
                }

                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    continueQueue(InstallStep.Idle)
                }

                PackageInstaller.STATUS_SUCCESS -> continueQueue(InstallStep.Installed)

                else -> {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    val otherPackage = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)
                    logcat(LogPriority.ERROR) {
                        "PackageInstaller failed: status=$status, message=$message, otherPackage=$otherPackage"
                    }
                    continueQueue(InstallStep.Error)
                }
            }
        }
    }

    @Volatile
    private var activeSession: Pair<Entry, Int>? = null

    // Always ready
    override var ready = true

    override fun processEntry(entry: Entry) {
        super.processEntry(entry)
        activeSession = null
        var session: PackageInstaller.Session? = null
        try {
            // Clean up any orphaned sessions from previous failed installs
            cleanUpOrphanedSessions()

            val installParams = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            )
            val pkgName = entry.pkgName
            if (!pkgName.isNullOrEmpty()) {
                installParams.setAppPackageName(pkgName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !pkgName.isNullOrEmpty()) {
                installParams.setRequireUserAction(
                    PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED,
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                installParams.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
            }
            val fileSize = service.getUriSize(entry.uri) ?: throw IllegalStateException("Could not get URI size")
            installParams.setSize(fileSize)

            val sessionId = packageInstaller.createSession(installParams)
            activeSession = entry to sessionId

            session = packageInstaller.openSession(sessionId)

            // Write APK data into the session
            service.contentResolver.openInputStream(entry.uri)?.use { inputStream ->
                session.openWrite(entry.downloadId.toString(), 0, fileSize).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    session.fsync(outputStream)
                }
            } ?: throw IllegalStateException("Could not open input stream for ${entry.uri}")

            val intentSender = PendingIntent.getBroadcast(
                service,
                sessionId,
                Intent(INSTALL_ACTION).setPackage(service.packageName),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                },
            ).intentSender

            @SuppressLint("RequestInstallPackagesPolicy")
            session.commit(intentSender)

            // Delete the cached APK only after commit succeeds
            try {
                service.contentResolver.delete(entry.uri, null, null)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to delete cached APK ${entry.uri}" }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to install extension ${entry.downloadId} ${entry.uri}" }
            session?.close()
            activeSession?.let { (_, sessionId) ->
                try {
                    packageInstaller.abandonSession(sessionId)
                } catch (_: Exception) {
                    // Session may already be finalized
                }
            }
            continueQueue(InstallStep.Error)
        }
    }

    /**
     * Clean up any orphaned sessions from previous installs that may have been
     * left behind due to crashes or unexpected service restarts.
     */
    private fun cleanUpOrphanedSessions() {
        try {
            packageInstaller.mySessions.forEach { sessionInfo ->
                try {
                    packageInstaller.abandonSession(sessionInfo.sessionId)
                    logcat(LogPriority.DEBUG) { "Cleaned up orphaned session ${sessionInfo.sessionId}" }
                } catch (_: Exception) {
                    // Ignore — session may already be finalized
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to clean up orphaned sessions" }
        }
    }

    override fun cancelEntry(entry: Entry): Boolean {
        activeSession?.let { (activeEntry, sessionId) ->
            if (activeEntry == entry) {
                return try {
                    packageInstaller.abandonSession(sessionId)
                    false
                } catch (_: SecurityException) {
                    // Highly likely the session has succeeded
                    true
                }
            }
        }
        return true
    }

    override fun onDestroy() {
        service.unregisterReceiver(packageActionReceiver)
        super.onDestroy()
    }

    init {
        ContextCompat.registerReceiver(
            service,
            packageActionReceiver,
            IntentFilter(INSTALL_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}

private const val INSTALL_ACTION = "${BuildConfig.APPLICATION_ID}.PACKAGE_INSTALLER_MANGA.INSTALL_ACTION"
