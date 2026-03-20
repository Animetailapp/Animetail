package tachiyomi.domain.storage.service

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import tachiyomi.core.common.storage.FolderProvider
import java.io.File

class StorageManager(
    private val context: Context,
    storagePreferences: StoragePreferences,
    private val folderProvider: FolderProvider,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val storageDirPreference = storagePreferences.baseStorageDirectory
    private var baseDir: UniFile? = getBaseDir(storageDirPreference.get())

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        storageDirPreference.changes()
            .drop(1)
            .distinctUntilChanged()
            .onEach { uri ->
                baseDir = getBaseDir(uri)
                baseDir?.let { parent ->
                    parent.createDirectory(AUTOMATIC_BACKUPS_PATH)
                    parent.createDirectory(LOCAL_SOURCE_PATH)
                    parent.createDirectory(LOCAL_ANIMESOURCE_PATH)
                    parent.createDirectory(DOWNLOADS_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                    parent.createDirectory(MPV_CONFIG_PATH)?.let { mpvDir ->
                        mpvDir.createDirectory(FONTS_PATH)
                        mpvDir.createDirectory(SCRIPTS_PATH)
                        mpvDir.createDirectory(SCRIPT_OPTS_PATH)
                        mpvDir.createDirectory(SHADERS_PATH)
                    }
                }
                _changes.send(Unit)
            }
            .launchIn(scope)
    }

    private fun getBaseDir(uri: String): UniFile? {
        migrateLegacyFileUriIfNeeded(uri)?.let { return it }
        return UniFile.fromUri(context, uri.toUri())
            .takeIf { it?.exists() == true }
    }

    private fun migrateLegacyFileUriIfNeeded(uri: String): UniFile? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }

        val parsedUri = uri.toUri()
        if (parsedUri.scheme != "file") {
            return null
        }

        val fallbackDir = folderProvider.directory().apply { mkdirs() }
        val fallbackUri = folderProvider.path()
        if (fallbackUri != uri) {
            storageDirPreference.set(fallbackUri)
        }

        return UniFile.fromFile(fallbackDir)
            ?.takeIf { it.exists() || fallbackDir.exists() }
    }

    fun getAutomaticBackupsDirectory(): UniFile? {
        return baseDir?.createDirectory(AUTOMATIC_BACKUPS_PATH)
    }

    fun getDownloadsDirectory(): UniFile? {
        return baseDir?.createDirectory(DOWNLOADS_PATH)
    }

    fun getLocalMangaSourceDirectory(): UniFile? {
        return getLocalSourceDirectory(LOCAL_SOURCE_PATH)
    }

    fun getLocalAnimeSourceDirectory(): UniFile? {
        return getLocalSourceDirectory(LOCAL_ANIMESOURCE_PATH)
    }

    fun getFontsDirectory(): UniFile? {
        return getMPVConfigDirectory()?.createDirectory(FONTS_PATH)
    }

    fun getScriptsDirectory(): UniFile? {
        return getMPVConfigDirectory()?.createDirectory(SCRIPTS_PATH)
    }

    fun getScriptOptsDirectory(): UniFile? {
        return getMPVConfigDirectory()?.createDirectory(SCRIPT_OPTS_PATH)
    }

    fun getShadersDirectory(): UniFile? {
        return getMPVConfigDirectory()?.createDirectory(SHADERS_PATH)
    }

    fun getMPVConfigDirectory(): UniFile? {
        return baseDir?.createDirectory(MPV_CONFIG_PATH)
    }

    private fun getLocalSourceDirectory(path: String): UniFile? {
        return baseDir?.createDirectory(path) ?: getLegacyLocalSourceDirectory(path)
    }

    private fun getLegacyLocalSourceDirectory(path: String): UniFile? {
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        val legacyBaseDir = File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                appName,
        )
        val legacyDir = File(legacyBaseDir, path)
        return UniFile.fromFile(legacyDir)
            ?.takeIf { legacyDir.exists() && legacyDir.isDirectory }
    }
}

private const val AUTOMATIC_BACKUPS_PATH = "autobackup"
private const val DOWNLOADS_PATH = "downloads"
private const val LOCAL_SOURCE_PATH = "local"
private const val LOCAL_ANIMESOURCE_PATH = "localanime"
private const val MPV_CONFIG_PATH = "mpv-config"
private const val FONTS_PATH = "fonts"
const val SCRIPTS_PATH = "scripts"
const val SCRIPT_OPTS_PATH = "script-opts"
private const val SHADERS_PATH = "shaders"
