package eu.kanade.tachiyomi.util.storage

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.hippo.unifile.UniFile
import java.io.File

fun String.toFFmpegString(context: Context): String {
    return File(this).getUriCompat(context).toFFmpegString(context)
}

fun Uri.toFFmpegString(context: Context, mode: String = "r"): String {
    return if (this.scheme == "content") {
        FFmpegKitConfig.getSafParameter(context, this, mode)
    } else {
        this.path!!
    }.replace("\"", "\\\"")
}

fun UniFile.toFFmpegString(context: Context? = null, mode: String = "r"): String {
    return if (context != null && this.uri.scheme == "content") {
        FFmpegKitConfig.getSafParameter(context, this.uri, mode)
    } else {
        this.filePath!!
    }.replace("\"", "\\\"")
}
