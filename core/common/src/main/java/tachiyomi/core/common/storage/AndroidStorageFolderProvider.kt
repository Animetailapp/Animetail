package tachiyomi.core.common.storage

import android.content.Context
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
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
