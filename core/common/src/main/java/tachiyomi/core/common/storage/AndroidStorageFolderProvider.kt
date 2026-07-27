package tachiyomi.core.common.storage

import android.content.Context
import androidx.core.net.toUri
import java.io.File

class AndroidStorageFolderProvider(
    private val context: Context,
) : FolderProvider {

    override fun directory(): File {
        return context.getExternalFilesDir(null) ?: context.filesDir
    }

    override fun path(): String {
        return directory().toUri().toString()
    }
}
