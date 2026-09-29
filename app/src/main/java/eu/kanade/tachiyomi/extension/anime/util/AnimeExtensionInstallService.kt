package eu.kanade.tachiyomi.extension.anime.util

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.installer.InstallerAnime
import eu.kanade.tachiyomi.extension.anime.installer.PackageInstallerInstallerAnime
import eu.kanade.tachiyomi.extension.anime.installer.ShizukuInstallerAnime
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionInstaller.Companion.EXTRA_DOWNLOAD_ID
import eu.kanade.tachiyomi.util.system.getSerializableExtraCompat
import eu.kanade.tachiyomi.util.system.notificationBuilder
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionInstallService : Service() {

    private var installer: InstallerAnime? = null

    override fun onCreate() {
        val notification = notificationBuilder(Notifications.CHANNEL_EXTENSIONS_UPDATE) {
            setSmallIcon(R.drawable.ic_ani)
            setAutoCancel(false)
            setOngoing(true)
            setShowWhen(false)
            setContentTitle(stringResource(MR.strings.ext_install_service_notif))
            setProgress(100, 100, true)
        }.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_EXTENSION_INSTALLER,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(Notifications.ID_EXTENSION_INSTALLER, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.data
        val id = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1)?.takeIf { it != -1L }
        val installerUsed = intent?.getStringExtra(EXTRA_INSTALLER)?.let { name ->
            runCatching { BasePreferences.ExtensionInstaller.valueOf(name) }.getOrNull()
        } ?: intent?.getSerializableExtraCompat<BasePreferences.ExtensionInstaller>(EXTRA_INSTALLER)
        if (uri == null || id == null || installerUsed == null) {
            id?.let {
                runCatching { Injekt.get<AnimeExtensionManager>().updateInstallStep(it, InstallStep.Error) }
            }
            stopSelf()
            return START_NOT_STICKY
        }

        if (installer == null) {
            installer = when (installerUsed) {
                BasePreferences.ExtensionInstaller.PACKAGEINSTALLER -> PackageInstallerInstallerAnime(
                    this,
                )

                BasePreferences.ExtensionInstaller.SHIZUKU -> ShizukuInstallerAnime(this)

                else -> {
                    logcat(LogPriority.ERROR) { "Not implemented for installer $installerUsed" }
                    Injekt.get<AnimeExtensionManager>().updateInstallStep(id, InstallStep.Error)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        installer!!.addToQueue(id, uri, intent.getStringExtra(EXTRA_PKG_NAME))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        installer?.onDestroy()
        installer = null
    }

    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_INSTALLER = "EXTRA_INSTALLER"
        private const val EXTRA_PKG_NAME = "EXTRA_PKG_NAME"

        fun getIntent(
            context: Context,
            downloadId: Long,
            uri: Uri,
            installer: BasePreferences.ExtensionInstaller,
            pkgName: String? = null,
        ): Intent {
            return Intent(context, AnimeExtensionInstallService::class.java)
                .setDataAndType(uri, AnimeExtensionInstaller.APK_MIME)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                .putExtra(EXTRA_INSTALLER, installer.name)
                .putExtra(EXTRA_PKG_NAME, pkgName)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
