package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import androidx.core.content.edit
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDownloadStore(
    context: Context,
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val json: Json = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
) {

    private val preferences = context.getSharedPreferences("active_downloads", Context.MODE_PRIVATE)

    private var counter = 0

    fun addAll(downloads: List<MangaDownload>) {
        preferences.edit {
            downloads.forEach { putString(getKey(it), serialize(it)) }
        }
    }

    fun remove(download: MangaDownload) {
        preferences.edit {
            remove(getKey(download))
        }
    }

    fun removeAll(downloads: List<MangaDownload>) {
        preferences.edit {
            downloads.forEach { remove(getKey(it)) }
        }
    }

    fun clear() {
        preferences.edit {
            clear()
        }
    }

    private fun getKey(download: MangaDownload): String {
        return download.chapter.id.toString()
    }

    suspend fun restore(): List<MangaDownload> {
        val objs = preferences.all
            .mapNotNull { it.value as? String }
            .mapNotNull { deserialize(it) }
            .sortedBy { it.order }

        val downloads = mutableListOf<MangaDownload>()
        if (objs.isNotEmpty()) {
            val cachedManga = mutableMapOf<Long, Manga?>()
            for ((mangaId, chapterId) in objs) {
                val manga = cachedManga.getOrPut(mangaId) {
                    getManga.await(mangaId)
                } ?: continue
                val source = sourceManager.get(manga.source) as? HttpSource ?: continue
                val chapter = getChapter.await(chapterId) ?: continue
                downloads.add(MangaDownload(source, manga, chapter))
            }
        }

        clear()
        return downloads
    }

    private fun serialize(download: MangaDownload): String {
        val obj = DownloadObject(download.manga.id, download.chapter.id, counter++)
        return json.encodeToString(obj)
    }

    private fun deserialize(string: String): DownloadObject? {
        return try {
            json.decodeFromString<DownloadObject>(string)
        } catch (e: Exception) {
            null
        }
    }
}

@Serializable
private data class DownloadObject(val mangaId: Long, val chapterId: Long, val order: Int)
