package eu.kanade.tachiyomi.data.backup

import android.net.Uri
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager

@Inject
class BackupFileValidator(
    private val animeSourceManager: AnimeSourceManager,
    private val mangaSourceManager: MangaSourceManager,
    private val trackerManager: TrackerManager,
    private val backupDecoder: BackupDecoder,
) {

    /**
     * Checks for critical backup file data.
     *
     * @return List of missing sources or missing trackers.
     */
    suspend fun validate(uri: Uri): Results {
        val trackerIds = mutableSetOf<Int>()
        val backup = try {
            backupDecoder.decodeMetadata(uri, TrackedEntry.serializer()) { entry ->
                entry.tracking.mapTo(trackerIds) { it.syncId }
            }.backup
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

        val sources = backup.backupSources.associate { it.sourceId to it.name }
        val animesources = backup.backupAnimeSources.associate { it.sourceId to it.name }
        val missingSources = sources
            .filterKeys { mangaSourceManager.get(it) == null }
            .map { (id, name) ->
                if (name.isBlank() || name.toLongOrNull() != null) {
                    mangaSourceManager.getOrStub(id).toString()
                } else {
                    name
                }
            }
            .distinct()
            .sorted() +
            animesources
                .filterKeys { animeSourceManager.get(it) == null }
                .map { (id, name) ->
                    if (name.isBlank() || name.toLongOrNull() != null) {
                        animeSourceManager.getOrStub(id).toString()
                    } else {
                        name
                    }
                }
                .distinct()
                .sorted()

        val missingTrackers = trackerIds
            .mapNotNull { trackerManager.get(it.toLong()) }
            .filter { !it.isLoggedIn }
            .map { it.name }
            .sorted()

        return Results(missingSources, missingTrackers)
    }

    data class Results(
        val missingSources: List<String>,
        val missingTrackers: List<String>,
    )

    @Serializable
    private class TrackedEntry(
        @ProtoNumber(18) val tracking: List<TrackedService> = emptyList(),
    )

    @Serializable
    private class TrackedService(
        @ProtoNumber(1) val syncId: Int,
    )
}
