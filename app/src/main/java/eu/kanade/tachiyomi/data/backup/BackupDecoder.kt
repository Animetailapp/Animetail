package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSource
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException

@Inject
class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf,
) {
    /**
     * Decode everything but the entries of a potentially-gzipped backup, counting the entries without decoding them.
     */
    fun decodeMetadata(uri: Uri): Metadata {
        return read(uri) { reader ->
            orInvalid {
                var mangaCount = 0
                var animeCount = 0
                while (reader.nextEntry()) {
                    when (reader.currentEntryType) {
                        BackupReader.EntryType.MANGA -> {
                            reader.skipEntry()
                            mangaCount++
                        }

                        BackupReader.EntryType.ANIME -> {
                            reader.skipEntry()
                            animeCount++
                        }

                        BackupReader.EntryType.NONE -> {}
                    }
                }
                Metadata(mangaCount, animeCount, reader.readMetadata())
            }
        }
    }

    fun <T> decodeMetadata(uri: Uri, entry: DeserializationStrategy<T>, onEntry: (T) -> Unit): Metadata {
        return read(uri) { reader ->
            var mangaCount = 0
            var animeCount = 0
            while (orInvalid { reader.nextEntry() }) {
                when (reader.currentEntryType) {
                    BackupReader.EntryType.MANGA -> mangaCount++
                    BackupReader.EntryType.ANIME -> animeCount++
                    BackupReader.EntryType.NONE -> error("Missing entry type")
                }
                onEntry(orInvalid { reader.readEntry(entry) })
            }
            Metadata(mangaCount, animeCount, orInvalid { reader.readMetadata() })
        }
    }

    fun decodeMangaEntries(uri: Uri): Flow<BackupManga> = flow {
        read(uri) { reader ->
            while (orInvalid { reader.nextEntry() }) {
                if (reader.currentEntryType == BackupReader.EntryType.MANGA) {
                    emit(orInvalid { reader.readMangaEntry() })
                } else {
                    orInvalid { reader.skipEntry() }
                }
            }
        }
    }

    fun decodeAnimeEntries(uri: Uri): Flow<BackupAnime> = flow {
        read(uri) { reader ->
            while (orInvalid { reader.nextEntry() }) {
                if (reader.currentEntryType == BackupReader.EntryType.ANIME) {
                    emit(orInvalid { reader.readAnimeEntry() })
                } else {
                    orInvalid { reader.skipEntry() }
                }
            }
        }
    }

    private inline fun <T> read(uri: Uri, block: (BackupReader) -> T): T {
        return context.contentResolver.openInputStream(uri)!!.use { inputStream ->
            val source = inputStream.source().buffer()

            val peeked = source.peek().apply {
                require(2)
            }
            val id1id2 = peeked.readShort()
            val backupSource: BufferedSource = when (id1id2.toInt()) {
                0x1f8b -> source.gzip().buffer()

                // 0x1f8b is gzip magic bytes
                MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                }

                else -> source
            }

            backupSource.use { block(BackupReader(it, parser)) }
        }
    }

    // Only around reading, so the JSON error above and anything thrown downstream of emit keep their own messages
    private inline fun <T> orInvalid(block: () -> T): T {
        return try {
            block()
        } catch (e: IOException) {
            throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown), e)
        }
    }

    data class Metadata(
        val mangaCount: Int,
        val animeCount: Int,
        val backup: Backup,
    ) {
        val entryCount: Int get() = mangaCount + animeCount
    }

    companion object {
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
    }
}
