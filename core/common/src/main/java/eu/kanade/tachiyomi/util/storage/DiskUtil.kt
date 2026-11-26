package eu.kanade.tachiyomi.util.storage

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.lang.Hash
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

object DiskUtil {

    /**
     * Returns the root folders of all the available external storages.
     */
    fun getExternalStorages(context: Context): List<File> {
        return ContextCompat.getExternalFilesDirs(context, null)
            .filterNotNull()
            .mapNotNull {
                val file = File(it.absolutePath.substringBefore("/Android/"))
                val state = Environment.getExternalStorageState(file)
                if (state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY) {
                    file
                } else {
                    null
                }
            }
    }

    fun hashKeyForDisk(key: String): String {
        return Hash.md5(key)
    }

    fun getDirectorySize(f: File): Long {
        var size: Long = 0
        if (f.isDirectory) {
            for (file in f.listFiles().orEmpty()) {
                size += getDirectorySize(file)
            }
        } else {
            size = f.length()
        }
        return size
    }

    /**
     * Gets the total space for the disk that a file path points to, in bytes.
     */
    fun getTotalStorageSpace(file: File): Long {
        return try {
            val stat = StatFs(file.absolutePath)
            stat.blockCountLong * stat.blockSizeLong
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Gets the available space for the disk that a file path points to, in bytes.
     */
    fun getAvailableStorageSpace(file: File): Long {
        return try {
            val stat = StatFs(file.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Gets the available space for the disk that a file or document points to, in bytes, or -1
     * when it can't be found out.
     */
    fun getAvailableStorageSpace(f: UniFile): Long {
        return getStorageVolume(f)?.let { getAvailableStorageSpace(it) } ?: -1L
    }

    /**
     * Returns a folder on the same disk as [f]: its own path for a file, or the root of its storage
     * volume for a document of the external storage provider, which is what folders picked with
     * the system picker are. Null for documents of other providers.
     *
     * [StatFs] needs a path on the device, and the path of a document URI
     * (e.g. /tree/primary:Animetail/document/...) isn't one.
     */
    private fun getStorageVolume(f: UniFile): File? {
        val uri = f.uri
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let(::File)

            ContentResolver.SCHEME_CONTENT -> {
                if (uri.authority != EXTERNAL_STORAGE_PROVIDER_AUTHORITY) return null
                val documentId = try {
                    if (DocumentsContract.isTreeUri(uri)) {
                        DocumentsContract.getTreeDocumentId(uri)
                    } else {
                        DocumentsContract.getDocumentId(uri)
                    }
                } catch (_: IllegalArgumentException) {
                    return null
                }
                getVolumePath(documentId, Environment.getExternalStorageDirectory().absolutePath)?.let(::File)
            }

            else -> null
        }
    }

    /**
     * Returns the root of the storage volume of a document of the external storage provider from
     * its id, e.g. "primary:Animetail/downloads" (the shared storage, [primaryPath]) or
     * "1A2B-3C4D:Animetail" (an SD card or USB drive, mounted in /storage).
     */
    internal fun getVolumePath(documentId: String, primaryPath: String): String? {
        val volume = documentId.substringBefore(':', missingDelimiterValue = "")
        return when {
            volume.isEmpty() -> null

            // "home" is the Documents folder of the shared storage
            volume == "primary" || volume == "home" -> primaryPath

            volume.all { it.isLetterOrDigit() || it == '-' } -> "/storage/$volume"

            else -> null
        }
    }

    /**
     * Don't display downloaded chapters in gallery apps creating `.nomedia`.
     */
    fun createNoMediaFile(dir: UniFile?, context: Context?) {
        if (dir != null && dir.exists()) {
            val nomedia = dir.findFile(NOMEDIA_FILE)
            if (nomedia == null) {
                dir.createFile(NOMEDIA_FILE)
                context?.let { scanMedia(it, dir.uri) }
            }
        }
    }

    /**
     * Scans the given file so that it can be shown in gallery apps, for example.
     */
    fun scanMedia(context: Context, uri: Uri) {
        MediaScannerConnection.scanFile(context, arrayOf(uri.path), null, null)
    }

    /**
     * Transform a filename fragment to make it safe to use on almost
     * all commonly used filesystems. You can pass an entire filename,
     * or just part of one, in case you want a specific part of a long
     * filename to be truncated, rather than the end of it.
     *
     * Characters that are potentially unsafe for some filesystems are
     * replaced with underscores. This includes the standard ones from
     * https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file
     * but does allow any other valid Unicode code point.
     *
     * Excessively long filenames are truncated, by default to 240
     * bytes. Note that the truncation is based on bytes rather than
     * characters (code points), because this is what is relevant to
     * filesystem restrictions in most cases.
     *
     * Leading periods are stripped, to avoid the creation of hidden
     * files by default. If a hidden file is desired, a period can be
     * prepended to the return value from this function.
     *
     * If the optional argument disallowNonAscii is set to true,
     * then ANYTHING outside the ASCII range is replaced not with underscores,
     * but with its hexadecimal encoding. This is to make it so that distinct
     * non-English titles of things remain distinct, since not all
     * places where this function is used also take care of
     * disambiguation.
     *
     * We could instead replace only non-ASCII characters known to
     * be problematic, but so far nobody with a non-Unicode-compliant
     * device has been able to provide either directions to reproduce
     * their issue nor any documentation or tests that would allow us
     * to determine which characters are problems and which are not.
     */
    fun buildValidFilename(
        origName: String,
        maxBytes: Int = MAX_FILE_NAME_BYTES,
        disallowNonAscii: Boolean = false,
    ): String {
        val name = origName.trim('.', ' ')
        if (name.isEmpty()) {
            return "(invalid)"
        }
        val sb = StringBuilder(name.length)
        name.forEach { c ->
            if (disallowNonAscii && c >= 0x80.toChar()) {
                sb.append(
                    c.toString().toByteArray(Charsets.UTF_8).toHexString(
                        HexFormat {
                            upperCase = false
                        },
                    ),
                )
            } else if (isValidFatFilenameChar(c)) {
                sb.append(c)
            } else {
                sb.append('_')
            }
        }
        return truncateToLength(sb.toString(), maxBytes)
    }

    /**
     * Truncate a string to a maximum length, while maintaining valid Unicode encoding.
     */
    fun truncateToLength(s: String, maxBytes: Int): String {
        val charset = Charsets.UTF_8
        val decoder = charset.newDecoder()
        val sba = s.toByteArray(charset)
        if (sba.size <= maxBytes) {
            return s
        }
        // Ensure truncation by having byte buffer = maxBytes
        val bb = ByteBuffer.wrap(sba, 0, maxBytes)
        val cb = CharBuffer.allocate(maxBytes)
        // Ignore an incomplete character
        decoder.onMalformedInput(CodingErrorAction.IGNORE)
        decoder.decode(bb, cb, true)
        decoder.flush(cb)
        return String(cb.array(), 0, cb.position())
    }

    /**
     * Returns true if the given character is a valid filename character, false otherwise.
     */
    private fun isValidFatFilenameChar(c: Char): Boolean {
        if (0x00.toChar() <= c && c <= 0x1f.toChar()) {
            return false
        }
        return when (c) {
            '"', '*', '/', ':', '<', '>', '?', '\\', '|', 0x7f.toChar() -> false
            else -> true
        }
    }

    private const val EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents"

    const val NOMEDIA_FILE = ".nomedia"

    // Safe theoretical max filename size is 255 bytes and 1 char = 2-4 bytes (UTF-8).
    // To allow for writing to ext4 through a FUSE layer in the future, also subtract 15
    // reserved characters.
    const val MAX_FILE_NAME_BYTES = 240
}
