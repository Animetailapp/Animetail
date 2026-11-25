package eu.kanade.tachiyomi.util.storage

import com.hippo.unifile.UniFile
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Renames this file, falling back to copy and delete for providers that reject renames
 * (e.g. HarmonyOS), whether they return false or throw. Returns the renamed file, or null on
 * failure, in which case the original is left in place.
 */
fun UniFile.renameToOrCopy(displayName: String): UniFile? {
    val renamed = try {
        renameTo(displayName)
    } catch (e: Exception) {
        logcat(LogPriority.WARN, e) { "Failed to rename $name to $displayName, copying instead" }
        false
    }
    if (renamed) return this
    val parent = parentFile ?: return null
    if (parent.findFile(displayName) != null) return null

    val copy = try {
        copyInto(parent, displayName)
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e) { "Failed to copy $name to $displayName" }
        null
    }
    if (copy == null) {
        parent.findFile(displayName)?.delete()
        return null
    }
    delete()
    return copy
}

private fun UniFile.copyInto(parent: UniFile, name: String): UniFile? {
    if (isDirectory) {
        val dir = parent.createDirectory(name) ?: return null
        for (child in listFiles().orEmpty()) {
            child.copyInto(dir, child.name ?: return null) ?: return null
        }
        return dir
    }
    val target = parent.createFile(name) ?: return null
    openInputStream().use { input ->
        target.openOutputStream().use { output -> input.copyTo(output) }
    }
    return target
}
