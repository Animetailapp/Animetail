package eu.kanade.tachiyomi.data.download

internal fun <Directory, Entry> associateSourceDirectories(
    directories: List<Directory>,
    sourceIdsByDirName: Map<String, List<Long>>,
    directoryName: (Directory) -> String,
    createEntry: (Directory) -> Entry,
): Map<Long, Entry> = directories.flatMap { directory ->
    val entry = createEntry(directory)
    sourceIdsByDirName[directoryName(directory).lowercase()].orEmpty().map { it to entry }
}.toMap()

internal fun <Entry> restoreSharedDirectories(
    sourceDirectories: Map<Long, Entry>,
    directoryKey: (Entry) -> String?,
): Map<Long, Entry> {
    val directoriesByKey = mutableMapOf<String, Entry>()
    return sourceDirectories.mapValues { (_, directory) ->
        directoryKey(directory)?.let { key ->
            directoriesByKey.getOrPut(key) { directory }
        } ?: directory
    }
}
