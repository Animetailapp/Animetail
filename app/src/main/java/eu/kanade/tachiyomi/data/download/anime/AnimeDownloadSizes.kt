package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.util.size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Sizes of the downloaded episodes, by the anime directories of [AnimeDownloadCache].
 *
 * Measuring a download means listing its files and reading their lengths, which is slow through
 * the storage access framework, so sizes are measured in the background, kept in a file and only
 * measured again for new downloads or on [refresh] with `force`.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeDownloadSizes(
    private val context: Context,
    private val cache: AnimeDownloadCache,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val diskCacheFile: File
        get() = File(context.cacheDir, "dl_anime_size_cache_v1")

    /** Size in bytes by [key]. */
    private val sizes = mutableMapOf<String, Long>()
    private val sizesMutex = Mutex()
    private var loadedFromDisk = false

    private var scanJob: Job? = null
    private var forceQueued = false

    val state: StateFlow<State>
        field = MutableStateFlow(State())

    /**
     * Measures the downloads not measured yet, or all of them when [force]. Does nothing new while
     * a measurement is running, except remembering to measure everything again after it.
     */
    fun refresh(force: Boolean = false) {
        synchronized(this) {
            if (scanJob?.isActive == true) {
                forceQueued = forceQueued || force
                return
            }
            // Shown right away: listing the downloads can take a while before measuring starts
            state.update { it.copy(measuring = true) }
            scanJob = scope.launch { scanLoop(force) }
        }
    }

    /** Measures the downloads not measured yet and returns the result. */
    suspend fun await(): State {
        refresh()
        scanJob?.join()
        return state.value
    }

    private suspend fun scanLoop(initialForce: Boolean) {
        var force = initialForce
        while (true) {
            try {
                scan(force)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to measure anime downloads" }
                state.update { it.copy(measuring = false, scanned = true) }
            }
            force = synchronized(this) {
                val queued = forceQueued
                forceQueued = false
                queued
            }
            if (!force) break
        }
    }

    private suspend fun scan(force: Boolean) {
        loadDiskCache()
        val dirs = cache.getDownloadedAnimeDirs()

        val (toMeasure, initialShows) = sizesMutex.withLock {
            dirs.filter { dir -> force || dir.episodeEntries.any { key(dir, it) !in sizes } } to
                dirs.map { showOf(it) }
        }
        state.update { it.copy(shows = initialShows, measuring = true, measured = 0, toMeasure = toMeasure.size) }

        // A few at a time: the storage access framework serves one query per call anyway
        val permits = Semaphore(MEASURE_PARALLELISM)
        coroutineScope {
            toMeasure.map { dir ->
                async {
                    permits.withPermit {
                        val measured = measure(dir)
                        val show = sizesMutex.withLock {
                            sizes.putAll(measured)
                            showOf(dir)
                        }
                        state.update { state ->
                            state.copy(
                                shows = state.shows.map { if (it.isOf(dir)) show else it },
                                measured = state.measured + 1,
                            )
                        }
                    }
                }
            }.awaitAll()
        }

        val shows = sizesMutex.withLock {
            // Forget downloads that are gone
            val present = dirs.flatMapTo(HashSet()) { dir -> dir.episodeEntries.map { key(dir, it) } }
            sizes.keys.retainAll(present)
            dirs.map { showOf(it) }
        }
        state.update { it.copy(shows = shows, measuring = false, measured = toMeasure.size, scanned = true) }
        saveDiskCache()
    }

    /** Measures the episodes of [dir], returning their sizes by [key]. */
    private fun measure(dir: DownloadedAnimeDir): Map<String, Long> {
        val animeDir = dir.dir ?: return emptyMap()
        return animeDir.listFiles().orEmpty()
            .mapNotNull { file ->
                val name = episodeEntryName(file)?.takeIf { it in dir.episodeEntries } ?: return@mapNotNull null
                key(dir, name) to file.measure()
            }
            .toMap()
    }

    private fun UniFile.measure(): Long = if (isDirectory) size() else length().coerceAtLeast(0)

    /** Builds a show from the sizes measured so far. Call with [sizesMutex] held. */
    private fun showOf(dir: DownloadedAnimeDir): Show {
        return Show(
            sourceIds = dir.sourceIds,
            dirName = dir.dirName,
            dir = dir.dir,
            entries = dir.episodeEntries
                .map { Entry(it, sizes[key(dir, it)] ?: UNKNOWN_SIZE) }
                .sortedBy { it.name },
        )
    }

    /** Marks entries of an anime directory as deleted, without measuring anything again. */
    fun onEntriesDeleted(sourceIds: Set<Long>, dirName: String, entryNames: Collection<String>?) {
        state.update { state ->
            state.copy(
                shows = state.shows.mapNotNull { show ->
                    if (show.dirName != dirName || show.sourceIds.intersect(sourceIds).isEmpty()) return@mapNotNull show
                    if (entryNames == null) return@mapNotNull null
                    show.copy(
                        entries = show.entries.filterNot {
                            it.name in entryNames
                        },
                    ).takeIf { it.entries.isNotEmpty() }
                },
            )
        }
    }

    private suspend fun loadDiskCache() {
        if (loadedFromDisk) return
        sizesMutex.withLock {
            if (loadedFromDisk) return
            loadedFromDisk = true
            try {
                if (diskCacheFile.exists()) {
                    val file = ProtoBuf.decodeFromByteArray<SizeCacheFile>(diskCacheFile.readBytes())
                    sizes.putAll(file.sizes)
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to read the anime download size cache" }
                diskCacheFile.delete()
            }
        }
    }

    private suspend fun saveDiskCache() {
        val bytes = sizesMutex.withLock { ProtoBuf.encodeToByteArray(SizeCacheFile(sizes.toMap())) }
        try {
            diskCacheFile.writeBytes(bytes)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to write the anime download size cache" }
        }
    }

    private fun key(dir: DownloadedAnimeDir, entryName: String): String {
        return (dir.dir?.uri?.toString() ?: "${dir.sourceIds.min()}/${dir.dirName}") + "\n" + entryName
    }

    /**
     * @property measuring whether downloads are being measured, [measured] of [toMeasure] shows done.
     * @property scanned whether [shows] has been listed at least once.
     */
    data class State(
        val shows: List<Show> = emptyList(),
        val measuring: Boolean = false,
        val measured: Int = 0,
        val toMeasure: Int = 0,
        val scanned: Boolean = false,
    ) {
        val totalSize: Long
            get() = shows.sumOf { it.size }
    }

    /** The downloaded episodes in an anime directory. */
    data class Show(
        val sourceIds: Set<Long>,
        val dirName: String,
        val dir: UniFile?,
        val entries: List<Entry>,
    ) {
        fun isOf(dir: DownloadedAnimeDir): Boolean = dirName == dir.dirName && sourceIds == dir.sourceIds

        /** Bytes of the episodes measured so far. */
        val size: Long
            get() = entries.sumOf { it.size.coerceAtLeast(0) }
    }

    /**
     * A downloaded episode, by its entry name (see [episodeEntryName]).
     *
     * @property size bytes, or [UNKNOWN_SIZE] while not measured yet.
     */
    data class Entry(
        val name: String,
        val size: Long,
    )

    companion object {
        const val UNKNOWN_SIZE = -1L
        private const val MEASURE_PARALLELISM = 4
    }
}

@Serializable
private class SizeCacheFile(
    val sizes: Map<String, Long> = emptyMap(),
)
