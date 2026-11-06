package mihon.feature.library

import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import mihon.domain.library.model.search.QueryNode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.entries.anime.LocalAnimeSource

class QueryNodeExtensionsTest {

    private fun createAnimeItem(
        id: Long = 1L,
        source: Long = 12345L,
        title: String = "Frieren: Beyond Journey's End",
        sourceName: String = "gogoanime",
        sourceLanguage: String = "en",
        totalCount: Long = 28L,
        seenCount: Long = 10L,
    ): AnimeLibraryItem {
        val anime = Anime.create().copy(
            id = id,
            source = source,
            ogTitle = title,
            favorite = false,
        )
        val libraryAnime = LibraryAnime(
            anime = anime,
            category = 0L,
            totalCount = totalCount,
            seenCount = seenCount,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            episodeFetchedAt = 0L,
            lastSeen = 0L,
        )
        return AnimeLibraryItem(
            libraryAnime = libraryAnime,
            downloadCount = 0,
            unseenCount = totalCount - seenCount,
            isLocal = source == LocalAnimeSource.ID,
            sourceName = sourceName.lowercase(),
            sourceLanguage = sourceLanguage,
            badges = AnimeLibraryItem.Badges(0, totalCount - seenCount, source == LocalAnimeSource.ID, sourceLanguage),
        )
    }

    private fun createMangaItem(
        id: Long = 1L,
        source: Long = 54321L,
        title: String = "Catastrophic Priest",
        sourceName: String = "taiyō",
        sourceLanguage: String = "pt-BR",
        totalChapters: Long = 50L,
        readCount: Long = 20L,
    ): MangaLibraryItem {
        val manga = Manga.create().copy(
            id = id,
            source = source,
            ogTitle = title,
            favoriteAt = null,
        )
        val libraryManga = LibraryManga(
            manga = manga,
            category = 0L,
            totalChapters = totalChapters,
            readCount = readCount,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
        return MangaLibraryItem(
            libraryManga = libraryManga,
            downloadCount = 0,
            unreadCount = totalChapters - readCount,
            isLocal = source == LocalSource.ID,
            sourceName = sourceName.lowercase(),
            sourceLanguage = sourceLanguage,
            badges = MangaLibraryItem.Badges(0, totalChapters - readCount, source == LocalSource.ID, sourceLanguage),
        )
    }

    @Test
    fun `anime source query matching from AnimeInfoHeader`() {
        val item = createAnimeItem(sourceName = "Gogoanime", sourceLanguage = "en")

        // Matches what AnimeInfoHeader.query() generates: src:"$name" lang:$lang
        val query = QueryNode.from("""src:"Gogoanime" lang:en""")
        assertTrue(query.matches(item))

        val mismatchedQuery = QueryNode.from("""src:"Gogoanime" lang:es""")
        assertFalse(mismatchedQuery.matches(item))

        val mismatchedSource = QueryNode.from("""src:"AnimePahe" lang:en""")
        assertFalse(mismatchedSource.matches(item))
    }

    @Test
    fun `anime source id and local queries`() {
        val item = createAnimeItem(source = 12345L, sourceName = "custom")
        assertTrue(QueryNode.from("srcid:12345").matches(item))
        assertFalse(QueryNode.from("srcid:99999").matches(item))

        val localItem = createAnimeItem(source = LocalAnimeSource.ID, sourceName = "local")
        assertTrue(QueryNode.from("src:local").matches(localItem))
    }

    @Test
    fun `anime general search with source name and language`() {
        val item = createAnimeItem(sourceName = "AnimePahe", sourceLanguage = "en")

        // User searches for full display name
        assertTrue(QueryNode.from("AnimePahe (en)").matches(item))
        assertTrue(QueryNode.from("AnimePahe").matches(item))
    }

    @Test
    fun `anime comparison queries`() {
        val item = createAnimeItem(totalCount = 28L, seenCount = 10L)

        // unseenCount is 18
        assertTrue(QueryNode.from("unseen>10").matches(item))
        assertTrue(QueryNode.from("unseen=18").matches(item))
        assertTrue(QueryNode.from("unseen:18").matches(item))
        assertFalse(QueryNode.from("unseen>20").matches(item))

        // seenCount is 10
        assertTrue(QueryNode.from("seen=10").matches(item))
        assertTrue(QueryNode.from("read=10").matches(item))

        // totalCount is 28
        assertTrue(QueryNode.from("episodes=28").matches(item))
        assertTrue(QueryNode.from("total=28").matches(item))
    }

    @Test
    fun `manga source query matching from MangaInfoHeader`() {
        val item = createMangaItem(sourceName = "Taiyō", sourceLanguage = "pt-BR")

        // Matches what MangaInfoHeader.query() generates: src:"$name" lang:$lang
        val query = QueryNode.from("""src:"Taiyō" lang:pt-BR""")
        assertTrue(query.matches(item))

        // General search for Taiyō (pt-BR)
        assertTrue(QueryNode.from("Taiyō (pt-BR)").matches(item))
        assertTrue(QueryNode.from("Taiyō").matches(item))
    }
}
