package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadStore
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadStore
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager

/**
 * The anime and manga download queues are persisted so they survive the app being killed. Restoring
 * one queue must never drop the other.
 */
class DownloadStoreTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val files = mutableMapOf<String, FakeSharedPreferences>()
    private val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } answers {
            files.getOrPut(firstArg()) { FakeSharedPreferences() }
        }
    }

    private val anime = Anime.create().copy(id = 1, source = 10)
    private val manga = Manga.create().copy(id = 2, source = 20)

    private val animeSource = mockk<AnimeHttpSource>()
    private val animeSourcesLoaded = MutableStateFlow(true)
    private val animeSourceManager = mockk<AnimeSourceManager> {
        every { isInitialized } returns animeSourcesLoaded
        every { get(anime.source) } answers { if (animeSourcesLoaded.value) animeSource else null }
    }
    private val getAnime = mockk<GetAnime> {
        coEvery { await(any<Long>()) } answers
            { anime.takeIf { it.id == firstArg() } }
    }
    private val getEpisode = mockk<GetEpisode> {
        coEvery { await(any<Long>()) } answers { Episode.create().copy(id = firstArg(), animeId = anime.id) }
    }

    private val mangaSource = mockk<HttpSource>()
    private val mangaSourceManager = mockk<MangaSourceManager> { coEvery { get(manga.source) } returns mangaSource }
    private val getManga = mockk<GetManga> {
        coEvery { await(any<Long>()) } answers
            { manga.takeIf { it.id == firstArg() } }
    }
    private val getChapter = mockk<GetChapter> {
        coEvery { await(any<Long>()) } answers { Chapter.create().copy(id = firstArg(), mangaId = manga.id) }
    }

    private fun animeStore() = AnimeDownloadStore(context, animeSourceManager, json, getAnime, getEpisode)
    private fun mangaStore() = MangaDownloadStore(context, mangaSourceManager, json, getManga, getChapter)

    private fun animeDownloads(vararg episodeIds: Long) =
        episodeIds.map { AnimeDownload(animeSource, anime, Episode.create().copy(id = it, animeId = anime.id)) }

    private fun mangaDownloads(vararg chapterIds: Long) =
        chapterIds.map { MangaDownload(mangaSource, manga, Chapter.create().copy(id = it, mangaId = manga.id)) }

    private fun List<AnimeDownload>.episodeIds() = map { it.episode.id }
    private fun List<MangaDownload>.chapterIds() = map { it.chapter.id }

    @Test
    fun `restoring one queue keeps the other queue`() = runTest {
        animeStore().addAll(animeDownloads(1, 2))
        mangaStore().addAll(mangaDownloads(3, 4))

        // A new process: each downloader restores its own queue, in either order.
        assertEquals(listOf(1L, 2L), animeStore().restore().episodeIds())
        assertEquals(listOf(3L, 4L), mangaStore().restore().chapterIds())
    }

    @Test
    fun `an episode and a chapter with the same id are both kept`() = runTest {
        val animeStore = animeStore()
        val mangaStore = mangaStore()
        animeStore.addAll(animeDownloads(7))
        mangaStore.addAll(mangaDownloads(7))

        mangaStore.remove(mangaDownloads(7).single())

        assertEquals(listOf(7L), animeStore().restore().episodeIds())
        assertEquals(emptyList<Long>(), mangaStore().restore().chapterIds())
    }

    @Test
    fun `clearing one queue keeps the other queue`() = runTest {
        animeStore().addAll(animeDownloads(1))
        mangaStore().addAll(mangaDownloads(2))

        animeStore().clear()

        assertEquals(emptyList<Long>(), animeStore().restore().episodeIds())
        assertEquals(listOf(2L), mangaStore().restore().chapterIds())
    }

    @Test
    fun `entries in the old shared file move to their own store`() = runTest {
        files["active_downloads"] = FakeSharedPreferences(
            mutableMapOf(
                "11" to """{"animeId":1,"episodeId":11,"order":1}""",
                "12" to """{"animeId":1,"episodeId":12,"order":0}""",
                "21" to """{"mangaId":2,"chapterId":21,"order":0}""",
                "99" to "not a download",
            ),
        )

        val animeStore = animeStore()
        assertEquals(setOf("21", "99"), files.getValue("active_downloads").all.keys)
        val mangaStore = mangaStore()

        assertEquals(listOf(12L, 11L), animeStore.restore().episodeIds())
        assertEquals(listOf(21L), mangaStore.restore().chapterIds())
        assertEquals(setOf("99"), files.getValue("active_downloads").all.keys)
    }

    @Test
    fun `anime queue restore waits for the sources to load`() = runTest {
        animeStore().addAll(animeDownloads(1, 2))
        animeSourcesLoaded.value = false

        val restored = async { animeStore().restore() }
        runCurrent()
        assertFalse(restored.isCompleted)

        animeSourcesLoaded.value = true
        assertEquals(listOf(1L, 2L), restored.await().episodeIds())
    }

    @Test
    fun `downloads whose entry is gone are dropped`() = runTest {
        val store = animeStore()
        store.addAll(animeDownloads(1))
        store.addAll(listOf(AnimeDownload(animeSource, anime.copy(id = 404), Episode.create().copy(id = 2))))

        assertEquals(listOf(1L), animeStore().restore().episodeIds())
        assertTrue(files.getValue("active_downloads_anime").all.isEmpty())
    }
}

private class FakeSharedPreferences(
    private val values: MutableMap<String, Any?> = mutableMapOf(),
) : SharedPreferences {
    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = defValue
    override fun getLong(key: String, defValue: Long) = defValue
    override fun getFloat(key: String, defValue: Float) = defValue
    override fun getBoolean(key: String, defValue: Boolean) = defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {}
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, String?>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
